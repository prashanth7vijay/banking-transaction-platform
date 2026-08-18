FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
RUN addgroup --system platform && adduser --system --ingroup platform platform
COPY --from=build /build/target/*.jar app.jar
USER platform
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
