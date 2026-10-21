FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN ./gradlew :${MODULE}:bootJar --no-daemon

FROM eclipse-temurin:21-jre
ARG MODULE
ENV APP_MODULE=${MODULE}
WORKDIR /app
COPY --from=build /src/${MODULE}/build/libs/${MODULE}-1.0.0.jar app.jar
RUN mkdir -p /app/uploads && addgroup --system lexbridge && adduser --system --ingroup lexbridge lexbridge && chown -R lexbridge:lexbridge /app
USER lexbridge
ENTRYPOINT ["java","-jar","/app/app.jar"]
