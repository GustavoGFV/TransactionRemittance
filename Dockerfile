# syntax=docker/dockerfile:1

# ---- build: compile, run the unit tests and package (coverage is collected by JaCoCo) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Integration tests need a Docker daemon (Testcontainers), so they run in CI, not inside the image build.
RUN ./mvnw -B verify -DskipITs

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
# Numeric UID/GID so Kubernetes can verify runAsNonRoot
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER 10001:10001
EXPOSE 8080

# G1 with a pause goal keeps latency predictable. The heap is a percentage of the container limit,
# so the JVM never grows past the memory cgroup; min = max avoids heap resizing pauses under load.
# Override with JAVA_OPTS at runtime.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
 -XX:InitialRAMPercentage=75.0 -XX:MaxRAMPercentage=75.0 \
 -XX:+AlwaysPreTouch -XX:+DisableExplicitGC \
 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError \
 -Xlog:gc:stdout:time,uptime,level,tags \
 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
