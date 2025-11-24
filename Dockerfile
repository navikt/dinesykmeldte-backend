FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:086efc0e24d318f47e804fc58b0b1ad5356bc05d27f2c174955f13c9a1efa6a8
WORKDIR /app
COPY build/libs/app-*.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
