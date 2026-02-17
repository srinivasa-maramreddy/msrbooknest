FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml pom.xml
COPY enterprise-bookstore/pom.xml enterprise-bookstore/pom.xml
COPY enterprise-bookstore/src enterprise-bookstore/src

RUN mvn -pl enterprise-bookstore -am clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/enterprise-bookstore/target/enterprise-bookstore-1.0.0.jar app.jar

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
