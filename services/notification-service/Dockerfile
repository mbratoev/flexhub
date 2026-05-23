# syntax=docker/dockerfile:1.7
# See services/account-service/Dockerfile for the pattern explanation.

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:notification-service:bootJar --no-daemon

RUN java -Djarmode=tools -jar services/notification-service/build/libs/notification-service-*.jar \
        extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/extracted/dependencies/         ./
COPY --from=builder /workspace/extracted/spring-boot-loader/   ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/          ./

EXPOSE 8083

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
