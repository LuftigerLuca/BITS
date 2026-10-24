# Build context is the repository root: docker build -f backend/Dockerfile .
FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre@sha256:227118764cccd3c3294617061aaa93a08309c63a395eefa83e6b35032992ea99
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
EXPOSE 8080
# nobody: the app writes nowhere but /tmp, so it needs no user of its own, and never runs as root, also locally.
USER 65534:65534
ENTRYPOINT ["java", "-jar", "app.jar"]
