# ── 构建阶段 ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先只拷 pom，利用镜像层缓存：只要依赖没变，就不会重复下载
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── 运行阶段 ──
FROM eclipse-temurin:21-jre
WORKDIR /app

# JavaCV 会把原生库解压到缓存目录，这里指到容器内的可写路径
ENV JAVACPP_CACHE=/app/.javacpp-cache

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "app.jar"]
