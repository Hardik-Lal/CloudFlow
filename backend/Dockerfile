# syntax=docker/dockerfile:1

# Official Gradle image matching the wrapper version (gradle/wrapper/gradle-wrapper.properties),
# so the build does not download a Gradle distribution.
FROM gradle:9.7.1-jdk21-alpine AS build
WORKDIR /workspace
COPY settings.gradle build.gradle ./
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle --no-daemon dependencies > /dev/null
COPY src src
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle --no-daemon bootJar -x test \
    && java -Djarmode=tools -jar build/libs/*-SNAPSHOT.jar extract --layers --launcher --destination build/extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S cloudflow && adduser -S cloudflow -G cloudflow
WORKDIR /app
COPY --from=build /workspace/build/extracted/dependencies/ ./
COPY --from=build /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/build/extracted/application/ ./
USER cloudflow
EXPOSE 8080 8081
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8081/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
