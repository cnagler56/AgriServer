# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml first (caches dependencies if code changes but pom doesn't)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Don't run as root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/*.jar app.jar

# WASDE machine-readable CSVs (Supply & Demand data). Read at runtime via
# WASDE_CSV_PATH=/app/wasde. Add a new month's CSV here and redeploy to update.
COPY --chown=spring:spring wasde ./wasde

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]