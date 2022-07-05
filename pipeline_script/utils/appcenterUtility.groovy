/**
 * AppCenterのAPPName を取得する
 * @param  platform               [description]
 * @param  appKind                [description]
 * @return          [description]
 */
def getAppCenterAppName(platform, appKind) {
    // Dictionaryみたいなのがあればそれがいいかも
    if (platform == "ios") {
        def map = [
                    'Dev':'Pokepia-iOS',
                    'QA':'POKEPIA',
                    'Beta':'POKEPIA-iOSbeta',
                    'Release':'Pokepia-iOS-1'
                ]
        if (map.containsKey(appKind)) {
            return map.get(appKind)
        }
    }
    else if (platform == "android") {
        def map = [
                    'Dev':'MCombat_A_Dev',
                    'QA':'Pokepia-Android-3',
                    'Beta':'POKEPIA-Androidbeta',
                    'Release':'Pokepia-Android-1'
                ]
        if (map.containsKey(appKind)) {
            return map.get(appKind)
        }
    }

    return ""
}

/**
 * AppCenter AppのToken情報取得
 * @param  platform               [description]
 * @param  appKind                [description]
 * @return          [description]
 */
def getAppCenterToken(platform, appKind) {
    if (platform == "ios") {
        def map = [
                    'Dev':'3950f9fbd18dd8e2d18cb970933d125115bf6a67',
                    'QA':'027d9e2eab70992a3681db2743ed6ebb3d18d93b',
                    'Beta':'7e7c1f4cd71e0803466726400d82e783c5d0b319',
                    'Release':'33686d7866a23604057424c52b0474392c5c3b7e'
                ]
        if (map.containsKey(appKind)) {
            return map.get(appKind)
        }
    }
    else if (platform == "android") {
        def map = [
                    'Dev':'f44294c51d7cec86b1ee9002a3c92a0b22b44322',
                    'QA':'ef6bb95d43d77829b8b87f301288273fba6e5d40',
                    'Beta':'956368c661c35b21264b6153d1f8e5ad46820401',
                    'Release':'585387c9fed92e48c21dd4d2852823d8a14831f0'
                ]
        if (map.containsKey(appKind)) {
            return map.get(appKind)
        }
    }

    retrun ''
}

/**
 * AppCenter配布グループ情報の取得
 * @return         [description]
 */
def getAppCenterDistributionGroups() {
    return "MCombat-QA, Collaborators, HAKU"
}

/**
 * appcenterの最新のreleaseIDを取得する（最新のreleaseIDを取得するのでタイミングがかち合わないと失敗の可能性はある）
 * NOTE:AppCenter CLIが必要なので注意
 * @param  ownerName               [description]
 * @param  appName                 [description]
 * @param  apiToken                [description]
 * @return           [description]
 */
def getReleaseId(ownerName, appName, apiToken) {
    def script = """
    appcenter distribute releases list --app ${ownerName}/${appName} --token ${apiToken} --output json | jq '. | sort_by(.uploadedAt) | reverse | .[0].id'
    """
    return sh(script:script, returnStdout:true)
}

/**
 * appcenterのダウンロードURLの取得
 * @param  ownerName               [description]
 * @param  appName                 [description]
 * @param  releaseId               [description]
 * @return           [description]
 */
def getDownloadURL(ownerName, appName, releaseId) {
    return "https://install.appcenter.ms/orgs/${ownerName}/apps/${appName}/releases/${releaseId}"
}

return this
