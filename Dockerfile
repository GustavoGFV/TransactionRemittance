# syntax=docker/dockerfile:1

# ---- build: compile and run the unit tests (coverage is collected by JaCoCo) ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Integration tests need a Docker daemon (Testcontainers), so they run in CI, not inside the image build.
RUN ./mvnw -B verify -DskipITs

# ---- quality: SonarQube analysis and quality gate. The runtime image is built FROM this stage's jar,
# so a failed gate stops the build. Without SONAR_HOST_URL the analysis is skipped (local builds).
#   docker build --build-arg SONAR_HOST_URL=http://host.docker.internal:9000 \
#     --secret id=sonar_token,env=SONAR_TOKEN -t remittance .
FROM build AS quality
ARG SONAR_HOST_URL=""
ARG SONAR_PROJECT_KEY="inter-remittance"
RUN --mount=type=secret,id=sonar_token \
    if [ -n "$SONAR_HOST_URL" ]; then \
      SONAR_TOKEN="$(cat /run/secrets/sonar_token)" ./mvnw -B -DskipTests sonar:sonar \
        -Dsonar.host.url="$SONAR_HOST_URL" \
        -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
        -Dsonar.qualitygate.wait=true; \
    else \
      echo "SONAR_HOST_URL not set: skipping SonarQube analysis"; \
    fi

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=quality /workspace/target/*.jar app.jar
USER app
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
