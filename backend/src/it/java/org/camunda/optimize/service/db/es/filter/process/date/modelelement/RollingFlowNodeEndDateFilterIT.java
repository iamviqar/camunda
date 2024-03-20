/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.filter.process.date.modelelement;

import java.time.OffsetDateTime;
import java.util.List;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.date.DateUnit;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.FilterApplicationLevel;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.ProcessFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.filter.util.ProcessFilterBuilder;
import org.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;

public class RollingFlowNodeEndDateFilterIT extends AbstractRollingFlowNodeDateFilterIT {

  @Override
  protected ProcessGroupByType getDateReportGroupByType() {
    return ProcessGroupByType.END_DATE;
  }

  @Override
  protected void updateFlowNodeDate(
      final String instanceId, final String flowNodeId, final OffsetDateTime newDate) {
    engineDatabaseExtension.changeFlowNodeEndDate(instanceId, flowNodeId, newDate);
  }

  @Override
  protected List<ProcessFilterDto<?>> createRollingDateViewFilter(
      final Long value, final DateUnit unit) {
    return ProcessFilterBuilder.filter()
        .rollingFlowNodeEndDate()
        .filterLevel(FilterApplicationLevel.VIEW)
        .start(value, unit)
        .add()
        .buildList();
  }

  @Override
  protected List<ProcessFilterDto<?>> createRollingDateInstanceFilter(
      final List<String> flowNodeIds, final Long value, final DateUnit unit) {
    return ProcessFilterBuilder.filter()
        .rollingFlowNodeEndDate()
        .filterLevel(FilterApplicationLevel.INSTANCE)
        .flowNodeIds(flowNodeIds)
        .start(value, unit)
        .add()
        .buildList();
  }
}
