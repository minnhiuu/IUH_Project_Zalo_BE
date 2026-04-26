# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Nhận tên module từ build-arg
ARG MODULE_NAME

# 1. Copy POM gốc
COPY pom.xml .

# --- BƯỚC QUAN TRỌNG: Vô hiệu hóa Reactor tìm kiếm module con ---
# Lệnh này xóa bỏ toàn bộ nội dung giữa <modules> và </modules>
RUN sed -i '/<modules>/,/<\/modules>/d' pom.xml

# 2. Xử lý module common (thư viện dùng chung)
COPY common/ common/
# Build và cài đặt common vào local repository của Container (.m2)
# Dùng -f để chỉ định trực tiếp file pom của common
RUN mvn install -f common/pom.xml -DskipTests

# 3. Xử lý module chính (Ví dụ: auth-service, config-server...)
COPY ${MODULE_NAME}/ ${MODULE_NAME}/
# Build package module chính
# -am: also-make (để build các dependency nội bộ nếu có)
# -f: chỉ định file pom của module mục tiêu
RUN mvn clean package -f ${MODULE_NAME}/pom.xml -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG MODULE_NAME

# Copy jar từ stage build (Maven mặc định build ra target trong folder của module)
COPY --from=build /app/${MODULE_NAME}/target/*.jar app.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]