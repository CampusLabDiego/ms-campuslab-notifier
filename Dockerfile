FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw

COPY src/ src/

RUN ./mvnw -B clean package -DskipTests


FROM eclipse-temurin:21-jre

WORKDIR /app

RUN mkdir -p /app/storage \
    && chown -R 10001:10001 /app

COPY --from=build --chown=10001:10001 \
    /app/target/ms-campuslab-notifier-0.0.1-SNAPSHOT.jar \
    /app/app.jar

ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true"

USER 10001:10001

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]