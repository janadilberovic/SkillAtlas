# --- 1. Angular ------------------------------------------------------------
# Node lives only in this stage; the final image never sees npm or node_modules.
FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- 2. Spring Boot --------------------------------------------------------
# The maven image rather than ./mvnw: the wrapper is distributionType=only-script with no committed
# jar, so it would only add a download.
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
# This is what makes one artifact out of two: the SPA is served from the jar (see SpaWebConfig).
COPY --from=frontend /build/dist/skillatlas-nocturne/browser/ ./src/main/resources/static/
# Not `verify`: the *IT tests need a live Neo4j and Azurite. CI is the test gate, not the image build.
RUN mvn -B -ntp package -DskipTests

# --- 3. Runtime ------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=backend /build/target/skillatlas-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
