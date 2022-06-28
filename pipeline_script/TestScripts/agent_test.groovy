pipeline {
    agent {
        label "TestBuild_JobNode || TestBuild_JobNode2"
    }

    stages {
        stage ('ノードどこで走る') {
            steps {
                script {
                    println "ノード名：${env.NODE_NAME}"
                    currentBuild.description = "ノード名：${env.NODE_NAME}"
                }
            }
        }
    }
}
