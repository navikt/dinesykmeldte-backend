# dinesykmeldte-backend
This project contains the application code and infrastructure for dinesykmeldte-backend

[![Deploy dinesykmeldte-kafka app to dev and prod](https://github.com/navikt/dinesykmeldte-backend/actions/workflows/kafka-prod.yml/badge.svg)](https://github.com/navikt/dinesykmeldte-backend/actions/workflows/kafka-prod.yml)
[![Deploy to dev and prod](https://github.com/navikt/dinesykmeldte-backend/actions/workflows/deploy.yml/badge.svg)](https://github.com/navikt/dinesykmeldte-backend/actions/workflows/deploy.yml)


## Technologies used
* Kotlin
* Ktor
* Gradle
* Kafka

#### Requirements

* JDK 17

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t dinesykmeldte-backend .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 dinesykmeldte-backend`


### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```

### Contact

This project is maintained by [navikt/teamsykmelding](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/dinesykmeldte-backend/issues).

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997).