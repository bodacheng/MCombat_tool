String exportOptionsPlist()
{
    return '''<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>destination</key>
    <string>export</string>
    <key>method</key>
    <string>app-store-connect</string>
    <key>signingStyle</key>
    <string>manual</string>
    <key>signingCertificate</key>
    <string>Apple Distribution</string>
    <key>provisioningProfiles</key>
    <dict>
        <key>com.headsling.prototype</key>
        <string>HEADSLING App Store</string>
    </dict>
    <key>teamID</key>
    <string>S88E744TXJ</string>
    <key>stripSwiftSymbols</key>
    <true/>
    <key>manageAppVersionAndBuildNumber</key>
    <false/>
</dict>
</plist>
'''
}

pipeline {
    agent {
        node {
            label ''
            customWorkspace '/Users/daisei/HeadGame'
        }
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
    }

    parameters {
        string(name: 'APP_VERSION', defaultValue: '1.0', description: 'App Store version (CFBundleShortVersionString).')
        booleanParam(name: 'CLEAN_LIBRARY', defaultValue: false, description: 'Delete the Unity Library cache before building.')
        booleanParam(name: 'UPLOAD_APP_STORE', defaultValue: true, description: 'Validate and upload the IPA to App Store Connect.')
    }

    environment {
        UNITY_BUILD_METHOD = 'HeadSling.Editor.HeadSlingMobileBuilder.BuildIOS'
        APPLE_TEAM_ID = 'S88E744TXJ'
        APP_BUNDLE_ID = 'com.headsling.prototype'
        XCODE_OUTPUT_PATH = 'Builds/iOS'
        ARCHIVE_PATH = 'Builds/iOSArchive/HEADSLING.xcarchive'
        IPA_OUTPUT_PATH = 'Builds/iOSIPA'
        DERIVED_DATA_PATH = 'Builds/iOSDerivedData'
        EXPORT_OPTIONS_PATH = 'Builds/iOSExportOptions.plist'
    }

    stages {
        stage('Preflight') {
            steps {
                script {
                    if (!(params.APP_VERSION ==~ /^[0-9]+([.][0-9]+){0,2}$/)) {
                        error("APP_VERSION must contain one to three numeric components: ${params.APP_VERSION}")
                    }
                    currentBuild.description = "Version: ${params.APP_VERSION}\nBuild: ${env.BUILD_NUMBER}\nBundle: ${env.APP_BUNDLE_ID}"
                }
                sh '''#!/bin/bash
set -euo pipefail

UNITY_VERSION="$(awk '$1 == "m_EditorVersion:" { print $2; exit }' ProjectSettings/ProjectVersion.txt)"
UNITY_EXECUTABLE="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"
if [ -z "$UNITY_VERSION" ] || [ ! -x "$UNITY_EXECUTABLE" ]; then
    echo "Unity executable not found: $UNITY_EXECUTABLE" >&2
    exit 2
fi

if [ "$CLEAN_LIBRARY" = "true" ]; then
    rm -rf "$WORKSPACE/Library"
fi

rm -rf "$WORKSPACE/$XCODE_OUTPUT_PATH" "$WORKSPACE/$ARCHIVE_PATH" \
       "$WORKSPACE/$IPA_OUTPUT_PATH" "$WORKSPACE/$DERIVED_DATA_PATH"
mkdir -p "$WORKSPACE/Logs/Jenkins" "$WORKSPACE/$(dirname "$ARCHIVE_PATH")" \
         "$WORKSPACE/$IPA_OUTPUT_PATH" "$WORKSPACE/$DERIVED_DATA_PATH"

echo "Unity $UNITY_VERSION"
xcodebuild -version
security find-identity -v -p codesigning
'''
            }
        }

        stage('Unity Export') {
            steps {
                sh '''#!/bin/bash
set -euo pipefail

UNITY_VERSION="$(awk '$1 == "m_EditorVersion:" { print $2; exit }' ProjectSettings/ProjectVersion.txt)"
UNITY_EXECUTABLE="/Applications/Unity/Hub/Editor/${UNITY_VERSION}/Unity.app/Contents/MacOS/Unity"

HEADSLING_BUNDLE_ID="$APP_BUNDLE_ID" \
HEADSLING_APP_VERSION="$APP_VERSION" \
HEADSLING_BUILD_NUMBER="$BUILD_NUMBER" \
"$UNITY_EXECUTABLE" \
  -projectPath "$WORKSPACE" \
  -quit \
  -batchmode \
  -nographics \
  -buildTarget iOS \
  -executeMethod "$UNITY_BUILD_METHOD" \
  -logFile "$WORKSPACE/Logs/Jenkins/ios_${BUILD_NUMBER}.log"
'''
            }
        }

        stage('Unlock Keychain') {
            steps {
                withCredentials([string(credentialsId: 'PCUSER_PASSWORD', variable: 'KEYCHAIN_PASSWORD')]) {
                    sh '''#!/bin/bash
set -euo pipefail
KEYCHAIN="$HOME/Library/Keychains/login.keychain-db"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security set-keychain-settings -lut 21600 "$KEYCHAIN"
'''
                }
            }
        }

        stage('Xcode Archive') {
            steps {
                withCredentials([
                    string(credentialsId: 'HEADSLING_ASC_KEY_ID', variable: 'API_KEY'),
                    string(credentialsId: 'HEADSLING_ASC_ISSUER_ID', variable: 'API_ISSUER')
                ]) {
                    sh '''#!/bin/bash
set -euo pipefail

PRIVATE_KEY="$HOME/.appstoreconnect/private_keys/AuthKey_${API_KEY}.p8"
test -f "$PRIVATE_KEY"

cd "$WORKSPACE/$XCODE_OUTPUT_PATH"
if [ -d "Unity-iPhone.xcworkspace" ]; then
    CONTAINER_ARGS=( -workspace "Unity-iPhone.xcworkspace" )
elif [ -d "Unity-iPhone.xcodeproj" ]; then
    CONTAINER_ARGS=( -project "Unity-iPhone.xcodeproj" )
else
    echo "Unity-iPhone Xcode project was not generated." >&2
    exit 3
fi

xcodebuild "${CONTAINER_ARGS[@]}" \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$PRIVATE_KEY" \
  -authenticationKeyID "$API_KEY" \
  -authenticationKeyIssuerID "$API_ISSUER" \
  -scheme Unity-iPhone \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$WORKSPACE/$ARCHIVE_PATH" \
  -derivedDataPath "$WORKSPACE/$DERIVED_DATA_PATH" \
  archive \
  CODE_SIGN_STYLE=Automatic \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" \
  MARKETING_VERSION="$APP_VERSION" \
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER"
'''
                }
            }
        }

        stage('Export IPA') {
            steps {
                script {
                    writeFile(file: env.EXPORT_OPTIONS_PATH, text: exportOptionsPlist())
                }
                sh '''#!/bin/bash
set -euo pipefail

xcodebuild -exportArchive \
  -archivePath "$WORKSPACE/$ARCHIVE_PATH" \
  -exportPath "$WORKSPACE/$IPA_OUTPUT_PATH" \
  -exportOptionsPlist "$WORKSPACE/$EXPORT_OPTIONS_PATH"

IPA_FILE="$(find "$WORKSPACE/$IPA_OUTPUT_PATH" -maxdepth 1 -type f -name '*.ipa' -print -quit)"
test -n "$IPA_FILE"
'''
            }
        }

        stage('Archive IPA') {
            steps {
                archiveArtifacts(
                    artifacts: 'Builds/iOSIPA/*.ipa',
                    fingerprint: true,
                    followSymlinks: false
                )
            }
        }

        stage('Upload App Store') {
            when {
                expression { return params.UPLOAD_APP_STORE }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'HEADSLING_ASC_KEY_ID', variable: 'API_KEY'),
                    string(credentialsId: 'HEADSLING_ASC_ISSUER_ID', variable: 'API_ISSUER')
                ]) {
                    sh '''#!/bin/bash
set -euo pipefail

PRIVATE_KEY="$HOME/.appstoreconnect/private_keys/AuthKey_${API_KEY}.p8"
if [ ! -f "$PRIVATE_KEY" ]; then
    echo "App Store Connect private key is missing: ~/.appstoreconnect/private_keys/AuthKey_<key-id>.p8" >&2
    exit 4
fi

IPA_FILE="$(find "$WORKSPACE/$IPA_OUTPUT_PATH" -maxdepth 1 -type f -name '*.ipa' -print -quit)"
xcrun altool --validate-app -f "$IPA_FILE" -t ios --apiKey "$API_KEY" --apiIssuer "$API_ISSUER"
xcrun altool --upload-app -f "$IPA_FILE" -t ios --apiKey "$API_KEY" --apiIssuer "$API_ISSUER"
'''
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts(
                allowEmptyArchive: true,
                artifacts: "Logs/Jenkins/ios_${env.BUILD_NUMBER}.log, Builds/iOSExportOptions.plist, Builds/iOSIPA/*.ipa",
                fingerprint: true,
                followSymlinks: false
            )
        }
    }
}
