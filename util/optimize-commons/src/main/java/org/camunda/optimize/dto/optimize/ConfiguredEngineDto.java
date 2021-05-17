/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConfiguredEngineDto extends ConfiguredDataSourceDto {

  private String alias;

  public ConfiguredEngineDto(final String engineAlias) {
    super(DataImportSourceType.ENGINE);
    this.alias = engineAlias;
  }

}
