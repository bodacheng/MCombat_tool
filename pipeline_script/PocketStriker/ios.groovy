// Dedicated to CustomIOSBuild_V; job parameters remain managed by Jenkins.
def requiredScalar(String path, String key) {
    def entries = readFile(path).readLines().findAll { it.startsWith("${key}:") }
    if (entries.size() != 1) { error("Expected exactly one ${key} in ${path}") }
    def value = entries[0].substring(key.length() + 1).trim()
    if (!value || value.contains('\n')) { error("Missing ${key} in ${path}") }
    return value
}

pipeline {
    agent { label 'built-in' }
    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
    }
    environment {
        LANG = 'en_US.UTF-8'
        LC_ALL = 'en_US.UTF-8'
        UNITY_PATH = '/Applications/Unity/Hub/Editor/6000.5.1f1/Unity.app/Contents/MacOS/Unity'
        BUILD_CONFIG_DIR = 'Assets/App/Editor/Build/Configs'
        OUTPUT_PATH = 'build_ios/Export'
        ARCHIVE_PATH = 'build_ios/Archive.xcarchive'
        IPA_OUTPUT_PATH = 'build_ios/IPA'
        DERIVED_DATA_PATH = 'build_ios/DerivedData'
    }
    stages {
        stage('Checkout') {
            steps {
                script {
                    if (params.UNITY_VERSION != '6000.5.1f1') { error('Only Unity 6.5 (6000.5.1f1) is supported.') }
                    if (!(params.BUILD_KIND in ['Dev', 'Release'])) { error('BUILD_KIND must be Dev or Release.') }
                    if (!(params.AssetKind in ['Dev', 'Release'])) { error('AssetKind must be Dev or Release.') }
                    if (!(params.machine_name ==~ /[A-Za-z0-9_-]+/)) { error('Invalid machine_name.') }
                    def branch = (params.BRANCH ?: 'master').trim().replaceFirst('^refs/heads/', '')
                    if (!branch) { error('BRANCH must not be blank.') }
                    if (params.needCleanWorkspace) { deleteDir() }
                    checkout([$class: 'GitSCM', branches: [[name: branch]],
                        extensions: [
                            [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true,
                                recursiveSubmodules: true, reference: '', shallow: false, trackingSubmodules: false],
                            [$class: 'CloneOption', timeout: 180], [$class: 'CheckoutOption', timeout: 180]],
                        userRemoteConfigs: [[credentialsId: params.GIT_CREDENTIAL, url: params.GIT_URL]]])
                }
            }
        }
        stage('Preflight') {
            steps {
                script {
                    def buildSettings = "${env.BUILD_CONFIG_DIR}/${params.BUILD_KIND}BuildSettings.yaml"
                    requiredScalar(buildSettings, 'cfBundleName')
                    env.ASSET_PROFILE = requiredScalar("${env.BUILD_CONFIG_DIR}/AddressablesProfileSettings.yaml", "Profile${params.AssetKind}")
                    if (!(env.ASSET_PROFILE ==~ /[A-Za-z0-9_-]+/)) { error('Invalid Addressables profile.') }
                    env.EXPORT_OPTIONS_PATH = "${env.BUILD_CONFIG_DIR}/iOS/${params.machine_name}/ExportOptions_${params.BUILD_KIND}.plist"
                    if (!fileExists(env.EXPORT_OPTIONS_PATH)) { error("Missing ${env.EXPORT_OPTIONS_PATH}") }
                    env.XCODE_CONFIGURATION = params.developmentBuild ? 'Debug' : 'Release'
                    if (params.CLEAR_CACHE) { dir('Library') { deleteDir() } }
                    dir('build_ios') { deleteDir() }
                }
                sh '''#!/bin/bash
set -euo pipefail
test -x "$UNITY_PATH"
PROJECT_UNITY_VERSION="$(awk '$1 == "m_EditorVersion:" { print $2; exit }' ProjectSettings/ProjectVersion.txt)"
if [ "$PROJECT_UNITY_VERSION" != '6000.5.1f1' ]; then
    echo "Project requires Unity $PROJECT_UNITY_VERSION; expected 6000.5.1f1." >&2
    exit 2
fi
test -d "${UNITY_PATH%/Unity.app/Contents/MacOS/Unity}/PlaybackEngines/iOSSupport"
xcrun metal --version
mkdir -p Logs "$IPA_OUTPUT_PATH"
plutil -lint "$EXPORT_OPTIONS_PATH"
xcodebuild -version
'''
            }
        }
        stage('Addressables') {
            when { expression { return params.buildAsset } }
            options { timeout(time: 180, unit: 'MINUTES') }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
"$UNITY_PATH" -projectPath "$WORKSPACE" -quit -batchmode \
  -executeMethod Cocone.ProjectP3.BuildAddressableAssets.BatchBuild \
  -logFile "$WORKSPACE/Logs/assetbuild_${BUILD_NUMBER}_log.txt" \
  -buildTarget iOS -assetProfile "$ASSET_PROFILE"
'''
            }
        }
        stage('Unity Export') {
            options { timeout(time: 180, unit: 'MINUTES') }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
"$UNITY_PATH" -projectPath "$WORKSPACE" -quit -batchmode \
  -executeMethod Cocone.ProjectP3.Client.Build \
  -logFile "$WORKSPACE/Logs/build_${BUILD_NUMBER}_log.txt" \
  -buildTarget iOS -BuildNumber "$BUILD_NUMBER" -OutputPath "$OUTPUT_PATH" \
  -buildKind "$BUILD_KIND" -developmentBuild "$developmentBuild" \
  -machineName "$machine_name" -assetProfile "$ASSET_PROFILE"
test -f "$OUTPUT_PATH/Unity-iPhone.xcodeproj/project.pbxproj"
'''
            }
        }
        stage('CocoaPods') {
            when { expression { return params.INSTALL_POD } }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
POD_BIN="$(command -v pod || true)"
if [ -z "$POD_BIN" ]; then
    for candidate in "$HOME"/.gem/ruby/*/bin/pod /opt/homebrew/bin/pod /usr/local/bin/pod; do
        if [ -x "$candidate" ]; then POD_BIN="$candidate"; break; fi
    done
fi
if [ -z "$POD_BIN" ]; then echo 'CocoaPods executable not found.' >&2; exit 127; fi
"$POD_BIN" install --project-directory="$OUTPUT_PATH"
test -d "$OUTPUT_PATH/Unity-iPhone.xcworkspace"
'''
            }
        }
        stage('Unlock Keychain') {
            when { expression { return !params.VALIDATE_ONLY } }
            steps {
                withCredentials([string(credentialsId: 'PCUSER_PASSWORD', variable: 'PC_PASSWORD')]) {
                    sh '''#!/bin/bash
set -euo pipefail
security unlock-keychain -p "$PC_PASSWORD" "$HOME/Library/Keychains/login.keychain-db"
'''
                }
            }
        }
        stage('Xcode Build') {
            options { timeout(time: 180, unit: 'MINUTES') }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
if [ "$INSTALL_POD" = 'true' ]; then
    CONTAINER_ARGS=( -workspace "$WORKSPACE/$OUTPUT_PATH/Unity-iPhone.xcworkspace" )
else
    CONTAINER_ARGS=( -project "$WORKSPACE/$OUTPUT_PATH/Unity-iPhone.xcodeproj" )
fi
if [ "$VALIDATE_ONLY" = 'true' ]; then
    ACTION_ARGS=( build CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY= )
else
    ACTION_ARGS=( archive -archivePath "$WORKSPACE/$ARCHIVE_PATH" )
fi
xcodebuild "${CONTAINER_ARGS[@]}" -scheme Unity-iPhone \
  -configuration "$XCODE_CONFIGURATION" -destination 'generic/platform=iOS' \
  -derivedDataPath "$WORKSPACE/$DERIVED_DATA_PATH" "${ACTION_ARGS[@]}" \
  2>&1 | tee "Logs/xcode_${BUILD_NUMBER}.log"
'''
            }
        }
        stage('Export IPA') {
            when { expression { return !params.VALIDATE_ONLY } }
            steps {
                sh '''#!/bin/bash
set -euo pipefail
xcodebuild -exportArchive -archivePath "$WORKSPACE/$ARCHIVE_PATH" \
  -exportPath "$WORKSPACE/$IPA_OUTPUT_PATH" \
  -exportOptionsPlist "$WORKSPACE/$EXPORT_OPTIONS_PATH" \
  2>&1 | tee "Logs/export_${BUILD_NUMBER}.log"
'''
                script {
                    def ipaFiles = sh(script: "find '${env.IPA_OUTPUT_PATH}' -type f -name '*.ipa'", returnStdout: true).trim().readLines()
                    if (ipaFiles.size() != 1 || !ipaFiles[0]) { error('Expected exactly one exported IPA.') }
                    env.APP_OUTPUT_PATH = ipaFiles[0]
                }
                archiveArtifacts artifacts: 'build_ios/IPA/**/*.ipa', fingerprint: true, followSymlinks: false
            }
        }
        stage('Upload App Store') {
            when { expression { return !params.VALIDATE_ONLY && !params.SIGNING_VALIDATE_ONLY && params.BUILD_KIND == 'Release' } }
            steps {
                withCredentials([
                    string(credentialsId: 'AppleStore_API_Key', variable: 'API_KEY'),
                    string(credentialsId: 'AppleStore_API_Issuer', variable: 'API_ISSUE')]) {
                    sh '''#!/bin/bash
set -euo pipefail
xcrun altool --validate-app -f "$APP_OUTPUT_PATH" -t ios --apiKey "$API_KEY" --apiIssuer "$API_ISSUE"
xcrun altool --upload-app -f "$APP_OUTPUT_PATH" -t ios --apiKey "$API_KEY" --apiIssuer "$API_ISSUE"
'''
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts allowEmptyArchive: true,
                artifacts: "Logs/build_${env.BUILD_NUMBER}_log.txt,Logs/assetbuild_${env.BUILD_NUMBER}_log.txt,Logs/xcode_${env.BUILD_NUMBER}.log,Logs/export_${env.BUILD_NUMBER}.log",
                fingerprint: true, followSymlinks: false
        }
    }
}
