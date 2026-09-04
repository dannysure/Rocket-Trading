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

        // stage('Build') {
        //     steps {
        //         echo 'Building the project'
        //     }
        // }

        // stage('Test') {
        //     steps {
        //         echo 'Running tests'
        //     }
        // }

        // stage('Deploy') {
        //     steps {
        //         echo 'Deploying the project'
        //     }
        // }
    
}