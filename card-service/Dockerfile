# =====================================================
# STAGE 1: BUILD - Maven ile uygulama derleme aşaması
# =====================================================
# NOT: card-service root pom.xml'de modül olarak tanımlı değil.
# Bu yüzden hk-common-lib'i önce bağımsız olarak install edip,
# ardından card-service'i ayrı bir adımda derliyoruz.
# =====================================================
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

# 1) pom.xml dosyalarını kopyala
COPY pom.xml ./pom.xml
COPY hk-common-lib/pom.xml ./hk-common-lib/pom.xml
COPY card-service/pom.xml ./card-service/pom.xml
COPY identity-service/pom.xml ./identity-service/pom.xml
COPY invoice-service/pom.xml ./invoice-service/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY wallet-service/pom.xml ./wallet-service/pom.xml

# 2) Maven wrapper
COPY .mvn .mvn
COPY mvnw ./mvnw
RUN chmod +x mvnw
RUN ./mvnw install -N -B

# 3) hk-common-lib'i local repo'ya install et
COPY hk-common-lib/src ./hk-common-lib/src
RUN ./mvnw install -pl hk-common-lib -B -DskipTests

# 4) card-service bağımlılıklarını indir
RUN ./mvnw dependency:go-offline -f card-service/pom.xml -B

# 5) Kaynak kodları kopyala ve build et
COPY card-service/src ./card-service/src
RUN ./mvnw package -f card-service/pom.xml -DskipTests -B

# =====================================================
# STAGE 2: RUN - Minimal JRE ile çalıştırma
# =====================================================
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=build /workspace/card-service/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 9092

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
