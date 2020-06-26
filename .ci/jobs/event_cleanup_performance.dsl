pipelineJob('event-cleanup-performance') {

  displayName 'Optimize Event Based Process History Cleanup performance'
  description 'Test Optimize Event Based Process Import performance.'

  definition {
    cps {
      script(readFileFromWorkspace('.ci/pipelines/event_cleanup_performance.groovy'))
      sandbox()
    }
  }

  parameters {
    stringParam('BRANCH', 'master', 'Branch to use for performance tests.')

    choiceParam('SQL_DUMP', ['optimize_data-large.sqlc', 'optimize_data-medium.sqlc', 'optimize_data-stage.sqlc'])
    stringParam('ES_REFRESH_INTERVAL', '5s', 'Elasticsearch index refresh interval.')
    stringParam('ES_NUM_NODES', '1', 'Number of Elasticsearch nodes in the cluster (not more than 5)')
    stringParam('EXTERNAL_EVENT_COUNT', '40000000', 'Number of external events to ingest.')
    stringParam('CLEANUP_TIMEOUT_MINUTES', '120', 'Time limit for a cleanup run to finish')
  }

  properties {
    pipelineTriggers {
      triggers {
        cron {
          spec('H 20 * * *')
        }
      }
    }
  }
}
