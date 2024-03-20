/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.report.process.single.usertask.frequency.groupby.date.distributedby.process;

import java.time.OffsetDateTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.util.ProcessReportDataType;

public class UserTaskFrequencyByUserTaskStartDateByProcessReportEvaluationIT
    extends UserTaskFrequencyByUserTaskDateByProcessReportEvaluationIT {

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeUserTaskInstanceDate(
      final ProcessInstanceEngineDto processInstanceDto,
      final String flowNodeId,
      final OffsetDateTime date) {
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto.getId(), flowNodeId, date);
  }
}
