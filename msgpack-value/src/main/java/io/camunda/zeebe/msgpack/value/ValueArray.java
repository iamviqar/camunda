/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack.value;

import java.util.Collection;
import java.util.RandomAccess;
import java.util.stream.Stream;

public interface ValueArray<T> extends Iterable<T>, RandomAccess {
  T add();

  T add(final int index);

  Stream<T> stream();

  Collection<T> asCollection();
}
