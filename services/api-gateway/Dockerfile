# syntax=docker/dockerfile:1.7
# See services/account-service/Dockerfile for the pattern explanation.

FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace
COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :services:api-gateway:bootJar --no-daemon

RUN java -Djarmode=tools -jar services/api-gateway/build/libs/api-gateway-*.jar \
        extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /workspace/extracted/dependencies/         ./
COPY --from=builder /workspace/extracted/spring-boot-loader/   ./
COPY --from=builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/extracted/application/          ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
