pipeline {
    agent {
        label "master"
    }

    environment {
        USER_ID = ''
    }

    stages {
        stage ('準備') {
            steps {
                script {
                    wrap([$class: 'BuildUser']) {
                        USER_ID = env.BUILD_USER_ID
                    }
                }
            }
        }
        stage ("parallel") {
            parallel {
                stage ('ios') {
                    steps {
                        script {
                            build job: 'Release_Build_iOS',
                            parameters: [
                            listGitBranches(name: 'BRANCH', value: params.BRANCH),
                            text(name: 'RELEASENOTE', value: params.RELEASENOTE),
                            extendedChoice(name: 'UNITY_VERSION', value: params.UNITY_VERSION),
                            booleanParam(name: 'FORCE_TEST_LOGIN', value: params.FORCE_TEST_LOGIN),
                            booleanParam(name: 'CLEAR_CACHE', value: params.CLEAR_CACHE)]
                        }
                    }
                }
                stage ('android') {
                    steps {
                        script {
                            build job: 'Release_Build_Android',
                            parameters: [listGitBranches(name: 'BRANCH', value: params.BRANCH),
                            text(name: 'RELEASENOTE', value: params.RELEASENOTE),
                            extendedChoice(name: 'UNITY_VERSION', value: params.UNITY_VERSION),
                            booleanParam(name: 'CLEAR_CACHE', value: params.CLEAR_CACHE),
                            booleanParam(name: 'FORCE_TEST_LOGIN', value: params.FORCE_TEST_LOGIN)]
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                def message = """${NOTIFY_EMOJI} :skull:*ビルド失敗 [$JOB_NAME:$BUILD_ID]*:skull:
                \n${BUILD_URL}
                \nユーザー : $USERNAME @${USER_ID}
                \nbranch : $BRANCH_NAME
                \nRELEASE_NOTE : ${params.RELEASENOTE}
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
                \n${BUILD_URL}
                \nユーザー : $USERNAME @${USER_ID}
                \nbranch : $BRANCH_NAME
                \nRELEASE_NOTE : ${params.RELEASENOTE}
                """
                slackSend channel:env.SLACK_NOTIFY_CHANNEL,
                    teamDomain: env.SLACK_DOMAIN,
                    color: "warning",
                    message: message
            }
        }
    }
}
