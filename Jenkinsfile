pipeline {
    agent any
    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo 'Repository cloned successfully'
            }
        }
    }

    post {
        success {
            echo 'Pipeline executed successfully'
        }
    }
}