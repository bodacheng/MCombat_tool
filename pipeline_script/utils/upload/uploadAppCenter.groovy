pipeline {
    agent any

    environment {
        // groovy Files
        appcenterUtility = ''
    }

    stages {
        stage ('groovy準備') {
            steps {
                script {
                    // Git checkout before load source the file
                    checkout scm

                    // To know files are checked out or not
                    sh '''
                        ls -lhrt
                    '''

                    // load git utility
                    appcenterUtility = load "pipeline_script/utils/appcenterUtility.groovy"
                }
            }
        }
        stage('成果物コピー') {
            steps {
                copyArtifacts fingerprintArtifacts: true,
                projectName: params.copyArtifacts_ProjectName,
                target: params.target_filter_artifact,
                selector:specific(params.upstream_build_number)
            }
        }
        stage('AppCenterへのアップロード') {
            steps {
                script {
                    retry(2) {
                        println 'AppCenterへのアップロード parameters'
                        
                        println 'apiToken:' + params.APPCENTER_API_TOKEN
                        println 'ownerName:' + env.APPCENTER_OWNER
                        println 'appName:' + params.APP_NAME
                        println 'branchName:' + ''
                        println 'buildVersion:' + params.VERSION
                        println 'commitHash:' + ''
                        println 'distributionGroups:' + "${params.DISTRIBUTION_GROUPS}"
                        println 'mandatoryUpdate:' + false
                        println 'pathToApp:' + "${params.OUTPUT_DIR}/${params.APP_FILENAME}"
                        println 'pathToDebugSymbols:' + ''
                        println 'pathToReleaseNotes:' + ''
                        println 'releaseNotes:' + "ビルド${params.upstream_build_number} ${params.upstream_build_user} / RELEASE NOTE: ${params.RELEASENOTE}"
                                                
                        appCenter apiToken: params.APPCENTER_API_TOKEN,
                            ownerName: env.APPCENTER_OWNER,
                            appName: params.APP_NAME,
                            branchName: '',
                            buildVersion: params.VERSION,
                            commitHash: '',
                            distributionGroups: "${params.DISTRIBUTION_GROUPS}",
                            mandatoryUpdate: false,
                            notifyTesters: true,
                            pathToApp: "${params.OUTPUT_DIR}/${params.APP_FILENAME}",
                            pathToDebugSymbols: '',
                            pathToReleaseNotes: '',
                            releaseNotes: "ビルド${params.upstream_build_number} ${params.upstream_build_user} / RELEASE NOTE: ${params.RELEASENOTE}"
                            
                        //def releaseID = appcenterUtility.getReleaseId(env.APPCENTER_OWNER, params.APP_NAME, params.APPCENTER_API_TOKEN)
                        //def downloadURL = appcenterUtility.getDownloadURL(env.APPCENTER_OWNER, params.APP_NAME, releaseID)
                        //currentBuild.description = downloadURL
                    }
                }
            }
        }
    }
}
