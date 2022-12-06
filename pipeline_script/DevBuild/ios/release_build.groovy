pipeline {
    agent {
        label "master"
    }

    environment {
        // groovy Files
        gitUtility = ''
        appcenterUtility = ''
        slackUtility = ''
        slackNotify = ''
        NOTIFY_EMOJI = ':apple3:'

        // appcenter
        RELEASE_ID = ''
        APP_NAME = ''

        // git
        GIT_URL='https://github.com/bodacheng/MComat.git'
        GIT_CREDENTIAL='bodacheng1'
        GIT_HASH = ''

        // branch name
        BRANCH_NAME = ''

        // environment values
        UNITY_PATH="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"
        UNITY_METHOD='Cocone.ProjectP3.Client.Build'
        ADDRESSABLE_METHOD='Cocone.ProjectP3.BuildAddressableAssets.BatchBuild'

        // build configuration
        BUILD_TARGET='iOS'
        PRODUCT_NAME=''
        VERSION=""
        OUTPUT_PATH='build_ios'
        BUILD_KIND='Release'
        IPA_FILENAME="ipa_file_name"
        IPA_EXECUTABLE_NAME="ipa_executable_name"
        BUILD_CONFIG_DIR='Assets/App/Editor/Build/Configs'
        EXPORT_PLIST_DIR='${BUILD_CONFIG_DIR}/iOS'
        YamlFile = "${BUILD_CONFIG_DIR}/${BUILD_KIND}BuildSettings.yaml"
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
                    gitUtility = load "pipeline_script/utils/gitUtility.groovy"
                    appcenterUtility = load "pipeline_script/utils/appcenterUtility.groovy"

                    def slackNotifyClass = load "pipeline_script/utils/notify/SlackNotify.groovy"
                    slackNotify = slackNotifyClass.newInstance(env.SLACK_NOTIFY_CHANNEL, "p3-notify-slack-token", BUILD_KIND, BUILD_TARGET, "")
                    slackUtility = load "pipeline_script/utils/notify/slackUtility.groovy"
                    println '-------- 本环节成功？'
                }
            }
        }
        stage('Git') {
            steps {
                script{
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    USERNAME = cause.userName

//                     wrap([$class: 'BuildUser']) {
//                         // slack通知
//                         def releaseNote = ":play: *ビルド開始します。* @p3_client \n[Job:$JOB_NAME/BuildNo:$BUILD_ID/URL:${env.BUILD_URL}]\n${params.RELEASENOTE}\n"
//                         slackNotify.SetBuildUser(USERNAME.toString() + "/@" + env.BUILD_USER_ID)
//                         slackNotify.SetGitInfomation(params.BRANCH, "unknown")
//                         slackNotify.SetReleaseNotes(releaseNote)
//                         slackUtility.notifyStartSlackSendMessage(slackNotify)
//                     }

                    BRANCH_NAME = gitUtility.get_branch_name(params.BRANCH)
                    checkout([$class: 'GitSCM',
                        branches: [[name: "$BRANCH_NAME"]],
                        extensions: [
                            //[$class: 'GitLFSPull'],
                            //[$class: 'CloneOption', timeout: 60],
                            //[$class: 'CheckoutOption', timeout: 60]
                        ],
                        gitTool: 'Default',
                        userRemoteConfigs: [[credentialsId: "$GIT_CREDENTIAL", url: "$GIT_URL"]]
                    ])
                    
                    println 'Checked out to' + BRANCH_NAME

                    // Git情報の取得
                    //GIT_LOG = gitUtility.getGitCommitLatestLog()
                    //GIT_HASH = gitUtility.getGitRevision()
                }

            }
        }
        stage('yaml取得/初期化') {
            steps {
                script {
                    def script = $/eval "cat ${YamlFile} | grep -o 'productName: .*$' | sed -e 's/productName: ''//'"/$
                    PRODUCT_NAME = sh(script:"${script}", returnStdout:true)
                    PRODUCT_NAME = PRODUCT_NAME.replaceAll("\n", "")
                    println '-------- PRODUCT_NAME:' + PRODUCT_NAME

                    script = $/eval "cat ${YamlFile} | grep -o 'cfBundleName: .*$' | sed -e 's/cfBundleName: ''//'"/$
                    IPA_FILENAME = sh(script:"${script}", returnStdout:true)
                    IPA_FILENAME = IPA_FILENAME.replaceAll("\n", "")
                    println '-------- IPA_FILENAME:' + IPA_FILENAME

                    APP_OUTPUT_PATH = "$OUTPUT_PATH/Export/${IPA_FILENAME}.ipa"
                    println '-------- APP_OUTPUT_PATH:' + APP_OUTPUT_PATH

                    script = $/eval "cat ${YamlFile} | grep -o 'cfBundleExecutableName: .*$' | sed -e 's/cfBundleExecutableName: ''//'"/$
                    IPA_EXECUTABLE_NAME = sh(script:"${script}", returnStdout:true)
                    IPA_EXECUTABLE_NAME = IPA_EXECUTABLE_NAME.replaceAll("\n", "")
                    println '--------IPA_EXECUTABLE_NAME:' + IPA_EXECUTABLE_NAME

                    // キャッシュ削除が必要な場合Libraryフォルダーを削除
                    if (params.CLEAR_CACHE)
                    {
                        dir(env.LIBRARY_PATH) {
                            deleteDir()
                        }
                    }

                    // ビルド前に出力ディレクトリの削除
                    dir(env.OUTPUT_PATH) {
                        deleteDir()
                    }

                    // 現在のジョブについての説明
                    //currentBuild.description = "ブランチ：${BRANCH_NAME}\nGITLOG：${GIT_LOG}"
                }
            }
        }
        stage('Assets') {
            steps {
               script {
                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "$UNITY_PATH"
                    commandBuilder.append " -projectPath $WORKSPACE"
                    commandBuilder.append " -quit -batchmode"
                    commandBuilder.append " -executeMethod $ADDRESSABLE_METHOD"
                    commandBuilder.append " -logFile ${WORKSPACE}/Logs/assetbuild_${BUILD_ID}_log.txt"
                    commandBuilder.append " -buildTarget $BUILD_TARGET"
                    commandBuilder.append " -assetProfile P3$BUILD_KIND"

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('Unity') {
            options {
                timeout(time: 90, unit: 'MINUTES')
            }

            steps {
                script {
                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "$UNITY_PATH"
                    commandBuilder.append " -projectPath $WORKSPACE"
                    commandBuilder.append " -quit -batchmode"
                    commandBuilder.append " -executeMethod $UNITY_METHOD"
                    commandBuilder.append " -logFile ${WORKSPACE}/Logs/build_${BUILD_ID}_log.txt"
                    commandBuilder.append " -buildTarget $BUILD_TARGET"
                    commandBuilder.append " -BuildNumber $BUILD_ID"
                    commandBuilder.append " -OutputPath $OUTPUT_PATH"
                    commandBuilder.append " -buildKind $BUILD_KIND"

                    // 強制的にTestLoginSceneを表示する
//                     if (params.FORCE_TEST_LOGIN)
//                     {
//                         commandBuilder.append " -forceTestLogin"
//                     }

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('unlock keychain') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'PCUSER_PASSWORD', variable: 'PC_PASSWORD')]) {
                        sh """
                        security unlock-keychain -p ${PC_PASSWORD} /Users/p3_dev/Library/Keychains/login.keychain
                        """
                    }
                }
            }
        }
        stage('Xcode') {
            steps {
                sh """
                xcodebuild -project "$OUTPUT_PATH"/Unity-iPhone.xcodeproj \
                -configuration Release \
                clean archive -archivePath "$OUTPUT_PATH"/Archive \
                -scheme Unity-iPhone
                """
            }
        }
        stage('Export') {
            steps {
                sh """
                xcodebuild -project "$OUTPUT_PATH"/Unity-iPhone.xcodeproj \
                -exportArchive -archivePath "$OUTPUT_PATH"/archive.xcarchive \
                -exportPath "$OUTPUT_PATH"/Export \
                -exportOptionsPlist "$EXPORT_PLIST_DIR"/ExportOptions_"$BUILD_KIND".plist
                """
            }
        }
        stage('ipa保存/version情報取得') {
            steps {
                // ipaファイルの保存
                archiveArtifacts artifacts: "$APP_OUTPUT_PATH",
                fingerprint: true,
                followSymlinks: false

                script {
                    def ver_script = $/eval "unzip -p "${APP_OUTPUT_PATH}" Payload/"${IPA_EXECUTABLE_NAME}".app/Info.plist | plutil -convert json -o - -- - | jq -r .CFBundleShortVersionString"/$
                    //println ver_script
                    //VERSION = sh(script:"${ver_script}", returnStdout: true)
                    //echo "version=${VERSION}"
                }
            }
        }
        stage('AppCenterのアップロード') {
            steps {
                script {
                    def releaseNote = """
                    ${params.RELEASENOTE}
                    \n___
                    \nGITコミット最新情報\n
                    \n    branch  : ${BRANCH}
                    \n    hash    : ${GIT_HASH}
                    """

                    wrap([$class: 'BuildUser']) {
                        APP_NAME = appcenterUtility.getAppCenterAppName("ios", BUILD_KIND)
                        BUILDER = env.BUILD_USER_ID
                        // TODO:Upsteram jobは後で変更必要
                        println 'appcenterへのアップロード'
                        build job: 'Upload_AppCenter',
                        parameters: [
                        string(name: 'APPCENTER_API_TOKEN', value: params.APPCENTER_API_TOKEN),
                        string(name: 'APP_NAME', value: APP_NAME),
                        string(name: 'OUTPUT_DIR', value: "$OUTPUT_PATH/Export"),
                        string(name: 'copyArtifacts_ProjectName', value: 'Release_Build_iOS'),
                        string(name: 'target_filter_artifact', value: ''),
                        string(name: 'upstream_build_number', value: env.BUILD_NUMBER),
                        string(name: 'upstream_build_user', value: BUILDER),
                        string(name: 'APP_FILENAME', value: "${IPA_FILENAME}.ipa"),
                        string(name: 'DISTRIBUTION_GROUPS', value: appcenterUtility.getAppCenterDistributionGroups()),
                        text(name: 'RELEASENOTE', value: releaseNote)]
                    }

                    //RELEASE_ID = appcenterUtility.getReleaseId(env.APPCENTER_OWNER, APP_NAME, APPCENTER_API_TOKEN)
                    //println "appcenter ReleaseID:${RELEASE_ID}"
                }
            }
        }
        stage('upload apple store') {
            steps {
                script {
                    withCredentials([
                    string(credentialsId: 'AppleStore_API_Key', variable: 'API_KEY'),
                    string(credentialsId: 'AppleStore_API_Issuer', variable: 'API_ISSUE')
                    ]) {
                        sh """
                        xcrun altool --validate-app -f $OUTPUT_PATH/Export/"${IPA_FILENAME}".ipa -t ios --apiKey ${API_KEY} --apiIssuer ${API_ISSUE} --verbose
                        xcrun altool --upload-app -f $OUTPUT_PATH/Export/"${IPA_FILENAME}".ipa -t ios --apiKey ${API_KEY} --apiIssuer ${API_ISSUE} --verbose
                        """
                    }
                }
            }
        }
    }
    post {
        success {
            script {
//                 def preFixReleaseNote = ":kirby::tada:*ビルド成功 [Job:$JOB_NAME/BuildNo:$BUILD_ID]*:tada::kirby:\n${env.BUILD_URL}"
//                 def releaseNote = "${preFixReleaseNote}\n--\n${params.RELEASENOTE}\n--\n${GIT_LOG}"
// 
//                 def downloadURL = appcenterUtility.getDownloadURL(env.APPCENTER_OWNER, APP_NAME, RELEASE_ID)
//                 println "downloadURL:${downloadURL}"
//                 slackNotify.SetAppCenterInfomation(RELEASE_ID, downloadURL, VERSION)
//                 slackNotify.SetGitInfomation(BRANCH_NAME, GIT_HASH)
//                 slackNotify.SetReleaseNotes(releaseNote)
//                 slackNotify.SetBuildTime(currentBuild.durationString)
//                 slackUtility.notifySlackSendMessage(slackNotify)

                    println "success?"
            }
        }
        failure {
            script {
//                 def message = """${NOTIFY_EMOJI} :skull:*ビルド失敗 [$JOB_NAME:$BUILD_ID]*:skull::alert:
//                 \n${BUILD_URL}
//                 \nユーザー : $USERNAME @${BUILDER}
//                 \nbranch : $BRANCH_NAME
//                 \nRELEASE_NOTE : ${params.RELEASENOTE}
//                 """
//                 slackSend channel:env.SLACK_NOTIFY_CHANNEL,
//                     teamDomain: env.SLACK_DOMAIN,
//                     color: "danger",
//                     message: message

                println "fail?"
            }
        }
        aborted {
            script {
//                 def message = """${NOTIFY_EMOJI} :construction:*ビルド中断 [$JOB_NAME:$BUILD_ID]*:construction:
//                 \n${BUILD_URL}
//                 \nユーザー : $USERNAME @${BUILDER}
//                 \nbranch : $BRANCH_NAME
//                 \nRELEASE_NOTE : ${params.RELEASENOTE}
//                 """
//                 slackSend channel:env.SLACK_NOTIFY_CHANNEL,
//                     teamDomain: env.SLACK_DOMAIN,
//                     color: "warning",
//                     message: message
                println "aborted?"
            }
        }
        always {
            // ログ保存
            archiveArtifacts allowEmptyArchive: true,
            artifacts: "Logs/build_${BUILD_ID}_log.txt, Logs/assetbuild_${BUILD_ID}_log.txt",
            fingerprint: true,
            followSymlinks: false
        }
    }
}
