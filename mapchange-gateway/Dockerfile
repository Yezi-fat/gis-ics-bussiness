# mapchange-gateway（M1 骨架；基于本地 mvn package 产物，构建前需先执行 mvn -DskipTests package）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY mapchange-gateway/target/mapchange-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
