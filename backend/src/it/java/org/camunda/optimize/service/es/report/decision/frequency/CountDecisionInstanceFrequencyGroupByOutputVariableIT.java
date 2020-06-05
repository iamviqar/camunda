/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.decision.frequency;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.group.value.DecisionGroupByVariableValueDto;
import org.camunda.optimize.dto.optimize.query.report.single.group.GroupByDateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import org.camunda.optimize.dto.optimize.query.sorting.SortingDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedDecisionReportEvaluationResultDto;
import org.camunda.optimize.service.es.report.decision.AbstractDecisionDefinitionIT;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.test.util.decision.DecisionTypeRef;
import org.camunda.optimize.test.util.decision.DmnModelGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_KEY;
import static org.camunda.optimize.dto.optimize.query.sorting.SortingDto.SORT_BY_VALUE;
import static org.camunda.optimize.service.es.filter.DateHistogramBucketLimiterUtil.mapToChronoUnit;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createBooleanOutputVariableFilter;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION;
import static org.camunda.optimize.util.DmnModels.OUTPUT_AUDIT_ID;
import static org.camunda.optimize.util.DmnModels.OUTPUT_CLASSIFICATION_ID;

public class CountDecisionInstanceFrequencyGroupByOutputVariableIT extends AbstractDecisionDefinitionIT {

  @Test
  public void reportEvaluationSingleBucketSpecificVersionGroupByStringOutputVariable() {
    // given
    final String expectedClassificationOutputValue = "day-to-day expense";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_CLASSIFICATION_ID, VariableType.STRING
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(expectedClassificationOutputValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(2L);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByBooleanOutputVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // audit = false
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // audit = true
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    assertThat(result.getIsComplete()).isTrue();
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(2);
    assertThat(resultData.get(0).getKey()).isEqualTo("false");
    assertThat(resultData.get(0).getValue()).isEqualTo(3L);
    assertThat(resultData.get(1).getKey()).isEqualTo("true");
    assertThat(resultData.get(1).getValue()).isEqualTo(1L);
  }

  @Test
  public void reportEvaluationMultiBuckets_resultLimitedByConfig() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // audit = false
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // audit = true
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );

    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    embeddedOptimizeExtension.getConfigurationService().setEsAggregationBucketLimit(1);

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    assertThat(result.getIsComplete()).isFalse();
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
  }

  @Test
  public void reportEvaluationMultiBucketsSpecificVersionGroupByBooleanOutputVariableFilterByVariable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // audit = false
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(200.0, "Misc")
    );
    // audit = true
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(2000.0, "Misc")
    );


    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_AUDIT_ID)
      .setVariableType(VariableType.BOOLEAN)
      .setFilter(Lists.newArrayList(createBooleanOutputVariableFilter(
        OUTPUT_AUDIT_ID, Collections.singletonList(true)
      )))
      .build();
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(1L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo("true");
    assertThat(resultData.get(0).getValue()).isEqualTo(1L);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByStringOutputVariable() {
    // given
    final String expectedClassificationOutputValue = "day-to-day expense";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different version
    engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, OUTPUT_CLASSIFICATION_ID, VariableType.STRING
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(expectedClassificationOutputValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(4L);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByBooleanOutputVariable() {
    // given
    final String auditValue = "false";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different version
    engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(4L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(auditValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(4L);
  }

  @Test
  public void reportEvaluationSingleBucketAllVersionsGroupByBooleanInputVariableOtherDefinitionsHaveNoSideEffect() {
    // given
    final String auditValue = "false";
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );

    // different decision definition
    final DecisionDefinitionEngineDto otherDecisionDefinition = deployDecisionDefinitionWithDifferentKey("otherKey");
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      otherDecisionDefinition.getId(), createInputs(100.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto1, ReportConstants.ALL_VERSIONS, OUTPUT_AUDIT_ID, VariableType.BOOLEAN
    ).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(2L);
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(1);
    assertThat(resultData.get(0).getKey()).isEqualTo(auditValue);
    assertThat(resultData.get(0).getValue()).isEqualTo(2L);
  }

  @Test
  public void reportEvaluationSingleBucketFilteredBySingleTenant() {
    // given
    final String tenantId1 = "tenantId1";
    final String tenantId2 = "tenantId2";
    final List<String> selectedTenants = Lists.newArrayList(tenantId1);
    final String decisionDefinitionKey = deployAndStartMultiTenantDefinition(
      Lists.newArrayList(null, tenantId1, tenantId2)
    );

    importAllEngineEntitiesFromScratch();

    // when
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionKey)
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setTenantIds(selectedTenants)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_CLASSIFICATION_ID)
      .setVariableType(VariableType.STRING)
      .build();
    ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    assertThat(result.getInstanceCount()).isEqualTo(selectedTenants.size());
  }

  @Test
  public void testCustomOrderOnStringOutputResultKeyIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // classification: day-to-day expense
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    // classification: budget
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(250.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(900.0, "Misc")
    );
    // classification: exceptional
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(25000.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_CLASSIFICATION_ID)
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_KEY, SortOrder.DESC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(3);
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  public void testCustomOrderOnNumberResultValueIsApplied() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    // classification: day-to-day expense
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(100.0, "Misc")
    );
    // classification: budget
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(250.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(300.0, "Misc")
    );
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(900.0, "Misc")
    );
    // classification: exceptional
    startDecisionInstanceWithInputVars(
      decisionDefinitionDto1.getId(), createInputs(25000.0, "Misc")
    );

    importAllEngineEntitiesFromScratch();

    // when
    final DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(OUTPUT_CLASSIFICATION_ID)
      .setVariableType(VariableType.STRING)
      .build();
    reportData.getConfiguration().setSorting(new SortingDto(SORT_BY_VALUE, SortOrder.ASC));
    final ReportMapResultDto result = reportClient.evaluateMapReport(reportData).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).hasSize(3);
    final List<Long> bucketValues = resultData.stream().map(MapResultEntryDto::getValue).collect(Collectors.toList());
    assertThat(bucketValues).isSortedAccordingTo(Comparator.naturalOrder());
  }

  @Test
  public void testVariableNameIsAvailable() {
    // given
    DecisionDefinitionEngineDto decisionDefinitionDto1 = engineIntegrationExtension.deployDecisionDefinition();
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());


    // different version
    DecisionDefinitionEngineDto decisionDefinitionDto2 = engineIntegrationExtension.deployAndStartDecisionDefinition();
    engineIntegrationExtension.startDecisionInstance(decisionDefinitionDto2.getId());

    importAllEngineEntitiesFromScratch();

    // when
    final AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> result =
      evaluateDecisionInstanceFrequencyByOutputVariable(
        decisionDefinitionDto1, decisionDefinitionVersion1, OUTPUT_AUDIT_ID, "audit", VariableType.BOOLEAN
      );

    // then
    final DecisionGroupByVariableValueDto value = (DecisionGroupByVariableValueDto)
      result.getReportDefinition().getData().getGroupBy().getValue();
    assertThat(value.getName()).isPresent();
    assertThat(value.getName().get()).isEqualTo("audit");
  }

  @Test
  public void dateVariableGroupByWithOneInstance() {
    // given
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE
    ).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(1);
    assertThat(result.getData().get(0).getValue()).isEqualTo(1L);
  }

  @Test
  public void dateVariableGroupByWithSeveralInstances() {
    // given
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();

    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusDays(1L))
    );
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusDays(1L))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE
    ).getResult();

    // then
    final List<MapResultEntryDto> resultData = result.getData();
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);
    assertThat(resultData.get(0).getValue()).isEqualTo(1L);
    assertThat(resultData.get(resultData.size() - 1).getValue()).isEqualTo(2L);
  }

  @Test
  public void dateVariablesAreSortedDescByDefault() {
    // given
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );

    OffsetDateTime now = LocalDateUtil.getCurrentDateTime();
    engineIntegrationExtension.startDecisionInstance(definition.getId(), ImmutableMap.of(camInputVariable, now));
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusSeconds(1L))
    );
    engineIntegrationExtension.startDecisionInstance(
      definition.getId(),
      ImmutableMap.of(camInputVariable, now.minusSeconds(6L))
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE
    ).getResult();


    // then
    final List<MapResultEntryDto> resultData = result.getData();
    final List<String> resultKeys = resultData.stream().map(MapResultEntryDto::getKey).collect(Collectors.toList());
    assertThat(resultKeys).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @ParameterizedTest
  @MethodSource("staticGroupByDateUnits")
  public void dateVariableGroupByWorksWithAllStaticUnits(final GroupByDateUnit unit) {
    // given
    final int numberOfInstances = 3;
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );
    final ChronoUnit chronoUnit = mapToChronoUnit(unit);
    OffsetDateTime dateVariableValue = OffsetDateTime.now();

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plus(1, chronoUnit);
      Map<String, Object> variables = ImmutableMap.of(camInputVariable, dateVariableValue);
      engineIntegrationExtension.startDecisionInstance(definition.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE, unit
    ).getResult().getData();

    // then
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(numberOfInstances);
    for (int i = 0; i < numberOfInstances; i++) {
      final String expectedBucketKey = embeddedOptimizeExtension.formatToHistogramBucketKey(
        dateVariableValue.minus(chronoUnit.getDuration().multipliedBy(i)),
        chronoUnit
      );
      assertThat(resultData.get(i).getValue()).isEqualTo(1);
      assertThat(resultData.get(i).getKey()).isEqualTo(expectedBucketKey);
    }
  }

  @SneakyThrows
  @Test
  public void dateVariableGroupByWithAutomaticIntervals() {
    // given
    final int numberOfInstances = 3;
    final String outputClauseId = "outputClauseId";
    final String camInputVariable = "input";
    final DecisionDefinitionEngineDto definition = deploySimpleDecisionDefinition(
      outputClauseId,
      camInputVariable,
      DecisionTypeRef.DATE
    );
    OffsetDateTime dateVariableValue = OffsetDateTime.now();

    for (int i = 0; i < numberOfInstances; i++) {
      dateVariableValue = dateVariableValue.plusMinutes(1);
      Map<String, Object> variables =
        ImmutableMap.of(camInputVariable, dateVariableValue);
      engineIntegrationExtension.startDecisionInstance(definition.getId(), variables);
    }

    importAllEngineEntitiesFromScratch();

    // when
    final List<MapResultEntryDto> resultData = evaluateDecisionInstanceFrequencyByOutputVariable(
      definition, definition.getVersionAsString(), outputClauseId, null, VariableType.DATE, GroupByDateUnit.AUTOMATIC
    ).getResult().getData();

    // then
    assertThat(resultData).isNotNull();
    assertThat(resultData).hasSize(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION);

    // the bucket span covers the earliest and the latest date variable values
    DateTimeFormatter formatter = embeddedOptimizeExtension.getDateTimeFormatter();
    final OffsetDateTime startOfFirstBucket = OffsetDateTime.from(formatter.parse(resultData.get(0).getKey()));
    final OffsetDateTime startOfLastBucket = OffsetDateTime
      .from(formatter.parse(resultData.get(NUMBER_OF_DATA_POINTS_FOR_AUTOMATIC_INTERVAL_SELECTION - 1).getKey()));
    final OffsetDateTime firstTruncatedDateVariableValue = dateVariableValue.truncatedTo(ChronoUnit.MILLIS);
    final OffsetDateTime lastTruncatedDateVariableValue =
      dateVariableValue.minusMinutes(numberOfInstances).truncatedTo(ChronoUnit.MILLIS);

    assertThat(startOfFirstBucket).isBeforeOrEqualTo(firstTruncatedDateVariableValue);
    assertThat(startOfLastBucket).isAfterOrEqualTo(lastTruncatedDateVariableValue);
  }

  @Test
  public void missingVariablesAggregationForUndefinedAndNullOutputVariables() {
    // given
    final String outputClauseId = "Donald";
    final String camInputVariable = "input";

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputClauseId,
      camInputVariable,
      "testValidMatch",
      DecisionTypeRef.STRING
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );
    engineDatabaseExtension.setDecisionOutputStringVariableValueToNull(outputClauseId, "testValidMatch");

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      ImmutableMap.of(camInputVariable, "testValidMatch")
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(camInputVariable, null)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, decisionDefinitionDto.getVersionAsString(), outputClauseId, null, VariableType.STRING
    ).getResult();


    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey("testValidMatch").get().getValue()).isEqualTo(1L);
    assertThat(result.getEntryForKey("missing").get().getValue()).isEqualTo(2L);
  }

  @Test
  public void missingVariablesAggregationForNullVariableOfTypeDouble_sortingByKeyDoesNotFail() {
    // given a decision instance with non null variable value and one instance with null variable value
    final String outputVarName = "outputVarName";
    final String inputVarName = "inputVarName";
    final Double doubleVarValue = 1.0;

    final DecisionDefinitionEngineDto decisionDefinitionDto = deploySimpleOutputDecisionDefinition(
      outputVarName,
      inputVarName,
      String.valueOf(doubleVarValue),
      DecisionTypeRef.DOUBLE
    );

    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, null)
    );
    engineIntegrationExtension.startDecisionInstance(
      decisionDefinitionDto.getId(),
      Collections.singletonMap(inputVarName, doubleVarValue)
    );

    importAllEngineEntitiesFromScratch();

    // when
    final ReportMapResultDto result = evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, decisionDefinitionDto.getVersionAsString(), outputVarName, null, VariableType.DOUBLE
    ).getResult();

    // then
    assertThat(result.getData()).isNotNull();
    assertThat(result.getData()).hasSize(2);
    assertThat(result.getEntryForKey(String.valueOf(doubleVarValue)).get().getValue()).isEqualTo(1L);
    assertThat(result.getEntryForKey("missing").get().getValue()).isEqualTo(1L);
  }

  @Test
  public void optimizeExceptionOnViewPropertyIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .build();
    reportData.getView().setProperty(null);

    //when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
  }

  @Test
  public void optimizeExceptionOnGroupByTypeIsNull() {
    // given
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey("key")
      .setDecisionDefinitionVersion(ReportConstants.ALL_VERSIONS)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .build();
    reportData.getGroupBy().setType(null);

    //when
    Response response = reportClient.evaluateReportAndReturnResponse(reportData);

    // then
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
  }

  private AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final VariableType variableType) {
    return evaluateDecisionInstanceFrequencyByOutputVariable(
      decisionDefinitionDto, decisionDefinitionVersion, variableId, null, variableType
    );
  }

  private AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType) {
    DecisionReportDataDto reportData = createReportDataDto(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType
    );
    return reportClient.evaluateMapReport(reportData);
  }

  private AuthorizedDecisionReportEvaluationResultDto<ReportMapResultDto> evaluateDecisionInstanceFrequencyByOutputVariable(
    final DecisionDefinitionEngineDto decisionDefinitionDto,
    final String decisionDefinitionVersion,
    final String variableId,
    final String variableName,
    final VariableType variableType,
    final GroupByDateUnit unit) {
    DecisionReportDataDto reportData = createReportDataDto(
      decisionDefinitionDto,
      decisionDefinitionVersion,
      variableId,
      variableName,
      variableType
    );
    reportData.getConfiguration().setGroupByDateVariableUnit(unit);
    return reportClient.evaluateMapReport(reportData);
  }

  private DecisionReportDataDto createReportDataDto(final DecisionDefinitionEngineDto decisionDefinitionDto,
                                                    final String decisionDefinitionVersion,
                                                    final String variableId,
                                                    final String variableName,
                                                    final VariableType variableType) {
    return DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_OUTPUT_VARIABLE)
      .setVariableId(variableId)
      .setVariableName(variableName)
      .setVariableType(variableType)
      .build();
  }


  private DecisionDefinitionEngineDto deploySimpleDecisionDefinition(final String outputClauseId,
                                                                     final String camInputVariable,
                                                                     final DecisionTypeRef type) {
    final DmnModelGenerator dmnModelGenerator = DmnModelGenerator.create()
      .decision()
      .addInput("input", camInputVariable, type)
      .addOutput("output", outputClauseId, camInputVariable, type)
      .rule()
      .addStringInputEntry(camInputVariable)
      .addStringOutputEntry(camInputVariable)
      .buildRule()
      .buildDecision();
    return engineIntegrationExtension.deployDecisionDefinition(dmnModelGenerator.build());
  }
}
