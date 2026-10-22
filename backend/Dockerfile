
# 第一阶段：使用 Maven 和 Java 21 构建 Spring Boot 项目
FROM maven:3.9-eclipse-temurin-21 AS build

# 保留 backend 与 openapi 的相对目录关系
WORKDIR /workspace/backend

# 复制 Maven 配置、业务源码和 OpenAPI 契约文件
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
COPY openapi/travel-assistant.yaml ../openapi/travel-assistant.yaml

# 生成 OpenAPI 代码、编译并打包
RUN mvn -B clean package -DskipTests \
    && JAR="$(find target -maxdepth 1 -type f -name '*.jar' ! -name '*.original' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

# 第二阶段：只保留运行应用所需的 Java 环境
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/app.jar app.jar

# Render Web Service 默认端口为 10000
EXPOSE 10000

# 使用平台提供的 PORT 环境变量启动应用
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
