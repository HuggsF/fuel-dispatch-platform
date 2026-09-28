# syntax=docker/dockerfile:1
# Multi-stage image for tracking-service (FND-2.3). Build context: repository root.
#   docker build -f tracking-service/Dockerfile -t tracking-service .

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY dispatch-service/pom.xml dispatch-service/
COPY tracking-service/pom.xml tracking-service/
COPY tracking-service/src tracking-service/src
# Tests run in ./mvnw verify and CI; the image build only packages the jar.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -pl tracking-service -am package -DskipTests \
    && cp tracking-service/target/tracking-service-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
