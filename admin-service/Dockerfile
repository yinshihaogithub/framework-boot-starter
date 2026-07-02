FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl admin-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/admin-service/target/admin-service.jar /app/admin-service.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/admin-service.jar"]
