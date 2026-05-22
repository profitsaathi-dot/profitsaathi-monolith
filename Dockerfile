# Multi-stage build keeps the runtime image small.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
RUN groupadd -g 10001 appgroup && useradd -u 10001 -g appgroup -m appuser
WORKDIR /app
COPY --from=build --chown=appuser:appgroup /workspace/target/profitsaathi-monolith-*.jar app.jar
ENV LOG_FORMAT=json \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9090/actuator/health/liveness || exit 1
USER 10001
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]