# 1단계: 빌드 스테이지 (Gradle 환경에서 직접 빌드)
# 1단계: 빌드 스테이지
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# 현재 폴더의 모든 파일을 도커 안으로 복사
COPY . .



RUN gradle clean build -x test

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jdk
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app

# 1단계에서 빌드된 jar 파일 가져오기
COPY --from=build /app/build/libs/*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]