FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21@sha256:b6e101c54501b27c39c47541e2d9f73c502be74c42ff4ba12c4d13972d0eff83
WORKDIR /app
COPY build/libs/app-*.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
