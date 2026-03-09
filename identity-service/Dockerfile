# =====================================================
# STAGE 1: BUILD — Maven ile derleme
# =====================================================
# maven imajı kullanıyoruz — mvnw'ye gerek yok.
# hk-common-lib bağımlılığı önce install edilir,
# sonra identity-service bağımsız olarak derlenir.
# =====================================================
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /workspace

# 1) pom.xml'leri kopyala (layer caching — bağımlılıklar cache'lenir)
COPY pom.xml ./pom.xml
COPY hk-common-lib/pom.xml ./hk-common-lib/pom.xml
COPY identity-service/pom.xml ./identity-service/pom.xml
COPY card-service/pom.xml ./card-service/pom.xml
COPY invoice-service/pom.xml ./invoice-service/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY wallet-service/pom.xml ./wallet-service/pom.xml

# 2) Parent POM'u yükle ve hk-common-lib'i derleyip local repo'ya yükle
RUN mvn install -N -B -q
COPY hk-common-lib/src ./hk-common-lib/src
RUN mvn install -pl hk-common-lib -B -DskipTests -q

# 3) identity-service bağımlılıklarını indir (cache katmanı)
RUN mvn dependency:go-offline -pl identity-service -B -q

# 4) Kaynak kodları kopyala ve derle
COPY identity-service/src ./identity-service/src
RUN mvn package -pl identity-service -B -DskipTests -q

# =====================================================
# STAGE 2: RUN — Sadece JRE ile çalıştırma
# =====================================================
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app
COPY --from=build /workspace/identity-service/target/*.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 9090

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
