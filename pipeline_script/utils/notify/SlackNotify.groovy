/**
 * スラック通知ようのクラス
 */
class SlackNotify {
    def channels
    def credentialsId
    def botUserName = "P3 Jenkins ジョブ通知"
    def buildKind
    def assetKind
    def platform
    def releaseNote
    def downloadURL
    def buildUser
    def version
    def buildTime

    // for appcenter
    def appcenterRleaseId

    // for git infomation
    def branch
    def hash

    def SlackNotify(channels, credentialsId, buildKind, platform, releaseNote) {
        this.channels = channels
        this.credentialsId = credentialsId
        this.buildKind = buildKind
        this.platform = platform
        this.releaseNote = releaseNote
    }

    /**
     * ビルドユーザーは後で設定できた方が楽の場合があるので
     * @param user  [description]
     */
    void SetBuildUser(user) {
        this.buildUser = user
    }

    /**
     * アセット種別を指定
     * @param assetKind  [description]
     */
    void SetAssetKind(kind) {
        this.assetKind = kind
    }

    /**
     * releaseNoteを設定する
     * @param releaseNote  [description]
     */
    void SetReleaseNotes(releaseNote) {
        this.releaseNote = releaseNote
    }

    /**
     * appcenterの情報を後から入れられるようにしておく
     * @param id           [description]
     * @param downloadURL  [description]
     */
    void SetAppCenterInfomation(id, downloadURL, version) {
        this.appcenterRleaseId = id
        this.downloadURL = downloadURL
        this.version = version
    }

    /**
     * Gitのブランチ情報などを後から付与
     * @param branch  [description]
     * @param hash    [description]
     */
    void SetGitInfomation(branch, hash) {
        this.branch = branch
        this.hash = hash
    }

    /**
     * かかったビルド時間の設定
     * @param buildTime  [description]
     */
    void SetBuildTime(buildTime) {
        this.buildTime = buildTime
    }
}

return SlackNotify.class
