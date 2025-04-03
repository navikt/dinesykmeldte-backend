group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.10.1"
val jacksonVersion = "2.18.3"
val kluentVersion = "1.73"
val logbackVersion = "1.5.18"
val ktorVersion = "3.1.2"
val logstashEncoderVersion = "8.0"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.13.17"
val nimbusdsVersion = "10.1"
val hikariVersion = "6.3.0"
val flywayVersion = "11.6.0"
val postgresVersion = "42.7.5"
val testContainerVersion = "1.20.6"
val kotlinVersion = "2.1.20"
val swaggerUiVersion = "5.20.3"
val kotestVersion = "5.9.1"
val googlePostgresVersion = "1.24.1"
val googleOauthVersion = "1.39.0"
val ktfmtVersion = "0.44"
val kafkaVersion = "3.9.0"
val koinVersion = "4.0.4"
val kotestKoinVersion = "1.3.0"
///Due to vulnerabilities
val nettycommonVersion = "4.2.0.Final"
val snappyJavaVersion = "1.1.10.7"
val commonsCompressVersion = "1.27.1"

plugins {
    id("application")
    id("com.diffplug.spotless") version "7.0.2"
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.6"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
}

application {
    mainClass.set("no.nav.syfo.ApplicationKt")
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
        constraints {
            implementation("io.netty:netty-common:$nettycommonVersion") {
                because("Due to vulnerabilities in io.ktor:ktor-server-netty")
            }
        }
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

        implementation("org.apache.kafka:kafka_2.12:$kafkaVersion")
        constraints {
            implementation("org.xerial.snappy:snappy-java:$snappyJavaVersion") {
                because("override transient from org.apache.kafka:kafka_2.12")
            }
        }

        implementation("ch.qos.logback:logback-classic:$logbackVersion")
        implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
        implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

        implementation("com.zaxxer:HikariCP:$hikariVersion")
        compileOnly("org.flywaydb:flyway-core:$flywayVersion")
        implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
        implementation("org.postgresql:postgresql:$postgresVersion")
        implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion")
        implementation("com.google.oauth-client:google-oauth-client:$googleOauthVersion")
        implementation("io.insert-koin:koin-ktor:$koinVersion")
        implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")
        implementation("io.kotest.extensions:kotest-extensions-koin:$kotestKoinVersion")

        swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")
        testImplementation("io.insert-koin:koin-test:$koinVersion")
        testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
        testImplementation("org.amshove.kluent:kluent:$kluentVersion")
        testImplementation("io.mockk:mockk:$mockkVersion")
        testImplementation("org.testcontainers:postgresql:$testContainerVersion")
        constraints {
            implementation("org.apache.commons:commons-compress:$commonsCompressVersion") {
                because("Due to vulnerabilities from org.testcontainers:postgresql")
            }
        }
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
            mergeServiceFiles {
                setPath("META-INF/services/org.flywaydb.core.extensibility.Plugin")
            }
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

