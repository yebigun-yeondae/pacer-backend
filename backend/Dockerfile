# 빌드 단계
FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

# 캐시 최적화
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle build -x test || return 0

# 실제 소스
COPY . .
RUN gradle clean build -x test

# 실행 단계
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# jar 복사
COPY --from=builder /app/build/libs/*SNAPSHOT.jar app.jar

# 포트
EXPOSE 8080

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]