FROM eclipse-temurin:21

WORKDIR /app

COPY target/web-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 35000

CMD ["java", "-jar", "app.jar", "com.eci.arep.web.HelloController", "com.eci.arep.web.GreetingController"]