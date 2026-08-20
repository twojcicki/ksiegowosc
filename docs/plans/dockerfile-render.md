# Dockerfile pod Render.com

Commit: `958dc19`

## Port

Render wstrzykuje zmienną `PORT`. W [application.yml](src/main/resources/application.yml):

```yaml
server:
  port: ${PORT:8080}
```

Lokalnie `8080`, na Renderze wartość z `PORT`.

## Dockerfile

Wieloetapowy build:

- build: `maven:3.9-eclipse-temurin-25` — `mvn -B -DskipTests package`
- runtime: `eclipse-temurin:25-jre` — tylko JAR
- użytkownik `app`, `EXPOSE 8080`, `CMD java -jar app.jar`

[.dockerignore](.dockerignore) wyklucza `target/`, `.git`, IDE.

## Render.com

- Web Service, runtime Docker, repozytorium GitHub
- Sekrety: `MERIT_API_ID`, `MERIT_API_KEY`
