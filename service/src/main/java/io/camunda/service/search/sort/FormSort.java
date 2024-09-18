/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.sort;

import io.camunda.search.clients.sort.SortOrder;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.function.Function;

public record FormSort(List<FieldSorting> orderings) implements SortOption {

  @Override
  public List<FieldSorting> getFieldSortings() {
    return orderings;
  }

  public static FormSort of(final Function<Builder, ObjectBuilder<FormSort>> fn) {
    return SortOptionBuilders.form(fn);
  }

  public static final class Builder extends AbstractBuilder<Builder>
      implements ObjectBuilder<FormSort> {

    @Override
    protected Builder self() {
      return this;
    }

    @Override
    public Builder asc() {
      return addOrdering(SortOrder.ASC);
    }

    @Override
    public Builder desc() {
      return addOrdering(SortOrder.DESC);
    }

    @Override
    public FormSort build() {
      return new FormSort(orderings);
    }
  }
}
