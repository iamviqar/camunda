/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.job.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.FailJobResponse;
import io.camunda.zeebe.client.protocol.rest.JobFailRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.JsonUtil;
import java.time.Duration;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class FailJobRestTest extends ClientRestTest {

  @Test
  public void shouldFailJobByKey() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newFailCommand(jobKey).retries(newRetries).send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getRetries()).isEqualTo(newRetries);
  }

  @Test
  public void shouldFailJob() {
    // given
    final int newRetries = 23;
    final ActivatedJob job = Mockito.mock(ActivatedJob.class);
    Mockito.when(job.getKey()).thenReturn(12L);

    // when
    client.newFailCommand(job).retries(newRetries).send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getRetries()).isEqualTo(newRetries);
  }

  @Test
  public void shouldFailJobWithMessage() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    client.newFailCommand(jobKey).retries(newRetries).errorMessage("failed message").send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getErrorMessage()).isEqualTo("failed message");
  }

  @Test
  public void shouldFailJobWithBackoff() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;

    // when
    final Duration backoffTimeout = Duration.ofSeconds(1);
    client.newFailCommand(jobKey).retries(newRetries).retryBackoff(backoffTimeout).send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getRetryBackOff()).isEqualTo(backoffTimeout.toMillis());
  }

  @Test
  public void shouldFailJobWithBackoffAndMessage() {
    // given
    final long jobKey = 12;
    final int newRetries = 23;
    final String message = "failed message";

    // when
    final Duration backoffTimeout = Duration.ofSeconds(1);
    client
        .newFailCommand(jobKey)
        .retries(newRetries)
        .retryBackoff(backoffTimeout)
        .errorMessage(message)
        .send()
        .join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getRetries()).isEqualTo(newRetries);
    assertThat(request.getRetryBackOff()).isEqualTo(backoffTimeout.toMillis());
    assertThat(request.getErrorMessage()).isEqualTo(message);
  }

  @Test
  public void shouldFailJobWithJsonStringVariables() {
    // given
    final long jobKey = 12;
    final int newRetries = 0;

    final String json = JsonUtil.toJson(Collections.singletonMap("key", "val"));

    // when
    final FailJobResponse response =
        client.newFailCommand(jobKey).retries(newRetries).variables(json).send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    JsonUtil.assertEquality(JsonUtil.toJson(request.getVariables()), json);
  }

  @Test
  public void shouldFailJobWithSingleVariable() {
    // given
    final long jobKey = 12;
    final int newRetries = 0;
    final String key = "key";
    final String value = "value";

    // when
    final FailJobResponse response =
        client.newFailCommand(jobKey).retries(newRetries).variable(key, value).send().join();

    // then
    final JobFailRequest request = gatewayService.getLastRequest(JobFailRequest.class);
    assertThat(request.getVariables()).containsOnly(entry(key, value));
  }

  @Test
  public void shouldThrowErrorWhenTryToFailJobWithNullVariable() {
    // when
    final long jobKey = 12;
    final int newRetries = 0;
    Assertions.assertThatThrownBy(
            () ->
                client
                    .newFailCommand(jobKey)
                    .retries(newRetries)
                    .variable(null, null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
