/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.join.ScoreMode;
import org.camunda.optimize.dto.optimize.query.PageResultDto;
import org.camunda.optimize.service.db.reader.DefinitionInstanceReader;
import org.camunda.optimize.service.db.reader.ProcessInstanceReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.PROCESS_INSTANCE_ID;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.service.util.InstanceIndexUtil.isInstanceIndexNotFoundException;
import static org.camunda.optimize.service.db.DatabaseConstants.MAX_RESPONSE_SIZE_LIMIT;
import static org.elasticsearch.core.TimeValue.timeValueSeconds;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.nestedQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class ProcessInstanceReaderES implements ProcessInstanceReader {

  private final ConfigurationService configurationService;
  private final OptimizeElasticsearchClient esClient;
  private final DateTimeFormatter dateTimeFormatter;
  private final DefinitionInstanceReader definitionInstanceReader;

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                                               final OffsetDateTime endDate,
                                                                                               final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
      processDefinitionKey,
      boolQuery()
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate)))
        .filter(nestedQuery(VARIABLES, existsQuery(VARIABLES + "." + VARIABLE_ID), ScoreMode.None)),
      limit
    );
  }

  @Override
  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(final String processDefinitionKey,
                                                                                              final OffsetDateTime endDate,
                                                                                              final Integer limit,
                                                                                              final PageResultDto<String> previousPage) {
    return getNextPageOfProcessInstanceIds(
      previousPage,
      () -> getFirstPageOfProcessInstanceIdsThatHaveVariablesAndEndedBefore(processDefinitionKey, endDate, limit)
    );
  }

  @Override
  public PageResultDto<String> getFirstPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                               final OffsetDateTime endDate,
                                                                               final Integer limit) {
    return getFirstPageOfProcessInstanceIdsForFilter(
      processDefinitionKey,
      boolQuery()
        .filter(rangeQuery(ProcessInstanceIndex.END_DATE).lt(dateTimeFormatter.format(endDate))),
      limit
    );
  }

  @Override
  public PageResultDto<String> getNextPageOfProcessInstanceIdsThatEndedBefore(final String processDefinitionKey,
                                                                              final OffsetDateTime endDate,
                                                                              final Integer limit,
                                                                              final PageResultDto<String> previousPage) {
    return getNextPageOfProcessInstanceIds(
      previousPage,
      () -> getFirstPageOfProcessInstanceIdsThatEndedBefore(processDefinitionKey, endDate, limit)
    );
  }

  @Override
  public Set<String> getExistingProcessDefinitionKeysFromInstances() {
    return definitionInstanceReader.getAllExistingDefinitionKeys(PROCESS);
  }

  @Override
  public Optional<String> getProcessDefinitionKeysForInstanceId(final String instanceId) {
    return definitionInstanceReader.getAllExistingDefinitionKeys(PROCESS, Collections.singleton(instanceId))
      .stream()
      .findFirst();
  }

  @Override
  public boolean processDefinitionHasStartedInstances(final String processDefinitionKey) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(boolQuery().filter(existsQuery(ProcessInstanceIndex.START_DATE)))
      .size(1)
      .fetchSource(PROCESS_INSTANCE_ID, null);

    final SearchRequest searchRequest = new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionKey))
      .source(searchSourceBuilder);
    final SearchResponse response;
    try {
      response = esClient.search(searchRequest);
      return response.getHits().getHits().length > 0;
    } catch (ElasticsearchStatusException e) {
      // If the index doesn't exist yet, then this exception is thrown. No need to worry, just return false
      return false;
    } catch (IOException e2) {
      // If this exception is thrown, sth went wrong with ElasticSearch, so returning false and logging it
      log.warn("Error with ElasticSearch thrown while querying for started process instances, returning false! The " +
                 "error was: " + e2.getMessage());
      return false;
    }
  }

  private PageResultDto<String> getNextPageOfProcessInstanceIds(final PageResultDto<String> previousPage,
                                                                final Supplier<PageResultDto<String>> firstPageFetchFunction) {
    if (previousPage.isLastPage()) {
      return new PageResultDto<>(previousPage.getLimit());
    }
    try {
      return ElasticsearchReaderUtil.retrieveNextScrollResultsPage(
        previousPage.getPagingState(),
        String.class,
        searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID),
        esClient,
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds(),
        previousPage.getLimit()
      );
    } catch (ElasticsearchStatusException e) {
      if (RestStatus.NOT_FOUND.equals(e.status())) {
        // this error occurs when the scroll id expired in the meantime, thus just restart it
        return firstPageFetchFunction.get();
      }
      throw e;
    }
  }

  private PageResultDto<String> getFirstPageOfProcessInstanceIdsForFilter(final String processDefinitionKey,
                                                                          final BoolQueryBuilder filterQuery,
                                                                          final Integer limit) {
    final PageResultDto<String> result = new PageResultDto<>(limit);
    final Integer resolvedLimit = Optional.ofNullable(limit).orElse(MAX_RESPONSE_SIZE_LIMIT);
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(filterQuery)
      .fetchSource(PROCESS_INSTANCE_ID, null)
      // size of each scroll page, needs to be capped to max size of elasticsearch
      .size(resolvedLimit <= MAX_RESPONSE_SIZE_LIMIT ? resolvedLimit : MAX_RESPONSE_SIZE_LIMIT);

    final SearchRequest scrollSearchRequest = new SearchRequest(getProcessInstanceIndexAliasName(processDefinitionKey))
      .source(searchSourceBuilder)
      .scroll(timeValueSeconds(configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds()));

    try {
      final SearchResponse response = esClient.search(scrollSearchRequest);
      result.getEntities().addAll(ElasticsearchReaderUtil.mapHits(
        response.getHits(),
        resolvedLimit,
        String.class,
        searchHit -> (String) searchHit.getSourceAsMap().get(PROCESS_INSTANCE_ID)
      ));
      result.setPagingState(response.getScrollId());
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Could not obtain process instance ids.", e);
    } catch (ElasticsearchStatusException e) {
      if (isInstanceIndexNotFoundException(PROCESS, e)) {
        log.info(
          "Was not able to obtain process instance IDs because instance index {} does not exist. Returning empty result.",
          getProcessInstanceIndexAliasName(processDefinitionKey)
        );
        result.setPagingState(null);
        return result;
      }
      throw e;
    }

    return result;
  }

}
