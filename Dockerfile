FROM gcr.io/distroless/java17-debian11@sha256:587ce66b08faea2e2e1568d6bb6c5fd6b085909621f4c14762206d687ff7d202
WORKDIR /app
COPY build/libs/app-*.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]