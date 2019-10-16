/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class GroupDto extends IdentityDto {
  private String name;

  public GroupDto(final String id) {
    this(id, null);
  }

  public GroupDto(final String id, final String name) {
    super(id, IdentityType.GROUP);
    this.name = name;
  }
}
