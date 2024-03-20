/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.filter.decision.variable.string;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.NOT_CONTAINS;
import static org.camunda.optimize.test.util.decision.DecisionFilterUtilHelper.createStringInputVariableFilter;
import static org.camunda.optimize.util.DmnModels.STRING_INPUT_ID;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.camunda.optimize.dto.optimize.query.report.single.decision.filter.DecisionFilterDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.rest.report.ReportResultResponseDto;
import org.junit.jupiter.api.Tag;

@Tag(OPENSEARCH_PASSING)
public class DecisionStringNotContainsInputVariableQueryFilterIT
    extends AbstractDecisionStringNotContainsVariableQueryFilterIT {

  private static final String INPUT_VARIABLE_ID_TO_FILTER_ON = STRING_INPUT_ID;

  @Override
  protected void assertThatResultDoesNotContainVariables(
      final ReportResultResponseDto<List<RawDataDecisionInstanceDto>> result,
      final String... shouldMatch) {
    assertThat(result.getData())
        .hasSize(shouldMatch.length)
        .extracting(RawDataDecisionInstanceDto::getInputVariables)
        .flatExtracting(Map::values)
        .filteredOn(var -> var.getId().equals(INPUT_VARIABLE_ID_TO_FILTER_ON))
        .extracting(InputVariableEntry::getValue)
        .containsExactlyInAnyOrderElementsOf(Arrays.asList(shouldMatch));
  }

  @Override
  protected List<DecisionFilterDto<?>> createNotContainsFilterForValues(
      final String... variableValues) {
    return Lists.newArrayList(
        createStringInputVariableFilter(
            INPUT_VARIABLE_ID_TO_FILTER_ON, NOT_CONTAINS, variableValues));
  }
}
