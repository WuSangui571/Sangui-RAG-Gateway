FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY settings.xml /root/.m2/settings.xml
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN apk add --no-cache curl
RUN adduser -D -h /app sangui
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/data/uploads && chown -R sangui:sangui /app
EXPOSE 8080
USER sangui
ENTRYPOINT ["java", "-jar", "app.jar"]
