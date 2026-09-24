FROM eclipse-temurin:17-jdk-alpine AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S banking && adduser -S banking -G banking
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER banking

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
