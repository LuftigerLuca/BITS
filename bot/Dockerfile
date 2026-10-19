# Build context is the repository root: docker build -f bot/Dockerfile .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :bot:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/bot/build/install/bot .
ENTRYPOINT ["bin/bot"]
