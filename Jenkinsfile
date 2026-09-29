pipeline {
    agent any
    // Agent prerequisites: Java 21, Node 22, Docker, and Chrome/Chromium.
    stages {
        stage('Checkout') { steps { checkout scm } }
        stage('Backend verification') {
            steps { dir('backend/rocket-trading') { sh './mvnw -B -ntp verify' } }
        }
        stage('Frontend verification') {
            steps { dir('frontend/rocket-trading-ui') {
                sh 'npm ci'
                sh 'npm run test:ci'
                sh 'npm run build'
            } }
        }
        stage('Browser verification') {
            steps {
                dir('frontend/rocket-trading-ui') { sh 'npx playwright install --with-deps chromium' }
                sh 'docker compose -p rocket-trading-e2e -f docker-compose.yml -f compose.e2e.yaml up --build -d'
                sh '''for attempt in $(seq 1 60); do
                    if [ "$(curl --silent -o /dev/null -w '%{http_code}' http://localhost:4200/api/v1/orders)" = "401" ]; then exit 0; fi
                    sleep 2
                done
                exit 1'''
                dir('frontend/rocket-trading-ui') { sh 'npm run test:e2e' }
            }
        }
    }
    post {
        always {
            sh 'docker compose -p rocket-trading-e2e -f docker-compose.yml -f compose.e2e.yaml down'
            junit allowEmptyResults: true, testResults: 'backend/rocket-trading/target/*-reports/TEST-*.xml'
        }
    }
}
