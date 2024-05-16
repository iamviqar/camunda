/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManager.InconsistentConfigurationListener;
import io.camunda.zeebe.dynamic.config.ClusterConfigurationManagerService;
import io.camunda.zeebe.dynamic.config.changes.PartitionChangeExecutor;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.file.Path;
import java.time.Duration;

public class DynamicClusterTopologyService implements ClusterTopologyService {

  private PartitionDistribution partitionDistribution;

  private ClusterConfigurationManagerService clusterConfigurationManagerService;

  @Override
  public PartitionDistribution getPartitionDistribution() {
    return partitionDistribution;
  }

  @Override
  public void registerPartitionChangeExecutor(final PartitionChangeExecutor executor) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.registerPartitionChangeExecutor(executor);
    } else {
      throw new IllegalStateException(
          "Cannot register partition change executor before the topology manager is started");
    }
  }

  @Override
  public void removePartitionChangeExecutor() {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removePartitionChangeExecutor();
    }
  }

  @Override
  public ActorFuture<Void> start(final BrokerStartupContext brokerStartupContext) {
    final CompletableActorFuture<Void> started = new CompletableActorFuture<>();

    clusterConfigurationManagerService = getClusterTopologyManagerService(brokerStartupContext);

    final var topologyManagerStartedFuture =
        startClusterTopologyManager(brokerStartupContext, clusterConfigurationManagerService);

    topologyManagerStartedFuture.onComplete(
        (ignore, topologyManagerFailed) -> {
          if (topologyManagerFailed != null) {
            started.completeExceptionally(topologyManagerFailed);
          } else {
            clusterConfigurationManagerService.addUpdateListener(
                brokerStartupContext.getBrokerClient().getTopologyManager());
            clusterConfigurationManagerService
                .getClusterTopology()
                .onComplete(
                    (topology, error) -> {
                      if (error != null) {
                        started.completeExceptionally(error);
                      } else {
                        try {
                          partitionDistribution =
                              new PartitionDistribution(
                                  ConfigurationUtil.getPartitionDistributionFrom(
                                      topology, PartitionManagerImpl.GROUP_NAME));
                          started.complete(null);
                        } catch (final Exception topologyConversionFailed) {
                          started.completeExceptionally(topologyConversionFailed);
                        }
                      }
                    });
          }
        });
    return started;
  }

  @Override
  public void registerTopologyChangeListener(final InconsistentConfigurationListener listener) {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.registerTopologyChangedListener(listener);
    } else {
      throw new IllegalStateException(
          "Cannot register topology change listener before the topology manager is started");
    }
  }

  @Override
  public void removeTopologyChangeListener() {
    if (clusterConfigurationManagerService != null) {
      clusterConfigurationManagerService.removeTopologyChangedListener();
    }
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    partitionDistribution = null;
    if (clusterConfigurationManagerService != null) {
      return clusterConfigurationManagerService.closeAsync();
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  private static ActorFuture<Void> startClusterTopologyManager(
      final BrokerStartupContext brokerStartupContext,
      final ClusterConfigurationManagerService clusterConfigurationManagerService) {
    final BrokerCfg brokerConfiguration = brokerStartupContext.getBrokerConfiguration();
    final var localMember =
        brokerStartupContext.getClusterServices().getMembershipService().getLocalMember().id();

    final var staticConfiguration =
        StaticConfigurationGenerator.getStaticConfiguration(brokerConfiguration, localMember);

    return clusterConfigurationManagerService.start(
        brokerStartupContext.getActorSchedulingService(), staticConfiguration);
  }

  private ClusterConfigurationManagerService getClusterTopologyManagerService(
      final BrokerStartupContext brokerStartupContext) {
    final var rootDirectory =
        Path.of(brokerStartupContext.getBrokerConfiguration().getData().getDirectory());
    return new ClusterConfigurationManagerService(
        rootDirectory,
        brokerStartupContext.getClusterServices().getCommunicationService(),
        brokerStartupContext.getClusterServices().getMembershipService(),
        getDefaultClusterTopologyGossipConfig() // TODO: allow user specified config
        );
  }

  private ClusterConfigurationGossiperConfig getDefaultClusterTopologyGossipConfig() {
    return new ClusterConfigurationGossiperConfig(
        true, Duration.ofSeconds(10), Duration.ofSeconds(2), 2);
  }
}
