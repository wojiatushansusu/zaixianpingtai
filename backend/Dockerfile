# ── 构建阶段 ──
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 先只拷 pom，利用镜像层缓存：只要依赖没变，就不会重复下载
COPY pom.xml .
# javacv-platform 默认会拉取所有平台的原生库（超过 1GB），这里只取 linux-x86_64
RUN mvn -B -q dependency:go-offline -Djavacpp.platform=linux-x86_64

COPY src ./src
RUN mvn -B -q clean package -DskipTests -Djavacpp.platform=linux-x86_64

# ── 运行阶段 ──
# 固定 jammy(22.04) 而不是默认的 latest：OpenCV 的 highgui 原生库在动态链接层依赖
# GTK2（libgtk-x11-2.0.so.0），而 Ubuntu 26.04 的软件源里已经没有 libgtk2.0-0，
# 用 latest 会在启动时抛 UnsatisfiedLinkError 直接退出。
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
        libgtk2.0-0 libglib2.0-0 libsm6 libxext6 libxrender1 \
    && rm -rf /var/lib/apt/lists/*

# JavaCV 会把原生库解压到缓存目录，这里指到容器内的可写路径
ENV JAVACPP_CACHE=/app/.javacpp-cache

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.awt.headless=true", "-jar", "app.jar"]
