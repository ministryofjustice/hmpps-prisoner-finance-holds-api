# hmpps-prisoner-finance-holds-api

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-prisoner-finance-holds-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-prisoner-finance-holds-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-prisoner-finance-holds-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prisoner-finance-holds-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

## Pre-requisites

To be able to run this repo locally you will need the following software installed

- Docker + Docker Compose
    - The easiest way to do this is to install Docker Desktop which comes bundled with both
- Java Development Kit (JDK) 21
    - The JDK version must match the gradle expectations exactly
- Gradle
    - Running the application will usually download and install this for you
- IntelliJ Idea
    - The ultimate version requires a license from MoJ but licenses are limited but you can use the Community Edition (CE) without issue



## Instructions

### Project set up

Enable pre-commit hooks for formatting and linting code with the following command;

```bash
./gradlew addKtlintFormatGitPreCommitHook addKtlintCheckGitPreCommitHook
```



### Running unit tests

To run the unit tests use the command:

```bash
make unit-test
```

### Running integration tests

To run the integration tests, use the command:

```bash
make integration-test
```

## Coverage
### Show coverage in intellij
- ```bash
  make check
  ```
- Intellij -> Run -> Manage Coverage Reports
- Add the files in build/jacoco/
- Click OK. The coverage report will now appear in the Coverage Tool Window and the code will be highlighted in the editor.

### Open Coverage report in the browser
To visualize the reports in the browser:
- Build the project
- Open the `index.html` files in the folders under `build/reports/jacoco`



## Running the application locally

There a `docker-compose.yml` that can be used to run a local instance in docker and also an
instance of HMPPS Auth.

```bash
make serve
```

will build the application and run it and HMPPS Auth within a local docker instance.

To verify the app has started,
1. ensure the containers are visible (and running) in Docker, and
2. visit http://localhost:8080/health ensuring the result contains "status: UP"


### Running the application in Intellij

```bash
make serve-environment
```

will just start a docker instance of HMPPS Auth. The application should then be started with
a `dev` active profile in Intellij.

### API Documentation
Is available on a running local server at http://localhost:8080/swagger-ui/index.html#/

### Generating an auth token
- Use this command to request a local auth token:
  ```bash
  curl -X POST "http://localhost:8090/auth/oauth/token?grant_type=client_credentials" -H 'Content-Type: application/json' -H "Authorization: Basic $(echo -n hmpps-prisoner-finance-holds-api:clientsecret | base64)"
  ```

- The response body will contain an access token something like this:

  ```json
  {
    "access_token": "eyJhbGciOiJSUzI1NiIsInR5...BAtWD653XpCzn8A",
    "token_type": "bearer",
    "expires_in": 3599,
    "scope": "read write",
    "sub": "hmpps-prisoner-finance-holds-api"        
  }
  ```
- Use the value of `access_token` as a Bearer Token to authenticate when calling the local API endpoints.

### Health Checks
- `/health`: provides information about the application health and its dependencies.
- `/info`: provides information about the version of deployed application.

