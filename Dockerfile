FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
# 빌드된 JAR를 app.jar로 복사 (파일명에 상관없이 자동 매칭)
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]