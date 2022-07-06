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
                    'Dev':'MCombat_A',
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
                    'Dev':'',
                    'QA':'',
                    'Beta':'',
                    'Release':''
                ]
        if (map.containsKey(appKind)) {
            return map.get(appKind)
        }
    }
    else if (platform == "android") {
        def map = [
                    'Dev':'f15e49718855b8bba0a8d3104c4e6fcf48193865',
                    'QA':'',
                    'Beta':'',
                    'Release':''
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
    return "https://install.appcenter.ms/users/${ownerName}/apps/${appName}/releases/${releaseId}"
}

return this
