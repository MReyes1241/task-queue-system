# Build stage
FROM gradle:8.5-jdk21 AS build

WORKDIR /app

# Copy Gradle wrapper and build files first (for caching)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user for security (IBM best practice)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy only the JAR from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check (Kubernetes/Docker compatibility)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]