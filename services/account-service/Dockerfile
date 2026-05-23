# syntax=docker/dockerfile:1.7
#
# Multi-stage build with Spring Boot layered jars.
#
# Builder stage: builds the bootJar using the project's gradle wrapper, then extracts
# the jar into per-layer directories (dependencies / spring-boot-loader /
# snapshot-dependencies / application). Each layer becomes its own Docker layer in the
# runtime image — only the small `application` layer rebuilds when Java code changes.
#
# Runtime stage: a slim JRE image (~250 MB) with each extracted layer COPY'd in.
# No build tools, no source, no test artefacts.
#
# Build context is the REPO ROOT (`docker compose build account-service` from /spring_demo
# or `docker build -f services/account-service/Dockerfile .`). The gradle wrapper and
# multi-module setup need the full project visible to resolve libs:events.

# ------------------------- Builder -------------------------
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY . .

# BuildKit cache mount keeps the gradle dependency cache between builds —
# turns a 90 s rebuild into a 5 s rebuild on incremental code changes.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:account-service:bootJar --no-daemon

# Spring Boot 3.3+ `tools` mode extracts layers + launcher in one step.
RUN java -Djarmode=tools -jar services/account-service/build/libs/account-service-*.jar \
        extract --layers --launcher --destination /workspace/extracted

# ------------------------- Runtime -------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy layers least-changing first so Docker layer cache works optimally.
COPY --from=builder /workspace/extracted/dependencies/         ./
COPY --from=builder /workspace/extracted/spring-boot-loader/   ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/          ./

EXPOSE 8081

# JarLauncher uses the layered classpath manifest the extract step produced.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
