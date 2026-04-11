# 1단계: 빌드 스테이지
FROM gradle:jdk21 AS build
WORKDIR /app
COPY . .

# 빌드 인자로 메인 클래스를 받습니다 (기본값은 Gateway)
ARG MAIN_CLASS=com.veriq.veriqgateway.VeriQGatewayApplication

# 지정된 메인 클래스로 빌드하도록 명령
RUN gradle clean build -x test -PmainClass=${MAIN_CLASS}

# 2단계: 실행 스테이지
FROM eclipse-temurin:21-jdk
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app

# 빌드된 jar 파일 가져오기
COPY --from=build /app/build/libs/*.jar /app/app.jar

RUN chown -R appuser:appgroup /app
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]