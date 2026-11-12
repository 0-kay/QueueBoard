# Build stage: compile and package the Spring Boot jar with the project's Gradle wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first, so the Gradle download and dependencies are cached
# as a layer and only re-fetched when these files change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null

COPY src src
# Tests run with ./gradlew test outside the image; skipping them keeps the image build fast.
RUN ./gradlew bootJar --no-daemon -q

# Run stage: just the JRE and the jar.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 queueboard
COPY --from=build /workspace/build/libs/*.jar app.jar
USER queueboard

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
