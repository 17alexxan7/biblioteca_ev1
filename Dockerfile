FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:24-jre-alpine
ARG MODULE
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /app/${MODULE}/target/*.jar app.jar
USER app
ENTRYPOINT ["java","-jar","app.jar"]
