// Dedicated to AssetDev_V. Its workspace must be separate from CustomIOSBuild_V.
def requiredScalar(String path, String key) {
    def entries = readFile(path).readLines().findAll { it.startsWith("${key}:") }
    if (entries.size() != 1) { error("Expected exactly one ${key} in ${path}") }
    def value = entries[0].substring(key.length() + 1).trim()
    if (!value || value.contains('\n')) { error("Missing ${key} in ${path}") }
    return value
}

def projectVersion() {
    def entries = readFile('ProjectSettings/ProjectSettings.asset').readLines().findAll { it.trim().startsWith('bundleVersion:') }
    if (entries.size() != 1) { error('Expected exactly one bundleVersion in ProjectSettings/ProjectSettings.asset.') }
    def version = entries[0].trim().substring('bundleVersion:'.length()).trim()
    if (!(version ==~ /[0-9]+\.[0-9]+\.[0-9]+/)) { error('Project bundleVersion must be a numeric dotted version.') }
    return version
}

def versionedAssetPath(String path, String key, String version, String kind) {
    def template = requiredScalar(path, key)
    def environment = kind.toLowerCase()
    def pattern = key.startsWith('Build')
        ? 'ServerData/' + environment + '/v/\\{version\\}/?'
        : 's3://[A-Za-z0-9_.-]+/' + environment + '/v/\\{version\\}/?'
    if (!(template ==~ pattern)) { error("${key} must use the ${environment}/v/{version} template; explicit versions are not allowed.") }
    return template.replace('{version}', version)
}

def buildAssets(String target) {
    withEnv(["ASSET_TARGET=${target}"]) {
        sh '''#!/bin/bash
set -euo pipefail
"$UNITY_PATH" -projectPath "$WORKSPACE" -quit -batchmode \
  -executeMethod Cocone.ProjectP3.BuildAddressableAssets.BatchBuild \
  -logFile "$WORKSPACE/Logs/build_${ASSET_TARGET}_${BUILD_NUMBER}_log.txt" \
  -buildTarget "$ASSET_TARGET" -assetProfile "$ASSET_PROFILE"
ASSET_DIRECTORY="$WORKSPACE/${ASSET_BUILDPATH%/}/$ASSET_TARGET"
test -d "$ASSET_DIRECTORY"
CATALOG="$(find "$ASSET_DIRECTORY" -type f \
  \\( -name 'catalog_*.json' -o -name 'catalog_*.bin' \\) -size +0c -print -quit)"
BUNDLE="$(find "$ASSET_DIRECTORY" -type f -name '*.bundle' -size +0c -print -quit)"
HASH="$(find "$ASSET_DIRECTORY" -type f -name 'catalog_*.hash' -size +0c -print -quit)"
if [ -z "$CATALOG" ] || [ -z "$HASH" ] || [ -z "$BUNDLE" ]; then
    echo "Addressables output is incomplete: $ASSET_DIRECTORY" >&2
    exit 3
fi
python3 Tools/package_addressables_bootstrap.py \
  --runtime-dir "$WORKSPACE/Library/com.unity.addressables/aa/$ASSET_TARGET" \
  --server-dir "$ASSET_DIRECTORY"
'''
    }
}

def uploadAssets(String target) {
    withEnv(["ASSET_TARGET=${target}"]) {
        sh '''#!/bin/bash
set -euo pipefail
ASSET_DIRECTORY="$WORKSPACE/${ASSET_BUILDPATH%/}/$ASSET_TARGET/"
DESTINATION="${UPLOAD_S3_ADDRESS%/}/$ASSET_TARGET/"
# Publish bundles before the catalog that references them.
set --
if [ "$AWS_CACHE" = 'true' ]; then set -- --cache-control 'max-age=86400'; fi
aws s3 cp --recursive "$ASSET_DIRECTORY" "$DESTINATION" --profile "$AWS_PROFILE" \
  --exclude 'catalog_*' --exclude 'player-bootstrap.zip' "$@"
# Catalog and bootstrap must be available before the hash advertises new content.
aws s3 cp --recursive "$ASSET_DIRECTORY" "$DESTINATION" --profile "$AWS_PROFILE" \
  --exclude '*' --include 'catalog_*.bin' --include 'catalog_*.json' --cache-control 'no-cache'
aws s3 cp "${ASSET_DIRECTORY}player-bootstrap.zip" "${DESTINATION}player-bootstrap.zip" \
  --profile "$AWS_PROFILE" --cache-control 'no-cache'
aws s3 cp --recursive "$ASSET_DIRECTORY" "$DESTINATION" --profile "$AWS_PROFILE" \
  --exclude '*' --include 'catalog_*.hash' --cache-control 'no-cache'
'''
    }
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
    }
    stages {
        stage('Checkout') {
            steps {
                script {
                    if (params.UNITY_VERSION != '6000.5.1f1') { error('Only Unity 6.5 (6000.5.1f1) is supported.') }
                    if (!(params.AssetKind in ['Dev', 'Release'])) { error('AssetKind must be Dev or Release.') }
                    if (!params.IOS && !params.ANDROID) { error('Select at least one asset platform.') }
                    if (!params.VALIDATE_ONLY && !(params.AWS_PROFILE ==~ /[A-Za-z0-9_-]+/)) { error('AWS_PROFILE is required.') }
                    def branch = (params.BRANCH ?: 'master').trim().replaceFirst('^refs/heads/', '')
                    if (!branch) { error('BRANCH must not be blank.') }
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
                    def settings = 'Assets/App/Editor/Build/Configs/AddressablesProfileSettings.yaml'
                    env.APP_VERSION = projectVersion()
                    env.ASSET_PROFILE = requiredScalar(settings, "Profile${params.AssetKind}")
                    if (env.ASSET_PROFILE != params.AssetKind.toLowerCase()) { error('Addressables profile must match AssetKind.') }
                    env.ASSET_BUILDPATH = versionedAssetPath(settings, "Build${params.AssetKind}", env.APP_VERSION, params.AssetKind)
                    env.UPLOAD_S3_ADDRESS = versionedAssetPath(settings, "Upload${params.AssetKind}", env.APP_VERSION, params.AssetKind)
                    if (params.CLEAR_CACHE) { dir('Library') { deleteDir() } }
                    dir('ServerData') { deleteDir() }
                    currentBuild.description = "${params.AssetKind}: ${env.UPLOAD_S3_ADDRESS}" + (params.VALIDATE_ONLY ? ' (validation only)' : '')
                }
                sh '''#!/bin/bash
set -euo pipefail
test -x "$UNITY_PATH"
PROJECT_UNITY_VERSION="$(awk '$1 == "m_EditorVersion:" { print $2; exit }' ProjectSettings/ProjectVersion.txt)"
if [ "$PROJECT_UNITY_VERSION" != '6000.5.1f1' ]; then
    echo "Project requires Unity $PROJECT_UNITY_VERSION; expected 6000.5.1f1." >&2
    exit 2
fi
PLAYBACK_ENGINES="${UNITY_PATH%/Unity.app/Contents/MacOS/Unity}/PlaybackEngines"
if [ "$IOS" = 'true' ]; then test -d "$PLAYBACK_ENGINES/iOSSupport"; fi
if [ "$ANDROID" = 'true' ]; then test -d "$PLAYBACK_ENGINES/AndroidPlayer"; fi
xcrun metal --version
mkdir -p Logs
if [ "$VALIDATE_ONLY" != 'true' ]; then
    command -v aws
    aws configure list --profile "$AWS_PROFILE" >/dev/null
fi
'''
            }
        }
        stage('Build iOS Assets') {
            when { expression { return params.IOS } }
            options { timeout(time: 180, unit: 'MINUTES') }
            steps { script { buildAssets('iOS') } }
        }
        stage('Upload iOS Assets') {
            when { expression { return params.IOS && !params.VALIDATE_ONLY } }
            steps { script { uploadAssets('iOS') } }
        }
        stage('Build Android Assets') {
            when { expression { return params.ANDROID } }
            options { timeout(time: 180, unit: 'MINUTES') }
            steps { script { buildAssets('Android') } }
        }
        stage('Upload Android Assets') {
            when { expression { return params.ANDROID && !params.VALIDATE_ONLY } }
            steps { script { uploadAssets('Android') } }
        }
    }
    post {
        always {
            archiveArtifacts allowEmptyArchive: true,
                artifacts: "Logs/build_iOS_${env.BUILD_NUMBER}_log.txt,Logs/build_Android_${env.BUILD_NUMBER}_log.txt",
                fingerprint: true, followSymlinks: false
        }
    }
}
