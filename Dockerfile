# Multi-stage Dockerfile: build with Maven and run with JRE
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace

# copy only what's necessary to leverage Docker cache
COPY pom.xml mvnw .
COPY .mvn .mvn
COPY src src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
