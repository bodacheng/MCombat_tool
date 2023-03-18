pipeline {

    agent any

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

        // branch name
        BRANCH_NAME = ''

        // git
        GIT_URL='https://github.com/bodacheng/MComat.git'
        GIT_CREDENTIAL='bodacheng1'
        GIT_HASH = ''

        // environment values
        UNITY_PATH="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"
        ANDROID_PALYAER_PATH="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/PlaybackEngines/AndroidPlayer"
        ANDROID_SDK_PATH="${ANDROID_PALYAER_PATH}/SDK"
        AAPT2_PATH="${ANDROID_SDK_PATH}/build-tools/30.0.2"
        PATH = "${AAPT2_PATH}/:$PATH"
        BUILD_CONFIG_DIR='Assets/App/Editor/Build/Configs'

        // build configuration
        UNITY_METHOD='Cocone.ProjectP3.Client.Build'
        ADDRESSABLE_METHOD='Cocone.ProjectP3.BuildAddressableAssets.BatchBuild'
        BUILD_TARGET='Android'
        PRODUCT_NAME=''
        VERSION=""
        OUTPUT_PATH='build_android'
        BUILD_KIND="Release"
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
                    
                    wrap([$class: 'BuildUser']) {
                        BUILDER = env.BUILD_USER_ID
                    }
                }
            }
        }
        stage('Git') {
            steps {
                script{
                    def cause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    USERNAME = cause.userName

                        wrap([$class: 'BuildUser']) {
                                                    
                            BRANCH_NAME = gitUtility.get_branch_name(params.BRANCH)

                            checkout([$class: 'GitSCM',
                                branches: [[name: "$BRANCH_NAME"]],
                                extensions: [
                                    [$class: 'GitLFSPull'],
                                    [$class: 'CloneOption', timeout: 180],
                                    [$class: 'CheckoutOption', timeout: 180]
                                ],
                                gitTool: 'Default',
                                userRemoteConfigs: [[credentialsId: "$GIT_CREDENTIAL", url: "$GIT_URL"]]
                            ])

                            // Git情報の取得
                            GIT_LOG = gitUtility.getGitCommitLatestLog()
                            GIT_HASH = gitUtility.getGitRevision()
                        }

                    // 現在のジョブについての説明
                    currentBuild.description = "ブランチ：${BRANCH_NAME}\nGITLOG：${GIT_LOG}"
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
                    println 'PRODUCT_NAME:' + PRODUCT_NAME
                    
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
                    commandBuilder.append " -assetProfile release"

                    sh(script:commandBuilder.toString(), returnStdout:false)
                }
            }
        }
        stage('Unity export apk') {
            options {
                timeout(time: 180, unit: 'MINUTES')
            }

            steps {
                script {
                withCredentials([
                        string(credentialsId: 'keyalias_password', variable: "KEYALIAS_PASS"),
                        string(credentialsId: 'keystore_password', variable: "KEYSTORE_PASS")
                    ])
                    {
                        StringBuilder commandBuilder = new StringBuilder()
                        commandBuilder.append "$UNITY_PATH"
                        commandBuilder.append " -projectPath $WORKSPACE"
                        commandBuilder.append " -quit -batchmode"
                        commandBuilder.append " -executeMethod $UNITY_METHOD"
                        commandBuilder.append " -logFile ${WORKSPACE}/Logs/build_${BUILD_ID}_apk_log.txt"
                        commandBuilder.append " -buildTarget $BUILD_TARGET"
                        commandBuilder.append " -BuildNumber $BUILD_ID"
                        commandBuilder.append " -OutputPath $OUTPUT_PATH"
                        commandBuilder.append " -buildKind $BUILD_KIND"
                        commandBuilder.append " -androidArchitectures ARM64"
                        commandBuilder.append " -keystorePass ${KEYSTORE_PASS}"
                        commandBuilder.append " -keyaliasPass ${KEYALIAS_PASS}"
                        
                        sh(script:commandBuilder.toString(), returnStdout:false)
                    }
                }
                archiveArtifacts artifacts: OUTPUT_PATH + "/" + PRODUCT_NAME + ".apk,", fingerprint: true, followSymlinks: false
            }
        }
        stage('バージョン情報の取得') {
            steps {
                script {
                    def APP_OUTPUT_PATH = "./$OUTPUT_PATH/${PRODUCT_NAME}.apk"
                    def version_script = $/eval "aapt2 dump badging ${APP_OUTPUT_PATH} | grep 'versionName' | sed -e 's/.*versionName=//' -e 's/ .*//'"/$
                    VERSION = sh(script:"${version_script}", returnStdout:true)
                    VERSION = VERSION.replaceAll("'", "")
                    echo "version=${VERSION}"
                }
            }
        }
        stage('Unity export aab') {
            options {
                timeout(time: 180, unit: 'MINUTES')
            }

            steps {
                script {
                    withCredentials([
                        string(credentialsId: 'keyalias_password', variable: "KEYALIAS_PASS"),
                        string(credentialsId: 'keystore_password', variable: "KEYSTORE_PASS")
                        ]) {
                            // architectureの文字列指定の仕方がよくないので引数割愛。指定しなくても上書きはしないので良いが、キレイに治したいところ

                        StringBuilder commandBuilder = new StringBuilder()
                        commandBuilder.append "$UNITY_PATH"
                        commandBuilder.append " -projectPath $WORKSPACE"
                        commandBuilder.append " -quit -batchmode"
                        commandBuilder.append " -executeMethod $UNITY_METHOD"
                        commandBuilder.append " -logFile ${WORKSPACE}/Logs/build_${BUILD_ID}_aab_log.txt"
                        commandBuilder.append " -buildTarget $BUILD_TARGET"
                        commandBuilder.append " -BuildNumber $BUILD_ID"
                        commandBuilder.append " -OutputPath $OUTPUT_PATH"
                        commandBuilder.append " -buildKind $BUILD_KIND"
                        commandBuilder.append " -androidArchitectures 'ARMv7;ARM64'"
                        commandBuilder.append " -useAndroidAppBundle -uploadToStore"
                        commandBuilder.append " -keystorePass ${KEYSTORE_PASS}"
                        commandBuilder.append " -keyaliasPass ${KEYALIAS_PASS}"
                        
                        sh(script:commandBuilder.toString(), returnStdout:false)
                    }
                }

                archiveArtifacts artifacts: OUTPUT_PATH + "/" + PRODUCT_NAME + ".aab,", fingerprint: false, followSymlinks: false
            }
        }
        // upload AppCenter for apk
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

                    APP_NAME = appcenterUtility.getAppCenterAppName("android", BUILD_KIND)

                    println 'appcenterへのアップロード parameters'
                    println 'APPCENTER_API_TOKEN:'+ params.APPCENTER_API_TOKEN
                    println 'APP_NAME:'+ params.APP_NAME
                    println 'OUTPUT_DIR:'+ OUTPUT_PATH
                    println 'copyArtifacts_ProjectName:'+ value:env.JOB_NAME
                    println 'target_filter_artifact:'+ ''
                    println 'upstream_build_number:'+ env.BUILD_NUMBER
                    println 'upstream_build_user:'+ value: BUILDER
                    println 'APP_FILENAME:'+ "${PRODUCT_NAME}.apk"
                    println 'DISTRIBUTION_GROUPS:'+ appcenterUtility.getAppCenterDistributionGroups()
                    println 'RELEASENOTE:'+ releaseNote
                    
                    build job: 'Upload_AppCenter',
                    parameters: [
                    string(name: 'APPCENTER_API_TOKEN', value: params.APPCENTER_API_TOKEN),
                    string(name: 'APP_NAME', value: APP_NAME),
                    string(name: 'OUTPUT_DIR', value: OUTPUT_PATH),
                    string(name: 'copyArtifacts_ProjectName', value:env.JOB_NAME),
                    string(name: 'target_filter_artifact', value: ''),
                    string(name: 'upstream_build_number', value: env.BUILD_NUMBER),
                    string(name: 'upstream_build_user', value: BUILDER),
                    string(name: 'APP_FILENAME', value: "${PRODUCT_NAME}.apk"),
                    string(name: 'DISTRIBUTION_GROUPS', value: appcenterUtility.getAppCenterDistributionGroups()),
                    text(name: 'RELEASENOTE', value: releaseNote)]

                    //RELEASE_ID = appcenterUtility.getReleaseId(env.APPCENTER_OWNER, APP_NAME, env.APPCENTER_API_TOKEN)
                    //println "appcenter ReleaseID:${RELEASE_ID}"
                }
            }
        }
        stage('Deploy Upload Google Play Console') {
            steps {
                androidApkUpload filesPattern: "${OUTPUT_PATH}/${PRODUCT_NAME}.aab",
                    googleCredentialsId: "google store upload",
                    recentChangeList: [
                        [
                            language: 'ja-JP',
                            text: "ビルド${BUILD_ID} ${USERNAME} / RELEASE NOTE: ${RELEASENOTE}"
                        ]
                    ],
                    releaseName: "$VERSION",
                    rolloutPercentage: '0',
                    trackName: 'internal'
            }
        }
    }
    post {
        success {
            // NOTE:GITのLogを付加したいので子ジョブで成功通知を出す
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
                println "ビルド所要時間${currentBuild.durationString}"
            }
        }
        always {
            // ログ保存
            archiveArtifacts allowEmptyArchive: true,
            artifacts: "Logs/build_${BUILD_ID}_apk_log.txt, Logs/build_${BUILD_ID}_aab_log.txt, Logs/assetbuild_${BUILD_ID}_log.txt",
            fingerprint: true,
            followSymlinks: false
        }
    }
}
