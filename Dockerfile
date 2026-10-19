# Build stage: dependencies are resolved in their own layer, so code-only changes rebuild fast.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: JRE only.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/shoppy-backend.jar app.jar

# Tuned for small free-tier containers (512 MB): capped heap, serial GC, C1-only JIT.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

# The app listens on $PORT (Render sets it); 8080 is only the local default for `docker run`.
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
