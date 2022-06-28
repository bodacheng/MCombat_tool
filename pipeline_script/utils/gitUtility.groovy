/**
 * Git情報を取得するのを共通化するためのgroovy
 * @param  from_branch_name               [description]
 * @return                  [description]
 */
// ブランチ名の取得
def get_branch_name(from_branch_name) {
    def branch = from_branch_name.replace("refs/heads/", "")

    println '--------------------'
    println "branch_name = ${branch}"
    println '--------------------'
    return branch
}

// gitのリビジョン番号の取得
def getGitRevision() {
    println '*** git revision番号の取得 ***'
    def gitRevisionFull = sh(script:'git rev-parse HEAD', returnStdout:true)
    println 'Git Full Revision Number = ' + gitRevisionFull

    // revision Number の抽出(7桁まで区切る）
    def revisionNo = gitRevisionFull.toString().substring(0, 7)

    return revisionNo
}

// ブランチを指定したログを取得
def getGitLogMessage(branch) {
    return sh(script: "git log origin/${branch} --pretty=short -1", returnStdout: true)
}

// コミットメッセージ情報の取得
def getGitCommitMessage() {
    def commitMessage = sh(script:'git log --format=format:%s -1', returnStdout:true)
    return commitMessage
}
// コミットメッセージ情報の取得
def getGitCommitAuthor() {
    def author = sh(script:'git log --format=format:%an -1', returnStdout:true)
    return author
}

//コミット日時の取得
def getGitCommitDate() {
    def date = sh(script:'git log --date=format-local:"%Y/%m/%d %H:%M:%S" --format=format:"%cd" -1', returnStdout:true)
    return date
}

// commitInfomation(ある程度フィルタする)
def getGitCommitInfomation(branch) {
    def message = 'branch:' + branch.GIT_LOCAL_BRANCH +
    '\nAuthor:' + getGitCommitAuthor() +
    '\nMessage:' + getGitCommitMessage()

    return message
}

/**
 * コミット時のdate/Author/messageのログを取得
 * @return [description]
 */
def getGitCommitLatestLog() {
    def message = 'Author:' + getGitCommitAuthor() +
    '\nMessage:' + getGitCommitMessage() +
    '\nDate:' + getGitCommitDate()
    return message
}

return this
