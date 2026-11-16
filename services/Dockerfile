# One Dockerfile for all three services. Build from the repo root:
#
#   docker build -f services/Dockerfile --build-arg SERVICE=identity -t triplor/identity .
#
# Stage 1 compiles with a full JDK and Maven. Stage 2 splits the jar into layers. Stage 3 is
# what runs: a JRE, the layers, and nothing else. No compiler, no source, no build cache.

FROM eclipse-temurin:21-jdk AS build
ARG SERVICE
WORKDIR /src
COPY . .
# The cache mount keeps downloaded dependencies between builds without putting them in a layer.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -ntp -pl services/${SERVICE} -am package -DskipTests \
 && cp services/${SERVICE}/target/${SERVICE}-*.jar /application.jar

# Spring Boot's own tool unpacks the jar into layers ordered from rarely to often changing.
FROM eclipse-temurin:21-jre AS layers
WORKDIR /layers
COPY --from=build /application.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre
# A fixed, unprivileged user: a bug in the application cannot act as root in the container.
RUN useradd --system --uid 10001 --no-create-home app
WORKDIR /application
# One COPY per layer, least often changed first. A code change rebuilds only the last one,
# so pushing and pulling a new version moves a few hundred kilobytes, not the whole image.
COPY --from=layers /layers/extracted/dependencies/ ./
COPY --from=layers /layers/extracted/spring-boot-loader/ ./
COPY --from=layers /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers /layers/extracted/application/ ./
USER app
# The JVM sizes its heap from the container's memory limit, not the host's memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
