FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
