/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.job;

import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.CamundaEventImportService;
import org.camunda.optimize.service.db.DatabaseClient;
import org.camunda.optimize.service.db.writer.variable.ProcessVariableUpdateWriter;
import org.camunda.optimize.service.importing.DatabaseImportJob;
import org.camunda.optimize.service.util.configuration.ConfigurationService;

import java.util.ArrayList;
import java.util.List;

public class VariableUpdateDatabaseImportJob extends DatabaseImportJob<ProcessVariableDto> {

  private final ProcessVariableUpdateWriter processVariableUpdateWriter;
  private final CamundaEventImportService camundaEventImportService;
  private final ConfigurationService configurationService;

  public VariableUpdateDatabaseImportJob(final ProcessVariableUpdateWriter variableWriter,
                                         final CamundaEventImportService camundaEventImportService,
                                         final ConfigurationService configurationService,
                                         final Runnable callback,
                                         final DatabaseClient databaseClient) {
    super(callback, databaseClient);
    this.processVariableUpdateWriter = variableWriter;
    this.camundaEventImportService = camundaEventImportService;
    this.configurationService = configurationService;
  }

  @Override
  protected void persistEntities(List<ProcessVariableDto> variableUpdates) {
    List<ImportRequestDto> importBulks = new ArrayList<>();
    importBulks.addAll(processVariableUpdateWriter.generateVariableUpdateImports(variableUpdates));
    importBulks.addAll(camundaEventImportService.generateVariableUpdateImports(variableUpdates));
    databaseClient.executeImportRequestsAsBulk(
      "Variable updates",
      importBulks,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

}
