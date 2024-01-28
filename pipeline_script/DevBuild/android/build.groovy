pipeline {
    agent {
        label "master"
    }

    // param.ANDROID_ARCHSは、"ARMv7;ARM64"のように、複数の場合は;を入れて指定する
    environment {
        // groovy Files
        gitUtility = ''
        appcenterUtility = ''
        slackUtility = ''
        slackNotify = ''
        versionInfomationUtility = ''
        buildUtility = ''
        
        // git
        GIT_LOG = ''
        GIT_HASH = ''
        
        // branch name
        BRANCH_NAME = ''
        
        // appcenter
        RELEASE_ID = ''
        APP_NAME = ''

        // environment values
        UNITY_PATH="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"
        BUILDER = ''

        // build configuration
        UNITY_METHOD='Cocone.ProjectP3.Client.Build'
        ADDRESSABLE_METHOD='Cocone.ProjectP3.BuildAddressableAssets.BatchBuild'
        BUILD_TARGET='Android'
        PRODUCT_NAME=''
        VERSION=""
        OUTPUT_PATH='build_android'
        BUILD_CONFIG_DIR='Assets/App/Editor/Build/Configs'
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
                    buildUtility = load "${utilisPath}/buildUtility.groovy"
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
                            //[$class: 'GitLFSPull'],
                            //[$class: 'CloneOption', timeout: 360],
                            //[$class: 'CheckoutOption', timeout: 360]
                        ],
                        gitTool: 'Default',
                        userRemoteConfigs: [[credentialsId: params.GIT_CREDENTIAL, url: params.GIT_URL]]
                    ])
                    
                    // Git情報の取得
                    //GIT_LOG = gitUtility.getGitLogMessage(BRANCH_NAME)
                    //GIT_HASH = gitUtility.getGitRevision()
                }
            }
        }
        stage('yaml取得/初期化') {
            steps {
                script {
                    // TODO:read yamlに直したい
                    def yamlFile = "${BUILD_CONFIG_DIR}/${BUILD_KIND}BuildSettings.yaml"
                    def script = $/eval "cat ${yamlFile} | grep -o 'productName: .*$' | sed -e 's/productName: ''//'"/$
                    PRODUCT_NAME = sh(script:"${script}", returnStdout:true)
                    PRODUCT_NAME = PRODUCT_NAME.replaceAll("\n", "")
                    println '-------- PRODUCT_NAME:' + PRODUCT_NAME

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

                    // ビルド前に出力ディレクトリの削除
                    dir(env.OUTPUT_PATH) {
                        deleteDir()
                    }

                    // 現在のジョブについての説明
                    //currentBuild.description = "ビルド種別：${params.BUILD_KIND}\nアセット種別：${params.AssetKind}\nブランチ：${BRANCH_NAME}\nGITLOG：${GIT_LOG}"
                }
            }
        }
        stage('Assets') {
            when {
                expression {
                   return params.buildAsset
                }
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
                // androidはiosよりもステージは長くなる（xcode/exportを内包するため）
                timeout(time: 360, unit: 'MINUTES')
            }
            steps {
                script {
                withCredentials([
                        string(credentialsId: 'keyalias_password', variable: "KEYALIAS_PASS"),
                        string(credentialsId: 'keystore_password', variable: "KEYSTORE_PASS")
                        // 这个的大体意思似乎是，keyalias_password也就是说用的你jenkins控制台设置的那个凭据的名字，
                        // 在这个位置把它又给起了个名字叫KEYALIAS_PASS，然后就带入到底下那个${KEYSTORE_PASS}上去了。
                        // 你在jenkins控制台设置的keyalias_password和keystore_password，里面的值其实是能跟着
                        // 那个保留了所有jenkins设置的项目复制过去的，但是每次你在新电脑上建设jenkins的时候，
                        // 你都似乎需要在unity里把keystore那个地方用密码输入一下，然后编译的时候就没问题了。
                        // 密码似乎是890710gxy
                    ]) 
                    {
                        buildUtility.deleteDirectory(WORKSPACE+ "Library/Bee/Android");
                    
                        println "androidArchitecture:" + params.ANDROID_ARCHS
                        println "WORKSPACE:" + WORKSPACE
    
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
                        commandBuilder.append " -developmentBuild ${params.developmentBuild}"
                        commandBuilder.append " -androidArchitectures '${params.ANDROID_ARCHS}'"
                        commandBuilder.append " -keystorePass ${KEYSTORE_PASS}"
                        commandBuilder.append " -keyaliasPass ${KEYALIAS_PASS}"
    
                        def tempPath = commandBuilder.toString()
                        println tempPath
    
                        // apk作成
                        sh(script:commandBuilder.toString(), returnStdout:false)
                    }
                }
            }
        }
        stage('バージョン情報の取得/apk保存') {
            steps {
                script {
                    versionName = versionInfomationUtility.getVersionName('android', "./${OUTPUT_PATH}", PRODUCT_NAME)
                    VERSION = versionName.replaceAll("'", '')
                    println 'apk/aab versionName:' + VERSION
                }
                
                archiveArtifacts artifacts: "${OUTPUT_PATH}/${PRODUCT_NAME}.apk,",
                fingerprint: true,
                followSymlinks: false
            }
        }
        stage('AppCenterのアップロード') {
             steps{
                script {
                    APP_NAME = appcenterUtility.getAppCenterAppName("android", params.BUILD_KIND)
                    BUILDER = env.BUILD_USER_ID
                    
                    println 'appcenterへのアップロード parameters'
                    println 'APPCENTER_API_TOKEN:'+ params.APPCENTER_API_TOKEN
                    println 'APP_NAME:'+ APP_NAME
                    println 'OUTPUT_DIR:'+ OUTPUT_PATH
                    println 'copyArtifacts_ProjectName:'+ env.JOB_NAME
                    println 'target_filter_artifact:'+ ''
                    println 'upstream_build_number:'+ env.BUILD_NUMBER
                    println 'upstream_build_user:'+ BUILDER
                    println 'APP_FILENAME:'+ "${PRODUCT_NAME}.apk"
                    println 'DISTRIBUTION_GROUPS:'+ appcenterUtility.getAppCenterDistributionGroups()
                    println 'RELEASENOTE:'+ params.RELEASENOTE
                    
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
                    text(name: 'RELEASENOTE', value: params.RELEASENOTE)]
                    
                    //RELEASE_ID = appcenterUtility.getReleaseId(env.APPCENTER_OWNER, APP_NAME, params.APPCENTER_API_TOKEN)
                }
            }
        }
    }
    post {
        success {
            // NOTE:GITのLogを付加したいので子ジョブで成功通知を出す
            script {
                //def preFixReleaseNote = ":kirby::tada:*ビルド成功 [Job:$JOB_NAME/BuildNo:$BUILD_ID]*:tada::kirby:\n${env.BUILD_URL}"
                //def releaseNote = "${preFixReleaseNote}\n--\n${params.RELEASENOTE}\n--\n${GIT_LOG}"

                //def downloadURL = appcenterUtility.getDownloadURL(env.APPCENTER_OWNER, APP_NAME, RELEASE_ID)
                //println "downloadURL:${downloadURL}"
                
                //slackNotify.SetAppCenterInfomation(RELEASE_ID, downloadURL, VERSION)
                //slackNotify.SetBuildUser(USERNAME.toString() + "/@" + BUILDER)
                //slackNotify.SetGitInfomation(BRANCH_NAME, GIT_HASH)
                //slackNotify.SetReleaseNotes(releaseNote)
                //slackNotify.SetAssetKind(AssetKind)
                //slackNotify.SetBuildTime(currentBuild.durationString)
                //slackUtility.notifySlackSendMessage(slackNotify)

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
