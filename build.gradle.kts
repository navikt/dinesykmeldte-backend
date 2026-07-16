group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.11.0"
val jacksonVersion = "2.22.1"
val kluentVersion = "1.73"
val logbackVersion = "1.5.38"
val ktorVersion = "3.5.1"
val logstashEncoderVersion = "9.0"
val prometheusVersion = "0.16.0"
val mockkVersion = "1.14.11"
val nimbusdsVersion = "10.9.1"
val hikariVersion = "7.1.0"
val flywayVersion = "12.10.0"
val postgresVersion = "42.7.13"
val testContainerVersion = "1.21.4"
val kotlinVersion = "2.4.0"
val swaggerUiVersion = "5.32.8"
val kotestVersion = "6.2.2"
val googlePostgresVersion = "1.28.6"
val googleOauthVersion = "1.39.0"
val kafkaVersion = "3.9.2"
val koinVersion = "4.2.2"
// Due to vulnerabilities
val nettyCodecHttpVersion = "4.2.16.Final"
val snappyJavaVersion = "1.1.10.8"
val commonsCompressVersion = "1.28.0"

plugins {
    id("application")
    kotlin("jvm") version "2.3.21"
    id("com.gradleup.shadow") version "9.5.1"
    id("org.hidetake.swagger.generator") version "2.19.2" apply true
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
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
            implementation("io.netty:netty-codec-http:$nettyCodecHttpVersion") {
                because("Due to vulnerabilities in netty pulled in by ktor 3.4.x")
            }
        }
        implementation("io.ktor:ktor-server-auth:$ktorVersion")
        implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
        implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
        implementation("io.ktor:ktor-server-cors:$ktorVersion")
        implementation("io.ktor:ktor-server-call-id:$ktorVersion")
        implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
        implementation("io.ktor:ktor-client-core:$ktorVersion")
        implementation("io.ktor:ktor-client-apache5:$ktorVersion")

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
        implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
        compileOnly("org.flywaydb:flyway-core:$flywayVersion")
        implementation("org.postgresql:postgresql:$postgresVersion")
        implementation("com.google.cloud.sql:postgres-socket-factory:$googlePostgresVersion")
        implementation("com.google.oauth-client:google-oauth-client:$googleOauthVersion")
        implementation("io.insert-koin:koin-ktor:$koinVersion")
        implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

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
            filesMatching("META-INF/services/**") {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
            mergeServiceFiles()
            archiveBaseName.set("app")
            archiveClassifier.set("")
            isZip64 = true
            manifest {
                attributes(
                    mapOf(
                        "Main-Class" to "no.nav.syfo.ApplicationKt",
                        "Multi-Release" to "true",
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
        named("check") {
            dependsOn("ktlintCheck")
        }
    }
}
