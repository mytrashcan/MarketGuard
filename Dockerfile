# 1) 빌드 스테이지 — Gradle 래퍼로 실행 가능한 jar 생성 (테스트는 제외)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# 의존성 캐시 최적화: 빌드 스크립트/래퍼 먼저 복사
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

# 소스 복사 후 jar 빌드 (.dockerignore가 build/·.gradle·시크릿 제외)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# 2) 런타임 스테이지 — 가벼운 JRE 이미지
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 5050
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
