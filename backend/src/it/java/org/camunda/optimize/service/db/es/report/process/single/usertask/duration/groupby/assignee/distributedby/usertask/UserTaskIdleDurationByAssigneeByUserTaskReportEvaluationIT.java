/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.process.single.usertask.duration.groupby.assignee.distributedby.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.ProcessReportDataType.USER_TASK_DUR_GROUP_BY_ASSIGNEE_BY_USER_TASK;

import java.util.List;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.db.es.report.util.MapResultUtil;
import org.camunda.optimize.service.util.TemplatedProcessReportDataBuilder;

public class UserTaskIdleDurationByAssigneeByUserTaskReportEvaluationIT
    extends AbstractUserTaskDurationByAssigneeByUserTaskReportEvaluationIT {

  @Override
  protected UserTaskDurationTime getUserTaskDurationTime() {
    return UserTaskDurationTime.IDLE;
  }

  @Override
  protected void changeDuration(
      final ProcessInstanceEngineDto processInstanceDto, final double durationInMs) {
    changeUserTaskIdleDuration(processInstanceDto, durationInMs);
  }

  @Override
  protected void changeDuration(
      final ProcessInstanceEngineDto processInstanceDto,
      final String userTaskKey,
      final double durationInMs) {
    changeUserTaskIdleDuration(processInstanceDto, userTaskKey, durationInMs);
  }

  @Override
  protected ProcessReportDataDto createReport(
      final String processDefinitionKey, final List<String> versions) {
    return TemplatedProcessReportDataBuilder.createReportData()
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessDefinitionVersions(versions)
        .setUserTaskDurationTime(UserTaskDurationTime.IDLE)
        .setReportDataType(USER_TASK_DUR_GROUP_BY_ASSIGNEE_BY_USER_TASK)
        .build();
  }

  @Override
  protected void assertEvaluateReportWithFlowNodeStatusFilter(
      final ReportResultResponseDto<List<HyperMapResultEntryDto>> result,
      final FlowNodeStatusTestValues expectedValues) {
    assertThat(MapResultUtil.getDataEntryForKey(result.getFirstMeasureData(), DEFAULT_USERNAME))
        .isPresent()
        .get()
        .isEqualTo(expectedValues.getExpectedIdleDurationValues());
  }
}
