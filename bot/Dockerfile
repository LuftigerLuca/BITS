# Build context is the repository root: docker build -f bot/Dockerfile .
FROM eclipse-temurin:21-jdk@sha256:ebfbb18d11ff163ee83b21258acf12248af48d22a0fdfdc68fa1a1516c05976a AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :bot:installDist --no-daemon

FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
WORKDIR /app
COPY --from=build /workspace/bot/build/install/bot .
# The commit it was built from, shown on the page "System-Status"; the CD passes it.
ARG BITS_VERSION=dev
ENV BITS_VERSION=$BITS_VERSION
# nobody: the app writes nowhere but /tmp, so it needs no user of its own, and never runs as root, also locally.
USER 65534:65534
ENTRYPOINT ["bin/bot"]
