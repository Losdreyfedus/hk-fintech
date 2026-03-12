FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY pom.xml ./pom.xml
COPY hk-common-lib/pom.xml ./hk-common-lib/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY identity-service/pom.xml ./identity-service/pom.xml
COPY invoice-service/pom.xml ./invoice-service/pom.xml
COPY card-service/pom.xml ./card-service/pom.xml
COPY wallet-service/pom.xml ./wallet-service/pom.xml

COPY .mvn .mvn
COPY mvnw ./mvnw
RUN chmod +x mvnw
RUN ./mvnw install -N -B

RUN ./mvnw dependency:go-offline -pl hk-common-lib,payment-service -am -B

COPY hk-common-lib/src ./hk-common-lib/src
COPY payment-service/src ./payment-service/src

RUN ./mvnw package -pl hk-common-lib,payment-service -am -DskipTests -B


FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/payment-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 9093

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
