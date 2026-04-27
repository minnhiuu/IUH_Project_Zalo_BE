FROM eclipse-temurin:21-jdk-alpine
RUN apk add --no-cache libc6-compat
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app.jar"]
