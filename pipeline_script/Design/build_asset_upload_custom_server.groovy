pipeline {
    agent {
        label "Design_JobNode"
    }

    environment {
        // BRANCHとRELEASENOTEはジョブのパラメーターつきビルドで設定される
        GIT_URL='https://git-1.cocone.jp/projectp3/p3-client'
        GIT_CREDENTIAL='p3_jenkins_gitlab'

        // environment
        UNITY_PATH="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"
        UNITY_METHOD='Cocone.ProjectP3.BuildAddressableAssets.BatchBuild'

        // path
        BUILD_CONFIG_DIR='Assets/App/Editor/Build/Configs'

        // variables
        ASSET_PROFILE=''
        UPLOAD_S3_ADDRESS=''
        ASSET_BUILDPATH=''
        CACHE_CATALOG='--cache-control "max-age=10"'
        CACHE_ASSET='--cache-control "max-age=86400"'
        FILTER_CATALOG='--exclude "*" --include "catalog_*"'
        FILTER_ASSET='--exclude "catalog_*"'
        SERVER_PROFILE_NAME=''

        BUILDER = ''
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

                    // load git utility
                    def utilisPath = "pipeline_script/utils"
                    gitUtility = load "${utilisPath}/gitUtility.groovy"

                    def slackNotifyClass = load "${utilisPath}/notify/SlackNotify.groovy"

                    slackNotify = slackNotifyClass.newInstance(env.SLACK_NOTIFY_CHANNEL, "p3-notify-slack-token", '', '', '')
                    slackUtility = load "${utilisPath}/notify/slackUtility.groovy"
                }
            }
        }
        stage('Git') {
            options {
                timeout(time: 2, unit: 'HOURS')   // timeout on whole pipeline job
            }

            steps {
                script{
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    USERNAME = cause.userName
                    wrap([$class: 'BuildUser']) {
                        BUILDER = env.BUILD_USER_ID
                    }
                    BRANCH_NAME = gitUtility.get_branch_name(params.BRANCH)

                    // NOTE:通知テストテストする　後で消す
                    def releaseNote = ":play: *アセットビルド開始します。* @p3_client \n[Job:$JOB_NAME/BuildNo:$BUILD_ID/URL:${env.BUILD_URL}]\n"
                    slackNotify.SetBuildUser(USERNAME.toString() + "/@" + BUILDER)
                    slackNotify.SetGitInfomation(params.BRANCH, "unknown")
                    slackNotify.SetReleaseNotes(releaseNote)
                    slackNotify.SetAssetKind(params.AssetKind)

                    // 本番ビルドのときだけ通知する
                    if (params.AssetKind == 'Release') {
                        slackUtility.notifyStartSlackSendMessageAsset(slackNotify)
                    }

                    dir("ServerData")
                    {
                        echo "delete ServerData"
                        deleteDir()
                    }

                    checkout([$class: 'GitSCM',
                        branches: [[name: "$BRANCH_NAME"]],
                        extensions: [
                            [$class: 'GitLFSPull'],
                            [$class: 'CloneOption', timeout: 60],
                            [$class: 'CheckoutOption', timeout: 60]
                        ],
                        gitTool: 'Default',
                        userRemoteConfigs: [[credentialsId: "$GIT_CREDENTIAL", url: "$GIT_URL"]]
                    ])

                    // Git情報の取得
                    GIT_LOG = gitUtility.getGitLogMessage(BRANCH_NAME)
                    GIT_HASH = gitUtility.getGitRevision()
                }
            }
        }
        stage('yaml情報の取得') {
            steps {
                script {
                    def yamlFile = "${BUILD_CONFIG_DIR}/AddressablesProfileSettings.yaml"

                    def script = $/eval "cat ${yamlFile} | grep -o 'Profile${AssetKind}: .*$' | sed -e 's/Profile${AssetKind}: ''//'"/$
                    ASSET_PROFILE = sh(script:"${script}", returnStdout:true)
                    ASSET_PROFILE = ASSET_PROFILE.replaceAll("\n", "")
                    println 'ASSET_PROFILE:' + ASSET_PROFILE

                    script = $/eval "cat ${yamlFile} | grep -o 'Upload${AssetKind}: .*$' | sed -e 's/Upload${AssetKind}: ''//'"/$
                    UPLOAD_S3_ADDRESS = sh(script:"${script}", returnStdout:true)
                    UPLOAD_S3_ADDRESS = UPLOAD_S3_ADDRESS.replaceAll("\n", "")
                    println 'UPLOAD_S3_ADDRESS:' + UPLOAD_S3_ADDRESS

                    script = $/eval "cat ${yamlFile} | grep -o 'Build${AssetKind}: .*$' | sed -e 's/Build${AssetKind}: ''//'"/$
                    ASSET_BUILDPATH = sh(script:"${script}", returnStdout:true)
                    ASSET_BUILDPATH = ASSET_BUILDPATH.replaceAll("\n", "")
                    println 'ASSET_BUILDPATH:' + ASSET_BUILDPATH

                    // プロファイルの名前設定(Release以外はDev)
                    SERVER_PROFILE_NAME = "p3Alpha"

                    // キャッシュ有効化設定
                    if (params.AssetKind == 'Release') {
                        SERVER_PROFILE_NAME = "p3${AssetKind}"
                    }

                    println "upload server : " + params.AssetKind
                    println "cache enabled: " + params.AWS_CACHE

                    currentBuild.description = "サーバー種別：${params.AssetKind}\nS3Address:${UPLOAD_S3_ADDRESS}"

                    // キャッシュ削除が必要な場合Libraryフォルダーを削除
                    if (params.CLEAR_CACHE)
                    {
                        dir(env.LIBRARY_PATH) {
                            deleteDir()
                        }
                    }
                }
            }
        }
        stage('Build iOS Asset') {
            when {
                expression {
                   return params.IOS
                }
            }
            steps {
                script {
                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "$UNITY_PATH"
                    commandBuilder.append " -projectPath $WORKSPACE"
                    commandBuilder.append " -quit -batchmode"
                    commandBuilder.append " -executeMethod $UNITY_METHOD"
                    commandBuilder.append " -logFile ${WORKSPACE}/Logs/build_iOS_${BUILD_ID}_log.txt"
                    commandBuilder.append " -buildTarget iOS"
                    commandBuilder.append " -assetProfile $ASSET_PROFILE"
                    if (params.UseReleaseList == true) {
                        commandBuilder.append " -useReleaseList"
                    }

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('Upload Server iOS Asset') {
            when {
                expression {
                   return params.IOS
                }
            }
            steps {
                script {
                    // upload asset ( with 1day cache if needed )
                    println "upload asset files..."

                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "aws s3 cp --recursive"
                    commandBuilder.append " ${WORKSPACE}/${ASSET_BUILDPATH}iOS/"
                    commandBuilder.append " ${UPLOAD_S3_ADDRESS}/iOS/"
                    commandBuilder.append " --profile ${SERVER_PROFILE_NAME}"
                    commandBuilder.append " ${FILTER_ASSET}"
                    if (params.AWS_CACHE == true) {
                        commandBuilder.append " ${CACHE_ASSET}"
                    }

                    sh(script:commandBuilder.toString(), returnStdout:false)

                    // upload catalog ( with 10 sec cache if needed )
                    println "upload catalog files..."

                    commandBuilder = new StringBuilder()
                    commandBuilder.append "aws s3 cp --recursive"
                    commandBuilder.append " ${WORKSPACE}/${ASSET_BUILDPATH}iOS/"
                    commandBuilder.append " ${UPLOAD_S3_ADDRESS}/iOS/"
                    commandBuilder.append " --profile ${SERVER_PROFILE_NAME}"
                    commandBuilder.append " ${FILTER_CATALOG}"
                    if (params.AWS_CACHE == true) {
                        commandBuilder.append " ${CACHE_CATALOG}"
                    }

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('Build Android Asset') {
            when {
                expression {
                   return params.ANDROID
                }
            }
            steps {
                script {
                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "$UNITY_PATH"
                    commandBuilder.append " -projectPath $WORKSPACE"
                    commandBuilder.append " -quit -batchmode"
                    commandBuilder.append " -executeMethod $UNITY_METHOD"
                    commandBuilder.append " -logFile ${WORKSPACE}/Logs/build_Android_${BUILD_ID}_log.txt"
                    commandBuilder.append " -buildTarget Android"
                    commandBuilder.append " -assetProfile $ASSET_PROFILE"
                    if (params.UseReleaseList == true) {
                        commandBuilder.append " -useReleaseList"
                    }

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('Upload Server Android Asset') {
            when {
                expression {
                   return params.ANDROID
                }
            }
            steps {
                script {
                    // upload asset ( with 1day cache if needed )
                    println "upload asset files..."

                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "aws s3 cp --recursive"
                    commandBuilder.append " ${WORKSPACE}/${ASSET_BUILDPATH}Android/"
                    commandBuilder.append " ${UPLOAD_S3_ADDRESS}/Android/"
                    commandBuilder.append " --profile ${SERVER_PROFILE_NAME}"
                    commandBuilder.append " ${FILTER_ASSET}"
                    if (params.AWS_CACHE == true) {
                        commandBuilder.append " ${CACHE_ASSET}"
                    }

                    sh(script:commandBuilder.toString(), returnStdout:false)

                    // upload catalog ( with 10 sec cache if needed )
                    println "upload catalog files..."

                    commandBuilder = new StringBuilder()
                    commandBuilder.append "aws s3 cp --recursive"
                    commandBuilder.append " ${WORKSPACE}/${ASSET_BUILDPATH}Android/"
                    commandBuilder.append " ${UPLOAD_S3_ADDRESS}/Android/"
                    commandBuilder.append " --profile ${SERVER_PROFILE_NAME}"
                    commandBuilder.append " ${FILTER_CATALOG}"
                    if (params.AWS_CACHE == true) {
                        commandBuilder.append " ${CACHE_CATALOG}"
                    }

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
    }
    post {
        success {
            script {
                def message = ":asset::tada:*ビルド成功 [Job:$JOB_NAME/BuildNo:$BUILD_ID]*:tada::asset:\n${env.BUILD_URL}"
                slackNotify.SetBuildUser(USERNAME.toString() + "/@" + BUILDER)
                slackNotify.SetGitInfomation(BRANCH_NAME, GIT_HASH)
                slackNotify.SetReleaseNotes(message)
                slackNotify.SetBuildTime(currentBuild.durationString)
                slackUtility.notifySlackSendMessageForAsset(slackNotify)
            }
        }
        failure {
            slackSend channel:"${env.SLACK_NOTIFY_CHANNEL}",
                teamDomain: "${env.SLACK_DOMAIN}",
                color: "danger",
                message: ":asset::skull:*アセットビルド失敗 [$JOB_NAME:$BUILD_ID]*:skull:\n$BUILD_URL\nユーザー : $USERNAME @${BUILDER} \nbranch : $BRANCH_NAME"
        }
        aborted {
            slackSend channel:"${env.SLACK_NOTIFY_CHANNEL}",
                teamDomain: "${env.SLACK_DOMAIN}",
                color: "warning",
                message: ":asset::construction:*アセットビルド中断 [$JOB_NAME:$BUILD_ID]*:construction:\n$BUILD_URL\nユーザー : $USERNAME @${BUILDER} \nbranch : $BRANCH_NAME"
        }
        always {
            // ログ保存
            archiveArtifacts allowEmptyArchive: true,
            artifacts: "Logs/build_iOS_${BUILD_ID}_log.txt, Logs/build_Android_${BUILD_ID}_log.txt",
            fingerprint: true,
            followSymlinks: false
        }
    }
}
