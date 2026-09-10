# syntax=docker/dockerfile:1

# ---- build ----------------------------------------------------------------
# Pinned by digest-able tag rather than 'latest' so the toolchain is reproducible.
FROM gradle:8.14-jdk17 AS build
WORKDIR /home/gradle/src

# Copy the build definition first. Dependency resolution then caches as its own
# layer and only re-runs when the build files actually change, not on every edit.
COPY --chown=gradle:gradle settings.gradle build.gradle gradle.properties ./
RUN gradle --no-daemon -q dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY --chown=gradle:gradle src ./src
RUN gradle --no-daemon installDist

# ---- runtime --------------------------------------------------------------
# JRE only: the compiler, Gradle, and the dependency cache stay out of the
# shipped image, which cuts both size and attack surface.
FROM eclipse-temurin:17-jre AS runtime

RUN groupadd --system app && useradd --system --gid app --home-dir /app app

WORKDIR /app
COPY --from=build --chown=app:app /home/gradle/src/build/install/api ./

# Never run the service as root.
USER app

EXPOSE 8080

# JWT_SECRET is deliberately not defaulted here. Unset, the app generates an
# ephemeral key and warns; it must be provided explicitly for any real deployment.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["/app/bin/api"]
