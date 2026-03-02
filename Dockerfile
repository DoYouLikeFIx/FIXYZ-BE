FROM gradle:9.3.1-jdk21 AS builder
ARG SERVICE_MODULE
WORKDIR /workspace
COPY . .
RUN chmod +x ./gradlew \
    && ./gradlew :${SERVICE_MODULE}:bootJar --no-daemon \
    && cp ${SERVICE_MODULE}/build/libs/*.jar /tmp/app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=builder /tmp/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
