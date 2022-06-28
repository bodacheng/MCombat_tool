/**
 * PlistBuddyとaapt2を使っています
 * @param  platform                [description]
 * @param  outputDir               [description]
 * @param  appName                 [description]
 * @return           [description]
 */
def getVersionName(platform, outputDir, appName) {
    def command = ''
    switch (platform) {
        case "ios":
            command = """
            unzip -p "${outputDir}/${appName}.ipa" Payload/"${appName}".app/Info.plist | plutil -convert json -o - -- - | jq -r .CFBundleShortVersionString
            """
            return sh(script:command, returnStdout:true)
        case "android":
            command = """
            aapt2 dump badging ${outputDir}/${appName}.apk | grep 'versionName' | sed -e 's/.*versionName=//' -e 's/ .*//'
            """
            return sh(script:command, returnStdout:true)
    }

    reurn ''
}

return this
