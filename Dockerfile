# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER app
EXPOSE 8080

# G1 with a pause goal keeps latency predictable; heap is a percentage of the container limit
# so the JVM never grows past the memory cgroup. Override with JAVA_OPTS at runtime.
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 \
 -XX:InitialRAMPercentage=50.0 -XX:MaxRAMPercentage=75.0 \
 -XX:+UseStringDeduplication -XX:+ExitOnOutOfMemoryError \
 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
