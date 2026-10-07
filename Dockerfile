# Imagem do Vigil em duas etapas: a primeira compila (precisa do JDK e do Maven),
# a segunda só roda (JRE, bem menor). O código-fonte e o Maven não vão para a imagem final.

FROM eclipse-temurin:21-jdk AS compilacao
WORKDIR /app
# Primeiro só o pom: enquanto as dependências não mudarem, o Docker reaproveita esta camada
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -q -B dependency:go-offline
COPY src src
# Os testes rodam no CI (precisam do Docker para o Testcontainers); aqui só empacota
RUN ./mvnw -q -B package -DskipTests && cp target/vigil-*.jar vigil.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Sem root: se alguém achar uma falha na API, não manda no contêiner
RUN addgroup -S vigil && adduser -S vigil -G vigil
COPY --from=compilacao /app/vigil.jar vigil.jar
USER vigil
EXPOSE 8080
# Memória: a VM tem 1 GB para tudo (o Sidekick também roda lá). Medido com o limite de 384 MB:
# com estas opções a API fica em ~250 MB; sem elas, em ~370 MB, colada no limite.
# - até 45% do limite para objetos (sobra para o resto do Java: classes, threads, código compilado)
# - SerialGC: o coletor de lixo mais econômico, bom para uma API pequena com poucos núcleos
# - TieredStopAtLevel=1: só o compilador rápido do Java; gasta bem menos memória e é suficiente aqui
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=32m -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=120m"
ENTRYPOINT ["java", "-jar", "vigil.jar"]
