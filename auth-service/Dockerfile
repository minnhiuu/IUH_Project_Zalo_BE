# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Nhận tên module từ build-arg
ARG MODULE_NAME

# 1. Copy POM gốc và cài đặt vào Local Repo của Container
COPY pom.xml .
# --- BƯỚC QUAN TRỌNG NHẤT ---
# -N (non-recursive) để chỉ install chính nó (parent pom) vào .m2
RUN mvn install -N -DskipTests

# 2. Xóa modules để tránh Maven Reactor đi tìm các folder chưa copy
RUN sed -i '/<modules>/,/<\/modules>/d' pom.xml

# 3. Xử lý module common
COPY common/ common/
# Cài đặt common vào repo để các service khác có thể lấy làm dependency
RUN mvn install -f common/pom.xml -DskipTests

# 4. Xử lý module chính (Gateway, Auth,...)
COPY ${MODULE_NAME}/ ${MODULE_NAME}/
# Build package module chính
RUN mvn clean package -f ${MODULE_NAME}/pom.xml -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG MODULE_NAME

# Copy jar từ stage build
COPY --from=build /app/${MODULE_NAME}/target/*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
