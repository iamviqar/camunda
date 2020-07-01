/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.alert;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.DecisionGroupByType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessVisualization;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemType;
import org.camunda.optimize.service.es.reader.AlertReader;
import org.camunda.optimize.service.es.writer.AlertWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.relations.ReportReferencingService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.ValidationHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.BadRequestException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.camunda.bpm.engine.EntityTypes.REPORT;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.CHECK_INTERVAL;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.EMAIL;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.INTERVAL_UNIT;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.THRESHOLD_OPERATOR;
import static org.camunda.optimize.service.es.schema.index.AlertIndex.WEBHOOK;

@RequiredArgsConstructor
@Component
public class AlertService implements ReportReferencingService {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ApplicationContext applicationContext;
  private final AlertReader alertReader;
  private final AlertWriter alertWriter;
  private final ConfigurationService configurationService;
  private final AlertReminderJobFactory alertReminderJobFactory;
  private final AlertCheckJobFactory alertCheckJobFactory;
  private final ReportService reportService;
  private final AuthorizedCollectionService authorizedCollectionService;

  private SchedulerFactoryBean schedulerFactoryBean;

  @PostConstruct
  public void init() {
    List<AlertDefinitionDto> alerts = alertReader.getStoredAlerts();
    checkAlertWebhooksAllExist(alerts);
    try {
      if (schedulerFactoryBean == null) {
        QuartzJobFactory sampleJobFactory = new QuartzJobFactory();
        sampleJobFactory.setApplicationContext(applicationContext);

        schedulerFactoryBean = new SchedulerFactoryBean();
        schedulerFactoryBean.setOverwriteExistingJobs(true);
        schedulerFactoryBean.setJobFactory(sampleJobFactory);

        Map<AlertDefinitionDto, JobDetail> checkingDetails = createCheckDetails(alerts);
        List<Trigger> checkingTriggers = createCheckTriggers(checkingDetails);

        Map<AlertDefinitionDto, JobDetail> reminderDetails = createReminderDetails(alerts);
        List<Trigger> reminderTriggers = createReminderTriggers(reminderDetails);

        List<Trigger> allTriggers = new ArrayList<>();
        allTriggers.addAll(checkingTriggers);
        allTriggers.addAll(reminderTriggers);

        List<JobDetail> allJobDetails = new ArrayList<>();
        allJobDetails.addAll(checkingDetails.values());
        allJobDetails.addAll(reminderDetails.values());

        JobDetail[] jobDetails = allJobDetails.toArray(new JobDetail[0]);
        Trigger[] triggers = allTriggers.toArray(new Trigger[0]);

        schedulerFactoryBean.setGlobalJobListeners(createReminderListener());
        schedulerFactoryBean.setTriggers(triggers);
        schedulerFactoryBean.setJobDetails(jobDetails);
        schedulerFactoryBean.setApplicationContext(applicationContext);
        schedulerFactoryBean.setQuartzProperties(configurationService.getQuartzProperties());
        schedulerFactoryBean.afterPropertiesSet();
        // we need to set this to make sure that there are no further threads running
        // after the quartz scheduler has been stopped.
        schedulerFactoryBean.setWaitForJobsToCompleteOnShutdown(true);
        schedulerFactoryBean.start();
      }
    } catch (Exception e) {
      logger.error("Couldn't initialize alert scheduling.", e);
      try {
        destroy();
      } catch (Exception destroyException) {
        logger.error("Failed destroying alertService", destroyException);
      }
      throw new RuntimeException(e);
    }
  }

  @PreDestroy
  public void destroy() {
    if (schedulerFactoryBean != null) {
      try {
        schedulerFactoryBean.stop();
        schedulerFactoryBean.destroy();
      } catch (Exception e) {
        logger.error("Can't destroy scheduler", e);
      }
      this.schedulerFactoryBean = null;
    }
  }

  private List<Trigger> createReminderTriggers(Map<AlertDefinitionDto, JobDetail> reminderDetails) {
    List<Trigger> triggers = new ArrayList<>();

    for (Map.Entry<AlertDefinitionDto, JobDetail> e : reminderDetails.entrySet()) {
      if (e.getKey().getReminder() != null) {
        triggers.add(alertReminderJobFactory.createTrigger(e.getKey(), e.getValue()));
      }
    }

    return triggers;
  }

  private List<Trigger> createCheckTriggers(Map<AlertDefinitionDto, JobDetail> details) {
    List<Trigger> triggers = new ArrayList<>();

    for (Map.Entry<AlertDefinitionDto, JobDetail> e : details.entrySet()) {
      triggers.add(alertCheckJobFactory.createTrigger(e.getKey(), e.getValue()));
    }

    return triggers;
  }

  private Map<AlertDefinitionDto, JobDetail> createReminderDetails(List<AlertDefinitionDto> alerts) {
    Map<AlertDefinitionDto, JobDetail> result = new HashMap<>();

    for (AlertDefinitionDto alert : alerts) {
      result.put(alert, alertReminderJobFactory.createJobDetails(alert));
    }

    return result;
  }

  private Map<AlertDefinitionDto, JobDetail> createCheckDetails(List<AlertDefinitionDto> alerts) {
    Map<AlertDefinitionDto, JobDetail> result = new HashMap<>();

    for (AlertDefinitionDto alert : alerts) {
      result.put(alert, alertCheckJobFactory.createJobDetails(alert));
    }

    return result;
  }

  private JobListener createReminderListener() {
    return new ReminderHandlingListener(alertReminderJobFactory);
  }

  private void checkAlertWebhooksAllExist(List<AlertDefinitionDto> alerts) {
    final Set<String> webhooks = configurationService.getConfiguredWebhooks().keySet();
    final Map<String, List<String>> missingWebhookMap = alerts.stream()
      .filter(alert -> StringUtils.isNotEmpty(alert.getWebhook()) && !webhooks.contains(alert.getWebhook()))
      .collect(groupingBy(AlertCreationDto::getWebhook, mapping(AlertDefinitionDto::getId, toList())));
    if (!missingWebhookMap.isEmpty()) {
      final String missingWebhookSummary = missingWebhookMap.entrySet()
        .stream()
        .map(entry -> String.format("Webhook: [%s] - Associated with alert(s): %s", entry.getKey(), entry.getValue()))
        .collect(Collectors.joining("\n"));
      final String errorMsg = String.format(
        "The following webhooks no longer exist in the service-config, yet are associated with existing alerts:%n%s",
        missingWebhookSummary
      );
      logger.error(errorMsg);
    }
  }

  public Scheduler getScheduler() {
    return schedulerFactoryBean.getObject();
  }

  public List<AlertDefinitionDto> getStoredAlertsForCollection(String userId, String collectionId) {
    List<String> authorizedReportIds = reportService
      .findAndFilterReports(userId, collectionId)
      .stream()
      .map(authorizedReportDefinitionDto -> authorizedReportDefinitionDto.getDefinitionDto().getId())
      .collect(toList());

    return alertReader.findFirstAlertsForReports(authorizedReportIds);
  }

  private List<AlertDefinitionDto> findFirstAlertsForReport(String reportId) {
    return alertReader.findFirstAlertsForReport(reportId);
  }

  private AlertDefinitionDto getAlert(String alertId) {
    return alertReader.getAlert(alertId);
  }

  public IdDto createAlert(AlertCreationDto toCreate, String userId) {
    validateAlert(toCreate, userId);
    verifyUserAuthorizedToEditAlertOrFail(toCreate, userId);
    String alertId = this.createAlertForUser(toCreate, userId).getId();
    return new IdDto(alertId);
  }

  private void validateAlert(AlertCreationDto toCreate, String userId) {
    ReportDefinitionDto report;
    try {
      report = reportService.getReportDefinition(toCreate.getReportId(), userId).getDefinitionDto();
    } catch (Exception e) {
      String errorMessage = "Could not create alert [" + toCreate.getName() + "]. Report id [" +
        toCreate.getReportId() + "] does not exist.";
      logger.error(errorMessage);
      throw new OptimizeRuntimeException(errorMessage, e);
    }

    if (report.getCollectionId() == null || report.getCollectionId().isEmpty()) {
      throw new BadRequestException(
        "Alerts cannot be created for private reports, only for reports within a collection.");
    }

    ValidationHelper.ensureNotEmpty(REPORT, report);

    ValidationHelper.ensureNotEmpty(THRESHOLD_OPERATOR, toCreate.getThresholdOperator());
    ValidationHelper.ensureNotNull(CHECK_INTERVAL, toCreate.getCheckInterval());
    ValidationHelper.ensureNotEmpty(INTERVAL_UNIT, toCreate.getCheckInterval().getUnit());

    ValidationHelper.ensureNotBothEmpty(EMAIL, WEBHOOK, toCreate.getEmail(), toCreate.getWebhook());
  }

  private AlertDefinitionDto createAlertForUser(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto alert = alertWriter.createAlert(newAlert(toCreate, userId));
    scheduleAlert(alert);
    return alert;
  }

  public void copyAndMoveAlerts(String oldReportId, String newReportId) {
    List<AlertDefinitionDto> oldAlerts = findFirstAlertsForReport(oldReportId);
    for (AlertDefinitionDto alert : oldAlerts) {
      alert.setReportId(newReportId);
      createAlert(alert, alert.getOwner());
    }
  }

  private void scheduleAlert(AlertDefinitionDto alert) {
    try {
      JobDetail jobDetail = alertCheckJobFactory.createJobDetails(alert);
      if (schedulerFactoryBean != null) {
        getScheduler().scheduleJob(jobDetail, alertCheckJobFactory.createTrigger(alert, jobDetail));
      }
    } catch (SchedulerException e) {
      logger.error("can't schedule new alert", e);
    }
  }

  private static AlertDefinitionDto newAlert(AlertCreationDto toCreate, String userId) {
    AlertDefinitionDto result = new AlertDefinitionDto();
    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    result.setOwner(userId);
    result.setCreated(now);
    result.setLastModified(now);
    result.setLastModifier(userId);

    AlertUtil.mapBasicFields(toCreate, result);
    return result;
  }

  public void updateAlert(String alertId, AlertCreationDto toCreate, String userId) {
    validateAlert(toCreate, userId);
    this.updateAlertForUser(alertId, toCreate, userId);
  }

  private void updateAlertForUser(String alertId, AlertCreationDto toCreate, String userId) {
    verifyUserAuthorizedToEditAlertOrFail(toCreate, userId);

    AlertDefinitionDto toUpdate = alertReader.getAlert(alertId);
    unscheduleJob(toUpdate);
    toUpdate.setLastModified(LocalDateUtil.getCurrentDateTime());
    toUpdate.setLastModifier(userId);
    AlertUtil.mapBasicFields(toCreate, toUpdate);
    alertWriter.updateAlert(toUpdate);
    scheduleAlert(toUpdate);
  }

  public void deleteAlert(String alertId, String userId) {
    verifyUserAuthorizedToEditAlertOrFail(getAlert(alertId), userId);

    AlertDefinitionDto toDelete = alertReader.getAlert(alertId);
    alertWriter.deleteAlert(alertId);
    unscheduleJob(toDelete);
  }

  private void unscheduleJob(AlertDefinitionDto toDelete) {
    String alertId = toDelete.getId();
    try {
      unscheduleCheckJob(toDelete);
      unscheduleReminderJob(toDelete);

    } catch (SchedulerException e) {
      logger.error("can't adjust scheduler for alert [{}]", alertId, e);
    }
    toDelete.setTriggered(false);
  }

  private void unscheduleReminderJob(AlertDefinitionDto toDelete) throws SchedulerException {
    JobKey toUnschedule = alertReminderJobFactory.getJobKey(toDelete);
    TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }

  private void unscheduleCheckJob(AlertDefinitionDto toDelete) throws SchedulerException {
    JobKey toUnschedule = alertCheckJobFactory.getJobKey(toDelete);
    TriggerKey triggerKey = alertReminderJobFactory.getTriggerKey(toDelete);

    if (toUnschedule != null) {
      getScheduler().unscheduleJob(triggerKey);
      getScheduler().deleteJob(toUnschedule);
    }
  }

  private void deleteAlertsForReport(String reportId) {
    List<AlertDefinitionDto> alerts = alertReader.findFirstAlertsForReport(reportId);

    for (AlertDefinitionDto alert : alerts) {
      unscheduleJob(alert);
    }

    alertWriter.deleteAlertsForReport(reportId);
  }

  /**
   * Check if it's still evaluated as number.
   */
  private void deleteAlertsIfNeeded(String reportId, ReportDefinitionDto reportDefinition) {
    if (reportDefinition instanceof SingleProcessReportDefinitionDto) {
      SingleProcessReportDefinitionDto singleReport = (SingleProcessReportDefinitionDto) reportDefinition;
      if (!validateIfReportIsSuitableForAlert(singleReport)) {
        this.deleteAlertsForReport(reportId);
      }
    }
  }

  private boolean validateIfReportIsSuitableForAlert(SingleProcessReportDefinitionDto report) {
    final ProcessReportDataDto data = report.getData();
    return data != null && data.getGroupBy() != null
      && ProcessGroupByType.NONE.equals(data.getGroupBy().getType())
      && ProcessVisualization.NUMBER.equals(data.getVisualization());
  }

  private boolean validateIfReportIsSuitableForAlert(SingleDecisionReportDefinitionDto report) {
    final DecisionReportDataDto data = report.getData();
    return data != null && data.getGroupBy() != null
      && DecisionGroupByType.NONE.equals(data.getGroupBy().getType())
      && DecisionVisualization.NUMBER.equals(data.getVisualization());
  }

  public JobDetail createStatusCheckJobDetails(AlertDefinitionDto fakeReportAlert) {
    return this.alertCheckJobFactory.createJobDetails(fakeReportAlert);
  }

  public Trigger createStatusCheckTrigger(AlertDefinitionDto fakeReportAlert, JobDetail jobDetail) {
    return this.alertCheckJobFactory.createTrigger(fakeReportAlert, jobDetail);
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportDelete(final ReportDefinitionDto reportDefinition) {
    return mapAlertsToConflictingItems(findFirstAlertsForReport(reportDefinition.getId()));
  }

  @Override
  public void handleReportDeleted(final ReportDefinitionDto reportDefinition) {
    deleteAlertsForReport(reportDefinition.getId());
  }

  @Override
  public Set<ConflictedItemDto> getConflictedItemsForReportUpdate(ReportDefinitionDto currentDefinition,
                                                                  ReportDefinitionDto updateDefinition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();

    if (currentDefinition instanceof SingleProcessReportDefinitionDto) {
      if (validateIfReportIsSuitableForAlert((SingleProcessReportDefinitionDto) currentDefinition)
        && !validateIfReportIsSuitableForAlert((SingleProcessReportDefinitionDto) updateDefinition)) {
        conflictedItems.addAll(mapAlertsToConflictingItems(findFirstAlertsForReport(currentDefinition.getId())));
      }
    } else if (currentDefinition instanceof SingleDecisionReportDefinitionDto) {
      if (validateIfReportIsSuitableForAlert((SingleDecisionReportDefinitionDto) currentDefinition)
        && !validateIfReportIsSuitableForAlert((SingleDecisionReportDefinitionDto) updateDefinition)) {
        conflictedItems.addAll(mapAlertsToConflictingItems(findFirstAlertsForReport(currentDefinition.getId())));
      }
    }

    return conflictedItems;
  }

  @Override
  public void handleReportUpdated(final String id, final ReportDefinitionDto updateDefinition) {
    deleteAlertsIfNeeded(id, updateDefinition);
  }

  private Set<ConflictedItemDto> mapAlertsToConflictingItems(List<AlertDefinitionDto> alertsForReport) {
    return alertsForReport.stream()
      .map(alertDto -> new ConflictedItemDto(alertDto.getId(), ConflictedItemType.ALERT, alertDto.getName()))
      .collect(toSet());
  }

  private void verifyUserAuthorizedToEditAlertOrFail(
    final AlertCreationDto alertDto, final String userId) {
    AuthorizedReportDefinitionDto reportDefinitionDto = reportService.getReportDefinition(
      alertDto.getReportId(),
      userId
    );
    authorizedCollectionService.verifyUserAuthorizedToEditCollectionResources(
      userId,
      reportDefinitionDto.getDefinitionDto()
        .getCollectionId()
    );
  }
}
