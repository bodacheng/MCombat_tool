pipeline {
    agent {
        label "master"
    }

    stages {
        stage("parallel") {
            parallel {
                stage ('ノードどこで走る') {
                    agent {
                        label "TestBuild_JobNode"
                    }
                    steps {
                        script {
                            println "ノード名：${env.NODE_NAME}"
                            currentBuild.description = "ノード名：${env.NODE_NAME}"
                        }
                    }
                }
                stage ('ノードどこで走る2') {
                    agent {
                        label "TestBuild_JobNode2"
                    }
                    steps {
                        script {
                            println "ノード名：${env.NODE_NAME}"
                            currentBuild.description = "ノード名：${env.NODE_NAME}"
                        }
                    }
                }
            }
        }
    }
}
