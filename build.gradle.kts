group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.9.0"
val jacksonVersion = "2.18.0"
val kluentVersion = "1.73"
val logbackVersion = "1.5.9"
val ktorVersion = "3.0.0"
val logstashEncoderVersion = "8.0"
val prometheusVersion = "0.16.0"
val smCommonVersion = "2.0.8"
val mockkVersion = "1.13.13"
val nimbusdsVersion = "9.41.2"
val hikariVersion = "6.0.0"
val flywayVersion = "10.19.0"
val postgresVersion = "42.7.4"
val testContainerVersion = "1.20.2"
val kotlinVersion = "2.0.21"
val swaggerUiVersion = "5.17.14"
val kotestVersion = "5.9.1"
val googlePostgresVersion = "1.21.0"
val googleOauthVersion = "1.36.0"
val ktfmtVersion = "0.44"
val kafkaVersion = "3.8.0"
val snappyJavaVersion = "1.1.10.7"
val commonsCodecVersion="1.17.1"
val commonsCompressVersion = "1.27.1"
val snakeYamlVersion = "2.3"

plugins {
    id("application")
    id("com.diffplug.spotless") version "6.25.0"
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.3"
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
        constraints {
            implementation("commons-codec:commons-codec:$commonsCodecVersion") {
                because("override transient from io.ktor:ktor-client-apache")
            }
        }
        constraints {
            implementation("org.yaml:snakeyaml:$snakeYamlVersion") {
                because("override transient from io.ktor:ktor-client-apach")
            }
        }
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

        swaggerUI("org.webjars:swagger-ui:$swaggerUiVersion")

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

