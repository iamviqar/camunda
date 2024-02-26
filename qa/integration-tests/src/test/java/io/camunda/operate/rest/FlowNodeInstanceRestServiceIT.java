/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.FlowNodeInstanceRestService;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      FlowNodeInstanceRestService.class,
      OperateProperties.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class
    })
public class FlowNodeInstanceRestServiceIT extends OperateAbstractIT {

  @MockBean protected ProcessInstanceReader processInstanceReader;
  @MockBean private FlowNodeInstanceReader flowNodeInstanceReader;
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testFlowNodeInstancesFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String treePath = "456";
    final String bpmnProcessId = "processId";
    final FlowNodeInstanceRequestDto requestDto =
        (new FlowNodeInstanceRequestDto())
            .setQueries(
                List.of(
                    new FlowNodeInstanceQueryDto()
                        .setProcessInstanceId(processInstanceId)
                        .setTreePath(treePath)));
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(FLOW_NODE_INSTANCE_URL, requestDto);
    // then
    assertErrorMessageContains(mvcResult, "No read permission for process instance");
  }

  @Test
  public void testFlowNodeInstancesOkWhenHasPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String treePath = "456";
    final String bpmnProcessId = "processId";
    final FlowNodeInstanceRequestDto requestDto =
        (new FlowNodeInstanceRequestDto())
            .setQueries(
                List.of(
                    new FlowNodeInstanceQueryDto()
                        .setProcessInstanceId(processInstanceId)
                        .setTreePath(treePath)));
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
        .thenReturn(true);
    when(flowNodeInstanceReader.getFlowNodeInstances(requestDto)).thenReturn(new LinkedHashMap<>());
    final MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, requestDto);
    final Map<String, FlowNodeInstanceResponseDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    // then
    assertThat(response).isEmpty();
  }
}
