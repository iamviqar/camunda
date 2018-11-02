package org.camunda.optimize.service.es.report;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.ExecutedFlowNodeFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.FilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.VariableFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.util.ExecutedFlowNodeFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataSingleReportResultDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.command.util.ReportConstants;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.DateUtilHelper;
import org.camunda.optimize.test.util.ReportDataBuilderHelper;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.VIEW_RAW_DATA_OPERATION;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.IsNull.notNullValue;


public class RawDataReportEvaluationIT {

  private static final String BUSINESS_KEY = "aBusinessKey";


  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  @Test
  public void reportAcrossAllVersions() {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), ReportConstants.ALL_VERSIONS);
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(2L));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(ReportConstants.ALL_VERSIONS));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));
  }

  @Test
  public void otherProcessDefinitionsDoNoAffectResult() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(result.getProcessInstanceCount(), is(1L));
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getBusinessKey(), is(BUSINESS_KEY));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationForOneProcessInstance() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);

    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  @Test
  public void reportEvaluationByIdForOneProcessInstance() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
    String reportId = createAndStoreDefaultReportDefinition(processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());

    // when
    RawDataSingleReportResultDto result = evaluateReportById(reportId);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
    assertThat(rawDataProcessInstanceDto.getStartDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEndDate(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getEngineName(), is("1"));
    assertThat(rawDataProcessInstanceDto.getVariables(), is(notNullValue()));
    assertThat(rawDataProcessInstanceDto.getVariables().size(), is(0));
  }

  private String createAndStoreDefaultReportDefinition(String processDefinitionKey, String processDefinitionVersion) {
    String id = createNewReport();
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(processDefinitionKey, processDefinitionVersion);
    SingleReportDefinitionDto report = new SingleReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  @Test
  public void reportEvaluationWithSeveralProcessInstances() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    ProcessInstanceEngineDto processInstance2 = engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));
    Set<String> expectedProcessInstanceIds = new HashSet<>();
    expectedProcessInstanceIds.add(processInstance.getId());
    expectedProcessInstanceIds.add(processInstance2.getId());
    for (RawDataProcessInstanceDto rawDataProcessInstanceDto : result.getResult()) {
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
      assertThat(rawDataProcessInstanceDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
      String actualProcessInstanceId = rawDataProcessInstanceDto.getProcessInstanceId();
      assertThat(expectedProcessInstanceIds.contains(actualProcessInstanceId), is(true));
      expectedProcessInstanceIds.remove(actualProcessInstanceId);
    }
  }

  @Test
  public void reportEvaluationOnProcessInstanceWithAllVariableTypes() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("stringVar", "Hello World!");
    variables.put("boolVar", true);
    variables.put("shortVar", (short) 2);
    variables.put("intVar", 2);
    variables.put("longVar", "Hello World!");
    variables.put("dateVar", new Date());

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
    rawDataProcessInstanceDto.getVariables().
      forEach((varName, varValue) -> {
          assertThat(variables.keySet().contains(varName), is(true));
          assertThat(variables.get(varName), is(notNullValue()));
        }
      );
  }

  @Test
  public void resultShouldBeOrderAccordingToStartDate() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    ProcessInstanceEngineDto processInstanceDto2 =  engineRule.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto2.getId(), OffsetDateTime.now().minusDays(2));
    ProcessInstanceEngineDto processInstanceDto3 =
      engineRule.startProcessInstance(processInstance.getDefinitionId());
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceDto3.getId(), OffsetDateTime.now().minusDays(1));
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(),processInstance.getProcessDefinitionVersion());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    List<RawDataProcessInstanceDto> rawDataList = result.getResult();
    assertThat(rawDataList, isInDescendingOrdering());
  }

  private Matcher<? super List<RawDataProcessInstanceDto>> isInDescendingOrdering()
  {
    return new TypeSafeMatcher<List<RawDataProcessInstanceDto>>()
    {
      @Override
      public void describeTo (Description description)
      {
        description.appendText("The given list should be sorted in ascending order!");
      }

      @Override
      protected boolean matchesSafely (List<RawDataProcessInstanceDto> items)
      {
        for(int i = (items.size()-1) ; i > 0 ; i--) {
          if(items.get(i).getStartDate().isAfter(items.get(i-1).getStartDate())) return false;
        }
        return true;
      }
    };
  }

  @Test
  public void variablesOfOneProcessInstanceAreAddedToOther() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("varName1", "value1");

    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    variables.clear();
    variables.put("varName2", "value2");
    engineRule.startProcessInstance(processInstance.getDefinitionId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(),processInstance.getProcessDefinitionVersion());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(2));
    result.getResult().forEach(
      rawDataProcessInstanceDto1 -> {
        Map<String, Object> vars = rawDataProcessInstanceDto1.getVariables();
        assertThat(vars.keySet().size(), is(2));
        assertThat(vars.values().contains(""), is(true));
        // ensure is ordered
        List<String> actual = new ArrayList<>(vars.keySet());
        List<String> expected = new ArrayList<>(vars.keySet());
        Collections.sort(expected);
        assertThat(actual, contains(expected.toArray()));
      }
    );
  }

  @Test
  public void evaluationReturnsOnlyDataToGivenProcessDefinitionId() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    deployAndStartSimpleProcess();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion()
    );
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessDefinitionId(), is(processInstance.getDefinitionId()));
  }

  //test that basic support for filter is there
  @Test
  public void durationFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDurationFilter(">", 1, "Days"));
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));

    // when
    reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createDurationFilter("<", 1, "Days"));
    result = evaluateReport(reportData);

    // then
    resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }


  @Test
  public void dateFilterInReport() throws Exception {
    // given
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    OffsetDateTime past = engineRule.getHistoricProcessInstance(processInstance.getId()).getStartTime();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(null, past.minusSeconds(1L)));
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(0));

    // when
    reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(DateUtilHelper.createFixedStartDateFilter(past, null));
    result = evaluateReport(reportData);

    // then
    resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(resultDataDto.getView(), is(notNullValue()));
    assertThat(resultDataDto.getView().getOperation(), is(VIEW_RAW_DATA_OPERATION));
    assertThat(result.getResult(), is(notNullValue()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void variableFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("var", true);
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcessWithVariables(variables);

    engineRule.startProcessInstance(processInstance.getDefinitionId());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processInstance.getProcessDefinitionKey(), processInstance.getProcessDefinitionVersion());
    reportData.setFilter(createVariableFilter());
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processInstance.getProcessDefinitionKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(processInstance.getProcessDefinitionVersion()));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  private List<FilterDto> createVariableFilter() {
    BooleanVariableFilterDataDto data = new BooleanVariableFilterDataDto("true");
    data.setName("var");

    VariableFilterDto variableFilterDto = new VariableFilterDto();
    variableFilterDto.setData(data);
    return Collections.singletonList(variableFilterDto);
  }

  @Test
  public void flowNodeFilterInReport() throws Exception {
    // given
    Map<String, Object> variables = new HashMap<>();
    variables.put("goToTask1", true);
    ProcessDefinitionEngineDto processDefinition = deploySimpleGatewayProcessDefinition();
    ProcessInstanceEngineDto processInstance = engineRule.startProcessInstance(processDefinition.getId(), variables);
    variables.put("goToTask1", false);
    engineRule.startProcessInstance(processDefinition.getId(), variables);
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // when
    SingleReportDataDto reportData = ReportDataBuilderHelper.createReportDataViewRawAsTable(
        processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
    List<ExecutedFlowNodeFilterDto> flowNodeFilter = ExecutedFlowNodeFilterBuilder.construct()
          .id("task1")
          .build();
    reportData.getFilter().addAll(flowNodeFilter);
    RawDataSingleReportResultDto result = evaluateReport(reportData);

    // then
    SingleReportDataDto resultDataDto = result.getData();
    assertThat(resultDataDto.getProcessDefinitionKey(), is(processDefinition.getKey()));
    assertThat(resultDataDto.getProcessDefinitionVersion(), is(String.valueOf(processDefinition.getVersion())));
    assertThat(result.getResult().size(), is(1));
    RawDataProcessInstanceDto rawDataProcessInstanceDto = result.getResult().get(0);
    assertThat(rawDataProcessInstanceDto.getProcessInstanceId(), is(processInstance.getId()));
  }

  @Test
  public void testValidationExceptionOnNullDto() {
    //when
    Response response = evaluateReportAndReturnResponse(null);

    // then
    assertThat(response.getStatus() ,is(500));
  }

  @Test
  public void missingProcessDefinition() {

    //when
    SingleReportDataDto dataDto = ReportDataBuilderHelper.createReportDataViewRawAsTable(null, null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus() ,is(500));
  }

  @Test
  public void missingViewField() {
    //when
    SingleReportDataDto dataDto = ReportDataBuilderHelper.createReportDataViewRawAsTable(null, null);
    dataDto.setView(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingOperationField() {
    //when
    SingleReportDataDto dataDto = ReportDataBuilderHelper.createReportDataViewRawAsTable(null, null);
    dataDto.getView().setOperation(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  @Test
  public void missingVisualizationField() {
    //when
    SingleReportDataDto dataDto = ReportDataBuilderHelper.createReportDataViewRawAsTable(null, null);
    dataDto.setVisualization(null);
    Response response = evaluateReportAndReturnResponse(dataDto);

    // then
    assertThat(response.getStatus(), is(500));
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables, BUSINESS_KEY);
  }

  private ProcessDefinitionEngineDto deploySimpleGatewayProcessDefinition() throws Exception {
    BpmnModelInstance modelInstance = Bpmn.createExecutableProcess()
      .startEvent("startEvent")
      .exclusiveGateway("splittingGateway")
        .name("Should we go to task 1?")
        .condition("yes", "${goToTask1}")
        .serviceTask("task1")
          .camundaExpression("${true}")
      .exclusiveGateway("mergeGateway")
        .endEvent("endEvent")
      .moveToNode("splittingGateway")
        .condition("no", "${!goToTask1}")
        .serviceTask("task2")
          .camundaExpression("${true}")
        .connectTo("mergeGateway")
      .done();
    return engineRule.deployProcessAndGetProcessDefinition(modelInstance);
  }

  private RawDataSingleReportResultDto evaluateReport(SingleReportDataDto reportData) {
    Response response = evaluateReportAndReturnResponse(reportData);
    assertThat(response.getStatus(), is(200));

    return response.readEntity(RawDataSingleReportResultDto.class);
  }

  private RawDataSingleReportResultDto evaluateReportById(String reportId) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSavedReportRequest(reportId)
            .execute(RawDataSingleReportResultDto.class, 200);
  }

  private Response evaluateReportAndReturnResponse(SingleReportDataDto reportData) {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildEvaluateSingleUnsavedReportRequest(reportData)
            .execute();
  }


  private String createNewReport() {
    return embeddedOptimizeRule
            .getRequestExecutor()
            .buildCreateSingleReportRequest()
            .execute(IdDto.class, 200)
            .getId();
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeRule
            .getRequestExecutor()
            .buildUpdateReportRequest(id, updatedReport)
            .execute();
    assertThat(response.getStatus(), is(204));
  }
}
