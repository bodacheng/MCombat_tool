pipeline {
    agent {
        label "TestBuild_JobNode"
    }

    environment {
        // branch name
        BRANCH_NAME = ''

        // git
        GIT_URL='https://git-1.cocone.jp/projectp3/p3-client'
        GIT_P3_URL='https://git-1.cocone.jp/projectp3/p3-assets'
        GIT_CREDENTIAL='p3_jenkins_gitlab'
    }

    stages {
        stage ('ワークスペースのクリーン') {
            steps {
                cleanWs()
            }
        }
        stage('Git') {
            steps {
                script {
                    checkout([$class: 'GitSCM',
                        branches: [[name: 'master']],
                        extensions: [
                            [$class: 'GitLFSPull'],
                            [$class: 'CloneOption', timeout: 180],
                            [$class: 'CheckoutOption', timeout: 180]
                        ],
                        gitTool: 'Default',
                        userRemoteConfigs: [[credentialsId: "$GIT_CREDENTIAL", url: "$GIT_URL"]]
                    ])

                    // ビルド時間を表示
                    currentBuild.description = currentBuild.durationString
                }
            }
        }
    }
    /*post {
        success {
            // NOTE:GITのLogを付加したいので子ジョブで成功通知を出す
            script {
                def preFixReleaseNote = ":kirby::tada:*ビルド成功 [Job:$JOB_NAME/BuildNo:$BUILD_ID]*:tada::kirby:\n${env.BUILD_URL}"
                def releaseNote = "${preFixReleaseNote}\n--\n${params.RELEASENOTE}\n--\n${GIT_LOG}"

                def downloadURL = appcenterUtility.getDownloadURL(env.APPCENTER_OWNER, APP_NAME, RELEASE_ID)
                println "downloadURL:${downloadURL}"
                slackNotify.SetAppCenterInfomation(RELEASE_ID, downloadURL, VERSION)
                slackNotify.SetBuildUser(USERNAME.toString() + "/@" + BUILDER)
                slackNotify.SetGitInfomation(BRANCH_NAME, GIT_HASH)
                slackNotify.SetReleaseNotes(releaseNote)
                slackNotify.SetBuildTime(currentBuild.durationString)
                slackNotify.SetAssetKind(AssetKind)
                slackUtility.notifySlackSendMessage(slackNotify)

                println "ビルド所要時間${currentBuild.durationString}"
            }
        }
        always {
        }
    }*/
}
