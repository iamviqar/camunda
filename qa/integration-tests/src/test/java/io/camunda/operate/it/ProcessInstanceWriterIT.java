/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.SequenceFlow;
import io.camunda.operate.webapp.api.v1.entities.Variable;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProcessInstanceWriterIT extends OperateSearchAbstractIT {

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Override
  public void runAdditionalBeforeAllSetup() {
    //operateTester.deployProcessAndWait("single-task.bpmn");
  }

  @Test
  public void shouldDeleteFinishedInstanceById() throws IOException {
    Long processInstanceKey = 4503599627370497L;
    ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity()
        .setId(String.valueOf(processInstanceKey)).setKey(processInstanceKey).setProcessDefinitionKey(2251799813685248L).setProcessInstanceKey(4503599627370497L)
        .setProcessName("Demo process parent").setBpmnProcessId("demoProcess").setState(ProcessInstanceState.COMPLETED)
        .setStartDate(OffsetDateTime.now()).setEndDate(OffsetDateTime.now())
        .setTreePath("PI_4503599627370497").setTenantId(DEFAULT_TENANT_ID).setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstance().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlow().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new Variable().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*");

    processInstanceWriter.deleteInstanceById(processInstance.getProcessInstanceKey());

    searchContainerManager.refreshIndices("*");

    assertThrows(NotFoundException.class, () ->  processInstanceReader.getProcessInstanceByKey(processInstance.getProcessInstanceKey()));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  @Test
  public void shouldDeleteCancelledInstanceById() throws IOException {
    Long processInstanceKey = 4503599627370497L;
    ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity()
        .setId(String.valueOf(processInstanceKey)).setKey(processInstanceKey).setProcessDefinitionKey(2251799813685248L).setProcessInstanceKey(4503599627370497L)
        .setProcessName("Demo process parent").setBpmnProcessId("demoProcess").setState(ProcessInstanceState.CANCELED)
        .setStartDate(OffsetDateTime.now()).setEndDate(OffsetDateTime.now())
        .setTreePath("PI_4503599627370497").setTenantId(DEFAULT_TENANT_ID).setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstance().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlow().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new Variable().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*");

    processInstanceWriter.deleteInstanceById(processInstanceKey);

    searchContainerManager.refreshIndices("*");

    assertThrows(NotFoundException.class, () ->  processInstanceReader.getProcessInstanceByKey(processInstanceKey));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  @Test
  public void shouldFailDeleteInstanceByIdWithInvalidState() throws IOException {
    Long processInstanceKey = 4503599627370497L;
    ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity()
        .setId(String.valueOf(processInstanceKey)).setKey(processInstanceKey).setProcessDefinitionKey(2251799813685248L).setProcessInstanceKey(4503599627370497L)
        .setProcessName("Demo process parent").setBpmnProcessId("demoProcess").setState(ProcessInstanceState.ACTIVE)
        .setStartDate(OffsetDateTime.now()).setEndDate(OffsetDateTime.now())
        .setTreePath("PI_4503599627370497").setTenantId(DEFAULT_TENANT_ID).setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*");

    assertThrows(IllegalArgumentException.class, () ->  processInstanceWriter.deleteInstanceById(processInstanceKey));

    // Cleanup so as not to interfere with other tests
    processInstance.setState(ProcessInstanceState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*");
    processInstanceWriter.deleteInstanceById(processInstanceKey);
  }

  private void assertThatDependantsAreAlsoDeleted(final long finishedProcessInstanceKey) throws IOException {
    for (ProcessInstanceDependant t : processInstanceDependants) {
      if (!(t instanceof OperationTemplate)) {
        var index = t.getFullQualifiedName() + "*";
        var field = ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
        var response = testSearchRepository.searchTerm(index, field, finishedProcessInstanceKey, Object.class, 100);
        assertThat(response.size()).isZero();
      }
    }
  }

  private String getFullIndexNameForDependant(String indexName) {
    ProcessInstanceDependant dependant = processInstanceDependants.stream().filter(template ->
        template.getFullQualifiedName().contains(indexName)).findAny().orElse(null);

    return dependant.getFullQualifiedName();
  }
}
