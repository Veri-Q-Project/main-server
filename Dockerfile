# 1단계: 빌드 스테이지 (Gradle 환경에서 직접 빌드)
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# 빌드에 필요한 파일들만 먼저 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# gradlew 실행 권한 부여 및 빌드 (clean을 포함해서 찌꺼기 제거!)
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

# 2단계: 실행 스테이지 (경량화된 이미지)
FROM eclipse-temurin:21-jdk
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app

# 1단계(build)에서 생성된 따끈따끈한 jar 파일만 쏙 가져오기
COPY --from=build /app/build/libs/*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]