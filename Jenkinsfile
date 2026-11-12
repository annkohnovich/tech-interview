// Declarative Jenkins pipeline for both services.
//
// Each stage is a QUALITY GATE: if it fails, later stages never run and
// nothing gets deployed. Order = cheapest/fastest feedback first.
//
//   1. Unit + contract (consumer)   flight-service   mvn test
//   2. Contract (provider)          airport-service  mvn test  (replays pacts/)
//   3. Integration + coverage gate  flight-service   mvn verify (Failsafe + JaCoCo check)
//
// Requires on the Jenkins agent: JDK 17 and Maven configured under
// Manage Jenkins -> Tools with the names below. Plugins: Pipeline, JUnit,
// (optional) Allure Jenkins Plugin, JaCoCo or Coverage plugin.

pipeline {
    agent any

    tools {
        jdk 'jdk17'
        maven 'maven3'
    }

    options {
        timeout(time: 20, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {
        stage('Unit + contract tests (consumer)') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'pacts/*.json', allowEmptyArchive: false
                }
            }
        }

        stage('Contract verification (provider)') {
            steps {
                dir('airport-service') {
                    sh 'mvn -B test'
                }
            }
            post {
                always {
                    junit 'airport-service/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration tests + coverage gate') {
            steps {
                // -DskipTests would also skip ITs; unit tests re-run here, which is cheap.
                sh 'mvn -B verify'
            }
            post {
                always {
                    junit 'target/failsafe-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                    // With the Allure Jenkins Plugin installed:
                    // allure results: [[path: 'target/allure-results']]
                }
            }
        }
    }

    post {
        failure {
            echo 'Quality gate failed - see the first red stage.'
        }
    }
}
