# 构建阶段
FROM maven:3.8.6-eclipse-temurin-17 AS builder

# 设置工作目录
WORKDIR /app

# 配置Maven使用阿里云镜像仓库
RUN mkdir -p /root/.m2 && echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd"><mirrors><mirror><id>aliyunmaven</id><mirrorOf>*</mirrorOf><name>阿里云公共仓库</name><url>https://maven.aliyun.com/repository/public</url></mirror><mirror><id>aliyunmaven-central</id><mirrorOf>central</mirrorOf><name>阿里云central仓库</name><url>https://maven.aliyun.com/repository/central</url></mirror><mirror><id>aliyunmaven-spring</id><mirrorOf>spring</mirrorOf><name>阿里云spring仓库</name><url>https://maven.aliyun.com/repository/spring</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 复制pom.xml文件并下载依赖
COPY pom.xml .
RUN mvn dependency:go-offline

# 复制源代码并构建项目
COPY src/ ./src
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:17-alpine

# 设置工作目录
WORKDIR /app

# 创建配置文件挂载点
VOLUME /app/config

# 从构建阶段复制构建产物
COPY --from=builder /app/target/case-ai-backend-0.0.1-SNAPSHOT.jar app.jar

# 设置JVM参数
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 暴露应用端口
EXPOSE 8080

# 设置启动命令，支持读取外部配置文件
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.config.location=classpath:/,optional:classpath:/config/,file:/app/config/"]