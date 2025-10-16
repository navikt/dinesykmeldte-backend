# dinesykmeldte-backend
This project contains the application code and infrastructure for dinesykmeldte-backend

## Technologies used
* Kotlin
* Ktor
* Gradle
* Kafka

#### Requirements

* JDK 21

## Getting started
### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`


### Upgrading the gradle wrapper
Find the newest version of gradle here: https://gradle.org/releases/ Then run this command:

```./gradlew wrapper --gradle-version $gradleVersjon```

### Contact

This project is maintained by [navikt/team-esyfo](CODEOWNERS)

Questions and/or feature requests? Please create an [issue](https://github.com/navikt/dinesykmeldte-backend/issues)

If you work in [@navikt](https://github.com/navikt) you can reach us at the Slack
channel [#team-sykmelding](https://nav-it.slack.com/archives/CMA3XV997)

## Docker compose
### Size of container platform
In order to run kafka++ you will probably need to extend the default size of your container platform. (Rancher Desktop, Colima etc.)

Suggestion for Colima
```bash
colima start --arch aarch64 --memory 8 --cpu 4 
```

We have a docker-compose.yml file to run a postgresql database, texas and a fake authserver.
In addition, we have a docker-compose.kafka.yml that will run a kafka broker, schema registry and kafka-io

Start them both using
```bash
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  up \
  db authserver texas broker kafka-ui \
  -d
```
Stop them all again
```bash
docker-compose \
  -f docker-compose.yml \
  -f docker-compose.kafka.yml \
  down
```

### Kafka-ui
You can use [kafka-ui](http://localhost:9000) to inspect your consumers and topics. You can also publish or read messages on the topics
