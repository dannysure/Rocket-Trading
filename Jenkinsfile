pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    parameters {
        booleanParam(
            name: 'RUN_COMPOSE_SMOKE',
            defaultValue: false,
            description: 'Build the Docker Compose stack and check API/UI health endpoints'
        )
    }

    environment {
        COMPOSE_PROJECT_NAME = "rocket-trading-ci-${BUILD_NUMBER}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Backend verification') {
            steps {
                dir('backend/rocket-trading') {
                    sh './mvnw -B -ntp verify'
                }
            }
        }

        stage('Frontend verification') {
            steps {
                dir('frontend') {
                    sh 'npm ci'
                    sh 'npm test -- --watch=false'
                    sh 'npm run build'
                }
            }
        }

        stage('Python verification') {
            steps {
                sh '''
                    python3 -m venv .ci-venv
                    . .ci-venv/bin/activate
                    python -m pip install --upgrade pip
                    python -m pip install -r requirements.txt
                    python -m pytest -q tests
                '''
            }
        }

        stage('Compose smoke') {
            when {
                expression { params.RUN_COMPOSE_SMOKE }
            }
            steps {
                sh '''
                    docker compose -p "$COMPOSE_PROJECT_NAME" \
                        -f docker-compose.yml -f compose.e2e.yaml config --quiet
                    docker compose -p "$COMPOSE_PROJECT_NAME" \
                        -f docker-compose.yml -f compose.e2e.yaml up --build -d

                    for attempt in $(seq 1 60); do
                        api_status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:8081/health || true)
                        ui_status=$(curl --silent --output /dev/null --write-out '%{http_code}' http://localhost:4200/ || true)
                        if [ "$api_status" = '200' ] && [ "$ui_status" = '200' ]; then
                            exit 0
                        fi
                        sleep 2
                    done

                    docker compose -p "$COMPOSE_PROJECT_NAME" \
                        -f docker-compose.yml -f compose.e2e.yaml ps
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true,
                testResults: 'backend/rocket-trading/target/*-reports/TEST-*.xml'
            archiveArtifacts allowEmptyArchive: true,
                artifacts: 'backend/rocket-trading/target/*-reports/**'
            script {
                if (params.RUN_COMPOSE_SMOKE) {
                    sh '''
                        docker compose -p "$COMPOSE_PROJECT_NAME" \
                            -f docker-compose.yml -f compose.e2e.yaml down \
                            --volumes --remove-orphans || true
                    '''
                }
            }
        }
    }
}