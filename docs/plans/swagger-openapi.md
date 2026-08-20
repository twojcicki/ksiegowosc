# Dodanie Swaggera

Commit: `3763723`

## Podejście

`springdoc-openapi-starter-webmvc-ui` w wersji `3.1.0` (linia 3.x dla Spring Boot 4). Starter wystawia Swagger UI i JSON OpenAPI bez ręcznej konfiguracji servletów.

## Zmiany

- Zależność w [pom.xml](pom.xml): `springdoc-openapi-starter-webmvc-ui` 3.1.0
- Bean `OpenAPI` w [OpenApiConfig.java](src/main/java/pl/tw/ksiegowosc/config/OpenApiConfig.java)
- Adnotacje `@Tag` / `@Operation` na [InvoicesController](src/main/java/pl/tw/ksiegowosc/controller/InvoicesController.java)
- Adresy w README:
  - UI: `http://localhost:8080/swagger-ui.html`
  - spec: `http://localhost:8080/v3/api-docs`
