pipeline {
    agent {
        label "PeriodBuild_JobNode"
    }

    environment {
        NOTIFY_EMOJI = ':apple3:'
    }

    stages {
        stage('customビルド') {
            steps {
                script {
                    build job: 'DevBuild_iOS_Custom',
                    parameters: [
                    listGitBranches(name: 'BRANCH', value: 'master'),
                    extendedChoice(name: 'BUILD_KIND', value: 'QA'),
                    extendedChoice(name: 'AssetKind', value: 'QA'),
                    text(name: 'RELEASENOTE', value: '定期ビルド'),
                    extendedChoice(name: 'UNITY_VERSION', value: '2020.3.21f1'),
                    booleanParam(name: 'CLEAR_CACHE', value: false),
                    booleanParam(name: 'needCleanWorkspace', value: false)]
                }
            }
        }
    }

    post {
        failure {
            script {
                def message = """${NOTIFY_EMOJI} :skull:*ビルド失敗 [$JOB_NAME:$BUILD_ID]*:skull:
                \n${BUILD_URL}
                \n定期ビルド
                \nbranch : master
                """
                slackSend channel:env.SLACK_NOTIFY_CHANNEL,
                    teamDomain: env.SLACK_DOMAIN,
                    color: "danger",
                    message: message
            }
        }
        aborted {
            script {
                def message = """${NOTIFY_EMOJI} :construction:*ビルド中断 [$JOB_NAME:$BUILD_ID]*:construction:
                \n$BUILD_URL
                \n定期ビルド
                \nbranch : master
                """
                slackSend channel:env.SLACK_NOTIFY_CHANNEL,
                    teamDomain: env.SLACK_DOMAIN,
                    color: "warning",
                    message: message
            }
        }
    }
}
