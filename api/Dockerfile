# ---- Étape 1 : build (Maven + JDK, jetée après) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# On copie pom.xml seul d'abord : les dépendances restent en cache tant que le pom ne change pas
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# ---- Étape 2 : image finale (JRE seul, utilisateur non-root) ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
