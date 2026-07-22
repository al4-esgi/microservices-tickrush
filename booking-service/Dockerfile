# --- Étape build : Maven + JDK 21 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
ARG OTEL_JAVAAGENT_VERSION=2.28.1
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package
RUN mvn -B -q dependency:copy \
    -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_JAVAAGENT_VERSION} \
    -DoutputDirectory=/otel

# --- Étape runtime : JRE 21 seul (image plus légère) ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /otel/opentelemetry-javaagent-*.jar opentelemetry-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
