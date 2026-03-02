# syntax=docker/dockerfile:1.7
FROM gradle:9.3.1-jdk21 AS builder
ARG SERVICE_MODULE
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    chmod +x ./gradlew \
    && ./gradlew :${SERVICE_MODULE}:bootJar --no-daemon \
    && cp ${SERVICE_MODULE}/build/libs/*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl
COPY --from=builder /tmp/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
