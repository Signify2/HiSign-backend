# Stage 1: Build bootable WAR
FROM eclipse-temurin:17-jdk-jammy AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x gradlew \
    && ./gradlew bootWar -x test --no-daemon

# Stage 2: Run application
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir -p /app/uploads/signatures /app/uploads/documents /app/config \
    && chown -R app:app /app

COPY --from=builder /app/build/libs/hisign_1-0.0.1.war app.war

USER app

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.war"]
