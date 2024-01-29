/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer.activity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.RequestType;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.db.es.writer.AbstractProcessInstanceDataWriterES;
import org.camunda.optimize.service.db.schema.ScriptData;
import org.camunda.optimize.service.db.writer.DatabaseWriterUtil;
import org.camunda.optimize.service.db.writer.activity.AbstractActivityInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;

@Component
@Conditional(ElasticSearchCondition.class)
public abstract class AbstractActivityInstanceWriterES extends AbstractProcessInstanceDataWriterES<FlowNodeEventDto>
  implements AbstractActivityInstanceWriter {

  private final ObjectMapper objectMapper;

  protected AbstractActivityInstanceWriterES(final OptimizeElasticsearchClient esClient,
                                             final ElasticSearchSchemaManager elasticSearchSchemaManager,
                                             final ObjectMapper objectMapper) {
    super(esClient, elasticSearchSchemaManager);
    this.objectMapper = objectMapper;
  }

  @Override
  public List<ImportRequestDto> generateActivityInstanceImports(List<FlowNodeEventDto> activityInstances) {
    final String importItemName = "activity instances";
    log.debug("Creating imports for {} [{}].", activityInstances.size(), importItemName);

    createInstanceIndicesIfMissing(activityInstances, FlowNodeEventDto::getProcessDefinitionKey);

    Map<String, List<FlowNodeEventDto>> processInstanceToEvents = new HashMap<>();
    for (FlowNodeEventDto e : activityInstances) {
      if (!processInstanceToEvents.containsKey(e.getProcessInstanceId())) {
        processInstanceToEvents.put(e.getProcessInstanceId(), new ArrayList<>());
      }
      processInstanceToEvents.get(e.getProcessInstanceId()).add(e);
    }

    return processInstanceToEvents.entrySet().stream()
      .map(entry -> createImportRequestForActivityInstance(entry, importItemName))
      .collect(Collectors.toList());
  }

  @Override
  public FlowNodeInstanceDto fromActivityInstance(final FlowNodeEventDto activityInstance) {
    return new FlowNodeInstanceDto(
      activityInstance.getProcessDefinitionKey(),
      activityInstance.getProcessDefinitionVersion(),
      activityInstance.getTenantId(),
      activityInstance.getEngineAlias(),
      activityInstance.getProcessInstanceId(),
      activityInstance.getActivityId(),
      activityInstance.getActivityType(),
      activityInstance.getId(),
      activityInstance.getTaskId()
    )
      .setTotalDurationInMs(activityInstance.getDurationInMs())
      .setStartDate(activityInstance.getStartDate())
      .setEndDate(activityInstance.getEndDate())
      .setCanceled(activityInstance.getCanceled());
  }

  protected abstract String createInlineUpdateScript();

  private ImportRequestDto createImportRequestForActivityInstance(Map.Entry<String, List<FlowNodeEventDto>> activitiesByProcessInstance, final String importItemName) {
    final List<FlowNodeEventDto> activityInstances = activitiesByProcessInstance.getValue();
    final String processInstanceId = activitiesByProcessInstance.getKey();

    final List<FlowNodeInstanceDto> flowNodeInstanceDtos = convertToFlowNodeInstanceDtos(activityInstances);
    final Map<String, Object> params = new HashMap<>();
    // see https://discuss.elastic.co/t/how-to-update-nested-objects-in-elasticsearch-2-2-script-via-java-api/43135
    try {
      params.put(FLOW_NODE_INSTANCES, flowNodeInstanceDtos);
      final ScriptData updateScript = DatabaseWriterUtil.createScriptData(
        createInlineUpdateScript(),
        params,
        objectMapper
      );

      final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
        .processInstanceId(processInstanceId)
        .dataSource(new EngineDataSourceDto(activityInstances.get(0).getEngineAlias()))
        .processDefinitionKey(activityInstances.get(0).getProcessDefinitionKey())
        .flowNodeInstances(flowNodeInstanceDtos)
        .build();
      String newEntryIfAbsent = objectMapper.writeValueAsString(procInst);
      return ImportRequestDto.builder()
        .indexName(getProcessInstanceIndexAliasName(procInst.getProcessDefinitionKey()))
        .id(processInstanceId)
        .scriptData(updateScript)
        .type(RequestType.UPDATE)
        .importName(importItemName)
        .source(newEntryIfAbsent)
        .retryNumbOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT)
        .build();
    } catch (IOException e) {
      String reason = String.format(
        "Error while processing JSON for activity instances for process instance ID [%s].",
        processInstanceId
      );
      throw new OptimizeRuntimeException(reason, e);
    }
  }

  private List<FlowNodeInstanceDto> convertToFlowNodeInstanceDtos(List<FlowNodeEventDto> activityInstances) {
    return activityInstances.stream().map(this::fromActivityInstance).collect(Collectors.toList());
  }

}
