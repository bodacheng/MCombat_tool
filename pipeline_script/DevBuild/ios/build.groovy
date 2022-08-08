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
        versionInfomationUtility = ''
        buildUtility = ''

        GIT_LOG = ''
        GIT_HASH = ''

        // branch name
        BRANCH_NAME = ''

        // appcenter
        RELEASE_ID = ''
        APP_NAME = ''

        // git
        GIT_URL='https://github.com/bodacheng/MComat.git'
        GIT_CREDENTIAL='bodacheng1'

        // environment values
        UNITY_PATH="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"
        UNITY_METHOD='Cocone.ProjectP3.Client.Build'
        ADDRESSABLE_METHOD='Cocone.ProjectP3.BuildAddressableAssets.BatchBuild'
        BUILDER = ''

        // build configuration
        BUILD_TARGET='iOS'
        PRODUCT_NAME=''
        VERSION=""
        OUTPUT_PATH='build_ios'
        IPA_FILENAME=''
        IPA_EXECUTABLE_NAME=''
        BUILD_CONFIG_DIR='Assets/App/Editor/Build/Configs'
        EXPORT_PLIST_DIR='${BUILD_CONFIG_DIR}/iOS'
        APP_OUTPUT_PATH=''

        // s3 upload variables
        UPLOAD_S3_ADDRESS=''
        ASSET_BUILDPATH=''
        SERVER_PROFILE_NAME=''
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
                    def slackNotifyClass = load "${utilisPath}/notify/SlackNotify.groovy"
                    slackNotify = slackNotifyClass.newInstance(env.SLACK_NOTIFY_CHANNEL, "p3-notify-slack-token", params.BUILD_KIND, BUILD_TARGET, "")
                    slackUtility = load "${utilisPath}/notify/slackUtility.groovy"
                    versionInfomationUtility = load "${utilisPath}/getVersionInfomationUtility.groovy"
                }
            }
        }
        stage('Git') {
            steps {
                script {
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    USERNAME = cause.userName
                    BRANCH_NAME = gitUtility.get_branch_name(params.BRANCH)

                    checkout([$class: 'GitSCM',
                        branches: [[name: BRANCH_NAME]],
                        extensions: [
//                             [$class: 'GitLFSPull'],
//                             [$class: 'CloneOption', timeout: 60],
//                             [$class: 'CheckoutOption', timeout: 60]
                        ],
                        gitTool: 'Default',
                        userRemoteConfigs: [[credentialsId: "$GIT_CREDENTIAL", url: "$GIT_URL"]]
                    ])

                    // Git情報の取得
//                     GIT_LOG = gitUtility.getGitCommitLatestLog()
//                     GIT_HASH = gitUtility.getGitRevision()
                }
            }
        }
        stage('yaml取得/初期化') {
            steps {
                script {
                    def yamlFile = "${BUILD_CONFIG_DIR}/${BUILD_KIND}BuildSettings.yaml"
                    def script = $/eval "cat ${yamlFile} | grep -o 'productName: .*$' | sed -e 's/productName: ''//'"/$
                    PRODUCT_NAME = sh(script:"${script}", returnStdout:true)
                    PRODUCT_NAME = PRODUCT_NAME.replaceAll("\n", "")
                    println '-------- PRODUCT_NAME:' + PRODUCT_NAME

                    script = $/eval "cat ${yamlFile} | grep -o 'cfBundleName: .*$' | sed -e 's/cfBundleName: ''//'"/$
                    IPA_FILENAME = sh(script:"${script}", returnStdout:true)
                    IPA_FILENAME = IPA_FILENAME.replaceAll("\n", "")
                    println '-------- IPA_FILENAME:' + IPA_FILENAME

                    APP_OUTPUT_PATH = "$OUTPUT_PATH/Export/Apps/${IPA_FILENAME}.ipa"
                    println '-------- APP_OUTPUT_PATH:' + APP_OUTPUT_PATH

                    script = $/eval "cat ${yamlFile} | grep -o 'cfBundleExecutableName: .*$' | sed -e 's/cfBundleExecutableName: ''//'"/$
                    IPA_EXECUTABLE_NAME = sh(script:"${script}", returnStdout:true)
                    IPA_EXECUTABLE_NAME = IPA_EXECUTABLE_NAME.replaceAll("\n", "")
                    println '-------- IPA_EXECUTABLE_NAME:' + IPA_EXECUTABLE_NAME

                    yamlFile = "${BUILD_CONFIG_DIR}/AddressablesProfileSettings.yaml"
                    script = $/eval "cat ${yamlFile} | grep -o 'Profile${AssetKind}: .*$' | sed -e 's/Profile${params.AssetKind}: ''//'"/$
                    ASSET_PROFILE = sh(script:"${script}", returnStdout:true)
                    ASSET_PROFILE = ASSET_PROFILE.replaceAll("\n", "")
                    println '-------- ASSET_PROFILE:' + ASSET_PROFILE

                    // キャッシュ削除が必要な場合Libraryフォルダーを削除
                    if (params.CLEAR_CACHE)
                    {
                        dir(env.LIBRARY_PATH) {
                            deleteDir()
                        }
                    }

                    println "TIMEOUT_VALUES:${env.TIMEOUT_UNITY_STAGE}"

                    // ビルド前に出力ディレクトリの削除
                    dir(env.OUTPUT_PATH) {
                        deleteDir()
                    }

                    // 現在のジョブについての説明
                    currentBuild.description = "ビルド種別：${params.BUILD_KIND}\nアセット種別：${assetKind}\nブランチ：${BRANCH_NAME}\nGITLOG：${GIT_LOG}"
                }
            }
        }
        stage('Assets') {
            options {
                // Mac Studio(M1 Max)はこのタイムアウト設定でいく想定
                timeout(time: 60, unit: 'MINUTES')
            }
            steps {
                script {
                    StringBuilder commandBuilder = new StringBuilder()
                    commandBuilder.append "$UNITY_PATH"
                    commandBuilder.append " -projectPath $WORKSPACE"
                    commandBuilder.append " -quit -batchmode"
                    commandBuilder.append " -executeMethod $ADDRESSABLE_METHOD"
                    commandBuilder.append " -logFile ${WORKSPACE}/Logs/assetbuild_${BUILD_ID}_log.txt"
                    commandBuilder.append " -buildTarget $BUILD_TARGET"
                    commandBuilder.append " -assetProfile $ASSET_PROFILE"

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('Unity') {
            options {
                // Mac Studio(M1 Max)はこのタイムアウト設定でいく想定
                timeout(time: 30, unit: 'MINUTES')
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
                    commandBuilder.append " -buildKind ${params.BUILD_KIND}"

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
                -exportOptionsPlist "$EXPORT_PLIST_DIR"/ExportOptions_"${params.BUILD_KIND}".plist
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
                    def archiveDir = APP_OUTPUT_PATH.replace("/${IPA_EXECUTABLE_NAME}.ipa", "")
                    versionName = versionInfomationUtility.getVersionName('ios', archiveDir, IPA_EXECUTABLE_NAME)
                    VERSION = versionName.replace("\n", '')
                    println 'ipa versionName:' + VERSION
                }
            }
        }
        stage('AppCenterのアップロード') {
            steps {
                script {
                    wrap([$class: 'BuildUser']) {
                        APP_NAME = appcenterUtility.getAppCenterAppName("ios", params.BUILD_KIND)
                        BUILDER = env.BUILD_USER_ID
                        // TODO:Upsteram jobは後で変更必要
                        println 'appcenterへのアップロード'
                        build job: 'Upload_AppCenter',
                        parameters: [
                        string(name: 'APPCENTER_API_TOKEN', value: params.APPCENTER_API_TOKEN),
                        string(name: 'APP_NAME', value: APP_NAME),
                        string(name: 'OUTPUT_DIR', value: "$OUTPUT_PATH/Export/Apps"),
                        string(name: 'copyArtifacts_ProjectName', value: 'DevBuild_iOS_Single'),
                        string(name: 'target_filter_artifact', value: ''),
                        string(name: 'upstream_build_number', value: env.BUILD_NUMBER),
                        string(name: 'upstream_build_user', value: BUILDER),
                        string(name: 'APP_FILENAME', value: "${IPA_FILENAME}.ipa"),
                        string(name: 'DISTRIBUTION_GROUPS', value: appcenterUtility.getAppCenterDistributionGroups()),
                        text(name: 'RELEASENOTE', value: params.RELEASENOTE)]
                    }

                    RELEASE_ID = appcenterUtility.getReleaseId(env.APPCENTER_OWNER, APP_NAME, params.APPCENTER_API_TOKEN)
                    println "appcenter ReleaseID:${RELEASE_ID}"
                }
            }
        }
    }
    post {
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
            // ログ保存
            archiveArtifacts allowEmptyArchive: true,
            artifacts: "Logs/build_${BUILD_ID}_log.txt, Logs/assetbuild_${BUILD_ID}_log.txt",
            fingerprint: true,
            followSymlinks: false
        }
    }
}
