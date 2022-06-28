pipeline {
    agent any // どこで実行するか等の情報(どのノードか、どういうdocker imageかとかを指定する)
    stages {  // Stageを列挙する
        stage('cleanup workspace') {
            steps {
                cleanWs()
            }
        }
        stage('ブランチ') {
            steps {
                // credentionaIdは、jenkinsで設定しているcredentialのid
                git branch: 'master', url: 'https://git-1.cocone.jp/projectp3/p3-tools.git', credentialsId: 'pokepia_buildmachine-1'
            }
        }

        stage('バックアップディレクトリ走査') {
            steps {
                script {
                    def command =
                    """
                    find "$JENKINS_BACKUP_DIR" -name "FULL-*" -maxdepth 1 -type d -print | sort | tail -n 1
                    """

                    def list = sh(script:"${command}", returnStdout:true)
                    list = list.replaceAll("\n", "")
                    println '==================================================='
                    println 'latest directory:' + list
                    println 'workspace =' + env.WORKSPACE
                    println '==================================================='

                    def workspace_backup_path = env.WORKSPACE + "/backup/buildmachine-1/"
                    println 'workspace_backup_path =' + workspace_backup_path
                    // ディレクトを作成
                    if(!fileExists(workspace_backup_path)) {
                        sh """mkdir -p $workspace_backup_path"""
                    }

                    // ワークスペースへのコピー及び、新規・差分の更新、コピー元にないファイルは削除
                    sh """
                    rsync -au --delete ${list}/* ${workspace_backup_path}
                    """
                }
            }
        }
        stage('gitへのコミット') {
            steps {
                script {
                    try {
                        def comment = "[chore][jenkins]定期バックアップ jenkins by buildmachine-1"
                            sh """
                            echo 'git add -A'
                            git add -A .
                            echo 'git commit -m $comment'
                            git commit -m "$comment"
                            git status
                            """
                    }
                    catch (Exception e) {
                        // NOTE: do nothing. 差分がない場合場合、コミットせずpushに失敗する??のでここの例外はみない
                    }
                }
            }
        }

        stage('git push') {
            steps {
                script {
                    sshagent(['pokepia_buildmachine-1']) {
                        sh "git push -u origin master"
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                // Job失敗時
                def emoji = ':alert:'
                def notify_message = '[FAILED]buildmachine-1:' + env.JOB_NAME + 'に失敗しました。' + '@here ' + emoji + '\n' + env.BUILD_URL
                def needNotify = params.failed_notify_channels != ''
                if (needNotify) {
                    slackSend channel: params.failed_notify_channels,
                    message: notify_message,
                    color: "danger"
                }
            }
        }
    }
}
