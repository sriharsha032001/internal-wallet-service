# Stage 1 — build the fat JAR
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# copy pom + wrapper first so dependency downloads are cached
# and only re-run when pom.xml actually changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2 — runtime only, no JDK needed
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
