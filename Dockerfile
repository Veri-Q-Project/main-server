# 1. 빌드된 JAR 파일을 실행할 환경 설정
FROM eclipse-temurin:21-jdk-jammy

# 2. 작업 디렉토리 설정
WORKDIR /app

# 3. 빌드된 jar 파일을 컨테이너 내부로 복사
# (build/libs/*.jar 파일 이름을 확인해서 넣어주세요)
COPY build/libs/*.jar app.jar

# 4. 앱 실행
ENTRYPOINT ["java", "-jar", "app.jar"]