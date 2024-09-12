/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.FormSearchClient;
import io.camunda.service.entities.FormEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.exception.SearchQueryExecutionException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.FormQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class FormServices extends SearchQueryService<FormServices, FormQuery, FormEntity> {

  private final FormSearchClient formSearchClient;

  public FormServices(
      final BrokerClient brokerClient,
      final FormSearchClient formSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.formSearchClient = formSearchClient;
  }

  @Override
  public FormServices withAuthentication(final Authentication authentication) {
    return new FormServices(brokerClient, this.formSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<FormEntity> search(final FormQuery query) {
    return formSearchClient
        .searchForms(query, authentication)
        .fold(
            (e) -> {
              throw new SearchQueryExecutionException("Failed to execute search query", e);
            },
            (r) -> r);
  }

  public FormEntity getByKey(final Long key) {
    final SearchQueryResult<FormEntity> result =
        search(
            SearchQueryBuilders.formSearchQuery().filter(f -> f.keys(key)).build());
    if (result.total() < 1) {
      throw new NotFoundException(String.format("Form with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaServiceException(
          String.format("Found form with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }
}
