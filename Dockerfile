# 1단계: 빌드 스테이지 (Gradle 버전을 최신으로 올립니다)
FROM gradle:jdk21 AS build
WORKDIR /app

# 모든 소스 파일 복사
COPY . .

# 설치된 최신 버전의 gradle로 빌드
RUN gradle clean build -x test

# 2단계: 실행 스테이지 (이후는 동일)
FROM eclipse-temurin:21-jdk
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app
COPY --from=build /app/build/libs/*.jar /app/app.jar
RUN chown -R appuser:appgroup /app
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]