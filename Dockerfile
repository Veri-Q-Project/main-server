FROM eclipse-temurin:21-jdk
RUN groupadd -r appgroup && useradd -r -g appgroup appuser
WORKDIR /app
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar
RUN chown -R appuser:appgroup /app
USER appuser
ENTRYPOINT ["java", "-jar", "/app/app.jar"]