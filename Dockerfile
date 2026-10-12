# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Copy Gradle wrapper and build scripts first — this layer is cached
# and only re-runs when the build config changes, not on every code edit.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Now the source. Changing code invalidates only from here down.
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Split the built jar into layers (dependencies, app code, etc.)
RUN java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination extracted

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user, never as root.
RUN useradd --system --no-create-home --uid 1001 mneme
USER mneme

# Copy layers in order of change frequency: least-changing first,
# so Docker reuses cached layers when only app code changes.
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]