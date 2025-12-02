# ============================
# 1단계: 빌드 스테이지
# ============================
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./

COPY src src

RUN chmod +x ./gradlew

RUN ./gradlew clean bootJar -x test


# ============================
# 2단계: 실행 스테이지
# ============================
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/build/libs/fryday-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","/app/app.jar"]
