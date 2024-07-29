FROM gcr.io/distroless/java17-debian11@sha256:c7846b62436ccf2961972fea5b776527610a1a51b48d8e7b434287146904cf2d
WORKDIR /app
COPY build/libs/app-*.jar app.jar
ENV JAVA_OPTS="-Dlogback.configurationFile=logback.xml"
ENV TZ="Europe/Oslo"
EXPOSE 8080
USER nonroot
CMD [ "app.jar" ]