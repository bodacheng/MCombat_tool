pipeline {
    agent {
        label "DevBuild_JobNode"
    }

    environment {
        BRANCH_NAME = ''
        APPCENTER_APPNAME = ''
        USERNAME = ''
        USER_ID = ''
        NOTIFY_EMOJI = ':apple3:'

        // groovy Files
        gitUtility = ''
        appcenterUtility = ''
        buildUtility = ''
    }

    stages {
        stage ('ワークスペースのクリーン') {
            steps {
                script {
                    if (params.needCleanWorkspace) {
                        cleanWs()
                    }
                }
            }
        }
        stage ('groovy準備') {
            steps {
                script {
                    // Git checkout before load source the file
                    checkout scm

                    // To know files are checked out or not
                    sh '''
                        ls -lhrt
                    '''

                    // load git utility
                    def utilisPath = "pipeline_script/utils"
                    gitUtility = load "${utilisPath}/gitUtility.groovy"
                    appcenterUtility = load "${utilisPath}/appcenterUtility.groovy"
                    buildUtility = load "${utilisPath}/buildUtility.groovy"
                }
            }
        }
        stage('情報の取得') {
            steps {
                script {
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')

                    USERNAME = cause.userName
                    APPCENTER_APPNAME = appcenterUtility.getAppCenterAppName("ios", params.BUILD_KIND)
                    BRANCH_NAME = gitUtility.get_branch_name(params.BRANCH)

                    wrap([$class: 'BuildUser']) {
                        USER_ID = env.BUILD_USER_ID
                    }
                }
            }
        }
        stage('customビルド') {
            steps {
                script {
                    def assetKind = params.AssetKind
                    if (params.BUILD_KIND != 'Dev') {
                        // Dev以外の場合は、アセット種別を割り出す
                        assetKind = buildUtility.getAssetKind(params.BUILD_KIND)
                    }

                    // 現在のジョブについての説明
                    currentBuild.description = "ビルド種別：${params.BUILD_KIND}\nアセット種別：${assetKind}\nブランチ：${BRANCH_NAME}"

                    build job: 'DevBuild_iOS_Single',
                    parameters: [
                    listGitBranches(name: 'BRANCH', value: params.BRANCH),
                    text(name: 'RELEASENOTE', value: params.RELEASENOTE),
                    extendedChoice(name: 'UNITY_VERSION', value: params.UNITY_VERSION),
                    extendedChoice(name: 'BUILD_KIND', value: params.BUILD_KIND),
                    extendedChoice(name: 'AssetKind', value: assetKind),
                    booleanParam(name: 'needCleanWorkspace', value: params.needCleanWorkspace),
                    booleanParam(name: 'CLEAR_CACHE', value: params.CLEAR_CACHE),
                    string(name: 'APPCENTER_API_TOKEN', value: appcenterUtility.getAppCenterToken('ios', params.BUILD_KIND)),
                    text(name: 'RELEASENOTE', value: params.RELEASENOTE)]
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
                \n$BUILD_URL
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
