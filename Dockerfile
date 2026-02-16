# =============================================================================
# Multi-stage Docker Build
# Stage 1 (builder): Compiles the application using Gradle
# Stage 2 (runtime): Minimal JRE image for production deployment
# Multi-stage builds reduce final image size significantly (~400MB -> ~200MB)
# by excluding build tools, source code, and intermediate artifacts.
# =============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Gradle wrapper and config first for Docker layer caching.
# Dependencies are downloaded only when build.gradle changes, not on every code change.
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle/ gradle/

# Download dependencies (cached layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Install curl for Docker healthcheck (eclipse-temurin JRE image does not include it)
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy only the built JAR from the builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Expose application port
EXPOSE 8080

# JVM flags optimized for containerized environments:
# -XX:+UseContainerSupport: Respects container memory/CPU limits
# -XX:MaxRAMPercentage=75: Uses up to 75% of container memory for heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
