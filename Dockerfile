# Build stage
# Build context must be ./back (set in docker-compose.yml)
# so this Dockerfile can access both financial-app-parent/ and ms-gateway/
FROM eclipse-temurin:25-jdk AS build

WORKDIR /build

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Install parent POM to local Maven repository
COPY financial-app-parent/pom.xml financial-app-parent/pom.xml
RUN mvn -f financial-app-parent/pom.xml install -N -q

# Resolve dependencies (cached layer — only re-runs when pom.xml changes)
COPY ms-gateway/pom.xml ms-gateway/pom.xml
RUN mvn -f ms-gateway/pom.xml dependency:go-offline -q

# Build
COPY ms-gateway/src ms-gateway/src
RUN mvn -f ms-gateway/pom.xml clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /build/ms-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
