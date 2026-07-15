# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e

FROM eclipse-temurin:17-jdk-jammy@sha256:723151f3fc88ca2060153ee08ab8dbbea7983d6ed6f2622fe440acf178737c94 AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod 0755 gradlew
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre-jammy@sha256:475d8e96b4b2bfe08999e5e854755c773af1581acdf959a4545d88f0696a2339
LABEL org.opencontainers.image.title="MarketGuard" \
      org.opencontainers.image.description="Read-only market-data anomaly detector" \
      org.opencontainers.image.source="https://github.com/mytrashcan/MarketGuard"

RUN groupadd --system --gid 10001 marketguard \
    && useradd --system --uid 10001 --gid marketguard --home-dir /app --shell /usr/sbin/nologin marketguard
WORKDIR /app
COPY --from=build --chown=marketguard:marketguard /workspace/build/libs/marketguard.jar /app/marketguard.jar

USER 10001:10001
EXPOSE 5050
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
    CMD curl --fail --silent --show-error http://127.0.0.1:5050/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-XX:+ExitOnOutOfMemoryError", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/marketguard.jar"]
