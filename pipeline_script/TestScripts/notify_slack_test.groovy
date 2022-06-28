import hudson.model.*
import java.io.File
import jenkins.text.*

pipeline {
    agent any

    environment {
        USERNAME = ''
        USER_ID = ''
        slackUtility = ''
        slackNotify = ''
    }


    stages {
        stage ('groovy準備') {
            steps {
                script {
                    // Git checkout before load source the file
                    checkout scm

                    // To know files are checked out or not
                    sh '''
                        ls -lhrt
                    '''

                    def channels = "#jenkins通知テスト"
                    def credentialsId = "p3-notify-slack-token"
                    def buildKind = "Beta"
                    def platform = "iOS"
                    def releaseNote = "test notify slack @matsumoto_rika"

                    def slackNotifyClass = load "pipeline_script/utils/notify/SlackNotify.groovy"
                    slackNotify = slackNotifyClass.newInstance(channels, credentialsId, buildKind, platform, releaseNote)

                    slackUtility = load "pipeline_script/utils/notify/slackUtility.groovy"
                }
            }
        }
        stage('実験') {
            steps {
                script {
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    USERNAME = cause.userName

                    wrap([$class: 'BuildUser']) {
                        USER_ID = env.BUILD_USER_ID
                    }

                    println "USERBANE:${USERNAME}"
                    println "USER_ID:${USER_ID}"

                    def downloadURL = "https://install.appcenter.ms/orgs/coconecorp/apps/Pokepia-iOS/releases"
                    def appcenterId = "9999"
                    slackNotify.SetAppCenterInfomation(appcenterId, downloadURL)
                    slackNotify.SetBuildUser(USERNAME.toString())
                }
            }
        }
    }

    post {
        success {
            script {
                //def preFixReleaseNote = ":kirby::tada:*ビルド成功 [Job:$JOB_NAME/BuildNo:$BUILD_ID]*:tada::kirby:"
                //releaseNote = "${preFixReleaseNote}\n---------------------\n${releaseNote}"

                slackUtility.notifySlackSendMessage(slackNotify)
            }
        }
        failure {
            script {
                def message = ":apple3: :skull:*Slack通知の研究テスト [$JOB_NAME:$BUILD_ID]*:skull:\n"
                + "$BUILD_URL"
                + "\nユーザー : $USERNAME @${USER_ID}"
                + "\nbranch : $BRANCH_NAME"
                + "\nRELEASE_NOTE : ${params.RELEASENOTE}"
                slackSend channel:"@matsumoto_rika",
                    teamDomain: env.SLACK_DOMAIN,
                    color: "danger",
                    message: message
            }
        }
        aborted {
            script {
                def message = ":apple3: :construction:*Slack通知の研究テスト [$JOB_NAME:$BUILD_ID]*:construction:\n"
                + "$BUILD_URL"
                + "\nユーザー : $USERNAME @${USER_ID}"
                + "\nbranch : $BRANCH_NAME"
                + "\nRELEASE_NOTE : ${params.RELEASENOTE}"
                slackSend channel:"@matsumoto_rika",
                    teamDomain: env.SLACK_DOMAIN,
                    color: "warning",
                    message: message
            }
        }
    }
}
