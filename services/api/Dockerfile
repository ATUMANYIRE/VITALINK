FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system vitalink && useradd --system --gid vitalink --home-dir /app vitalink
COPY --from=build /workspace/target/vitalink-api-0.1.0.jar /app/api.jar
USER vitalink:vitalink
ENV API_BIND_ADDRESS=0.0.0.0 API_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70.0","-jar","/app/api.jar"]
