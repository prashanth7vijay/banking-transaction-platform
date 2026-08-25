pipeline {
    agent none   // no default agent - each stage picks its own below

    stages {

        stage('Checkout') {
            agent any
            steps {
                echo "Checked out branch: ${env.BRANCH_NAME}"
                sh 'ls -la'   // sanity check: you should see backend/ and frontend/ listed
            }
        }

        stage('Backend: Build & Test') {
            agent {
                docker { image 'maven:3.9-eclipse-temurin-21' }
            }
            steps {
                dir('backend') {
                    sh 'mvn -B clean verify'
                }
            }
        }

        stage('Frontend: Build') {
            agent {
                docker { image 'node:20' }
            }
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm run build'
                }
            }
        }
    }

    post {
        success {
            echo 'Backend and frontend both built and tested successfully.'
        }
        failure {
            echo 'Something broke - check the stage logs above to see which one.'
        }
    }
}