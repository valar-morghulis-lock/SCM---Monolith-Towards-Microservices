FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root security
RUN addgroup -S scmgroup && adduser -S scmuser -G scmgroup
USER scmuser

# Copy the JAR built on your Windows host
COPY target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]