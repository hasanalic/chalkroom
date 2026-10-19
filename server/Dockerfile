# Server image: builds the fat JAR, then runs it on a plain JRE.
# Build from the repo root (the server needs :shared):
#   docker build -f server/Dockerfile -t chalkroom-server .
#   docker run --rm -p 8080:8080 -e GEMINI_API_KEY chalkroom-server
# PORT is honoured (hosts like Railway set it); GEMINI_API_KEY is passed at run time, never baked in.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :server:buildFatJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/server/build/libs/server-all.jar app.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
