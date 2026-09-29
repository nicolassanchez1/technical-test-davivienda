# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
# Dependencies change far less often than sources, so resolve them in their own layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/storage && chown -R app:app /app
USER app
EXPOSE 8081
# MaxRAMPercentage lets the JVM respect the container limit instead of the host memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
