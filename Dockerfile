FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY --from=build /app/target/resitrack-backend-1.0.0.jar app.jar

# PORT is injected dynamically by Render at runtime — do not hardcode 8080
EXPOSE $PORT

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]