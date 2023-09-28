group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.7.3"
val jacksonVersion = "2.15.2"
val kluentVersion = "1.73"
val logbackVersion = "1.4.11"
val ktorVersion = "2.3.4"
val logstashEncoderVersion = "7.4"
val prometheusVersion = "0.16.0"
val smCommonVersion = "2.0.2"
val mockkVersion = "1.13.8"
val nimbusdsVersion = "9.35"
val hikariVersion = "5.0.1"
val flywayVersion = "9.22.2"
val postgresVersion = "42.6.0"
val testContainerVersion = "1.19.0"
val kotlinVersion = "1.9.10"
val sykepengesoknadKafkaVersion = "2023.09.18-14.15-7ea2dc46"
val swaggerUiVersion = "5.7.2"
val kotestVersion = "5.7.2"
val googlePostgresVersion = "1.14.0"
val googleOauthVersion = "1.34.1"
val ktfmtVersion = "0.44"


plugins {
    id("application")
    id("com.diffplug.spotless") version "6.21.0"
    kotlin("jvm") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
}

application {
    mainClass.set("no.nav.syfo.BootstrapKt")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
        implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
        implementation("io.prometheus:simpleclient_common:$prometheusVersion")

        implementation("io.ktor:ktor-server-core:$ktorVersion")
        implementation("io.ktor:ktor-server-netty:$ktorVersion")
        implementation("io.ktor:ktor-server-auth:$ktorVersion")
        implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-server-cors:$ktorVersion")
        implementation("io.ktor:ktor-server-call-id:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-apache:$ktorVersion")
        implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

        implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
        implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
        implementation("no.nav.helse.flex:sykepengesoknad-kafka:$sykepengesoknadKafkaVersion")

        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("com.zaxxer:HikariCP:$hikariVersion")
        implementation("org.flywaydb:flyway-core:$flywayVersion")
        implementation("org.postgresql:postgresql:$postgresVersion")
        implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion")
        implementation("com.google.oauth-client:google-oauth-client:$googleOauthVersion")

        swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")

        testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
        testImplementation("org.amshove.kluent:kluent:$kluentVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.testcontainers:postgresql:$testContainerVersion")
        testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusdsVersion")
        testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
        testImplementation("io.ktor:ktor-server-test-host:$ktorVersion") {
            exclude(group = "org.eclipse.jetty")
        }
        testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
        testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
        testImplementation("io.kotest:kotest-property:$kotestVersion")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    swaggerSources {
        create("dinesykmeldte-backend").apply {
            setInputFile(file("api/oas3/dinesykmeldte-backend-api.yaml"))
        }
    }

    tasks {

        generateSwaggerUI {
            val output: Provider<Directory> = layout.buildDirectory.dir("/resources/main/api")
            outputDir = output.get().asFile
            dependsOn("jar")
        }

        shadowJar {
            archiveBaseName.set("app")
            archiveClassifier.set("")
            isZip64 = true
            manifest {
                attributes(
                   mapOf(
                        "Main-Class" to "no.nav.syfo.BootstrapKt",
                    ),
                )
            }
        }



        test {
            useJUnitPlatform {
            }
            testLogging {
                events("skipped", "failed")
                showStackTraces = true
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }

        spotless {
            kotlin { ktfmt(ktfmtVersion).kotlinlangStyle() }
            check {
                dependsOn("spotlessApply")
            }
        }
    }

}

