FROM eclipse-temurin:21-jdk
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 기본 실행 명령은 비워두거나 기본값 설정 (docker-compose에서 덮어씀)
ENTRYPOINT ["java", "-jar", "/app.jar"]