# Multi-stage Dockerfile: build with Maven and run with JRE
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

# copy only what's necessary to leverage Docker cache
COPY pom.xml .
COPY src src

RUN mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENV JAVA_OPTS="-Xms256m -Xmx768m"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
