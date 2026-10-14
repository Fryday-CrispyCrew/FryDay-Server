# CI에서 빌드한 jar(테스트·REST Docs 포함)를 담기만 한다. 로컬에서는 ./gradlew bootJar 후 빌드한다.
FROM eclipse-temurin:21-jre

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

WORKDIR /app

COPY build/libs/fryday-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
