# ---- Build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/bt-actividades-1.0.0.jar app.jar
# Render inyecta la variable PORT; la app la lee con server.port=${PORT:3000}
EXPOSE 3000
ENTRYPOINT ["java", "-jar", "app.jar"]
