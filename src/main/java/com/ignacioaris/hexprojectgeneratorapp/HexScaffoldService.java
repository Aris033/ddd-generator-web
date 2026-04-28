package com.ignacioaris.hexprojectgeneratorapp;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class HexScaffoldService {

    /**
     * Genera un proyecto completo a partir de la configuracion recibida.
     *
     * @param request configuracion del proyecto a crear
     * @return ruta raiz del proyecto generado
     * @throws IOException si falla alguna escritura en disco
     */
    public Path generate(GeneratorRequest request) throws IOException {
        String basePackage = mergePackages(request.groupId(), toPackage(request.artifactId()));
        String projectClassName = toUpperCamelPreservingExisting(request.projectName());
        List<ExampleSpec> exampleSpecs = request.exampleSpecs();
        boolean featureMode = exampleSpecs.size() > 1;
        boolean h2Mode = isH2(request.persistenceMode());
        Path root = request.outputDirectory().resolve(request.artifactId());
        deleteIfExists(root);
        Path mainJava = root.resolve("src/main/java");
        Path mainRes = root.resolve("src/main/resources");
        Path testJava = root.resolve("src/test/java");

        Files.createDirectories(mainJava);
        Files.createDirectories(mainRes);
        Files.createDirectories(testJava);

        Path pkgBootstrap = pkg(mainJava, basePackage + ".bootstrap");

        Path testBootstrap = pkg(testJava, basePackage + ".bootstrap");

        write(root.resolve(".gitignore"), gitignore());
        write(root.resolve(".editorconfig"), editorConfig());
        write(root.resolve("Dockerfile"), dockerfile(request.artifactId()));
        write(root.resolve("README.md"), readme(request, basePackage, featureMode));
        write(root.resolve("pom.xml"), pomXml(request.groupId(), request.artifactId(), request.projectName(), h2Mode));
        write(mainRes.resolve("application.yml"), appYaml(request.artifactId(), h2Mode));
        if (h2Mode) {
            write(mainRes.resolve("schema.sql"), schemaSql(exampleSpecs));
            write(mainRes.resolve("data.sql"), dataSql(exampleSpecs));
        }
        write(pkgBootstrap.resolve("package-info.java"), packageInfo(basePackage + ".bootstrap", "Punto de arranque y configuracion de Spring Boot."));

        write(pkgBootstrap.resolve(projectClassName + "Application.java"), appMain(basePackage, projectClassName, h2Mode));
        write(testBootstrap.resolve(projectClassName + "ApplicationTests.java"), applicationContextTest(basePackage, projectClassName));

        for (ExampleSpec exampleSpec : exampleSpecs) {
            String featureBasePackage = featureMode
                    ? basePackage + ".modules." + toPackage(GenRunner.toKebab(exampleSpec.name()))
                    : basePackage;
            generateExampleScaffold(mainJava, testJava, basePackage, featureBasePackage, exampleSpec, projectClassName, h2Mode);
        }

        return root;
    }

    /**
     * Genera todos los artefactos asociados a una entidad concreta.
     *
     * @param mainJava raiz de codigo de produccion
     * @param testJava raiz de codigo de pruebas
     * @param featureBasePackage package base efectivo de la entidad
     * @param exampleSpec nombre de la entidad
     * @param projectClassName nombre de la clase Application
     * @throws IOException si falla alguna escritura
     */
    private void generateExampleScaffold(
            Path mainJava,
            Path testJava,
            String rootBasePackage,
            String featureBasePackage,
            ExampleSpec exampleSpec,
            String projectClassName,
            boolean h2Mode
    ) throws IOException {
        String exampleName = exampleSpec.name();
        String exampleRoute = GenRunner.toKebab(exampleName);
        Path pkgDomain = pkg(mainJava, featureBasePackage + ".domain");
        Path pkgPortIn = pkg(mainJava, featureBasePackage + ".application.port.in");
        Path pkgPortOut = pkg(mainJava, featureBasePackage + ".application.port.out");
        Path pkgAppService = pkg(mainJava, featureBasePackage + ".application.service");
        Path pkgRest = pkg(mainJava, featureBasePackage + ".infrastructure.adapters.in.rest");
        Path pkgRestDto = pkg(mainJava, featureBasePackage + ".infrastructure.adapters.in.rest.dto");
        Path pkgOutMemory = pkg(mainJava, featureBasePackage + ".infrastructure.adapters.out.memory");
        Path pkgOutJpa = pkg(mainJava, featureBasePackage + ".infrastructure.adapters.out.jpa");
        Path testApplication = pkg(testJava, featureBasePackage + ".application.service");
        Path testRest = pkg(testJava, featureBasePackage + ".infrastructure.adapters.in.rest");

        write(pkgDomain.resolve("package-info.java"), packageInfo(featureBasePackage + ".domain", "Contiene el modelo de dominio y sus reglas puras."));
        write(pkgPortIn.resolve("package-info.java"), packageInfo(featureBasePackage + ".application.port.in", "Define los casos de uso y sus comandos de entrada."));
        write(pkgPortOut.resolve("package-info.java"), packageInfo(featureBasePackage + ".application.port.out", "Define los puertos de salida que la aplicacion necesita."));
        write(pkgAppService.resolve("package-info.java"), packageInfo(featureBasePackage + ".application.service", "Implementa la logica de aplicacion y orquesta el dominio."));
        write(pkgRest.resolve("package-info.java"), packageInfo(featureBasePackage + ".infrastructure.adapters.in.rest", "Expone la API REST y traduce HTTP hacia los casos de uso."));
        write(pkgRestDto.resolve("package-info.java"), packageInfo(featureBasePackage + ".infrastructure.adapters.in.rest.dto", "DTOs de entrada y salida de la API REST."));
        write(pkgOutMemory.resolve("package-info.java"), packageInfo(featureBasePackage + ".infrastructure.adapters.out.memory", "Implementaciones en memoria de los puertos de salida."));
        if (h2Mode) {
            write(pkgOutJpa.resolve("package-info.java"), packageInfo(featureBasePackage + ".infrastructure.adapters.out.jpa", "Adaptadores JPA/H2 para persistencia relacional."));
        }

        write(pkgDomain.resolve(exampleName + ".java"), domainEntity(featureBasePackage, exampleSpec));
        write(pkgPortIn.resolve("Create" + exampleName + "Command.java"), createCommand(featureBasePackage, exampleSpec));
        write(pkgPortIn.resolve("Update" + exampleName + "Command.java"), updateCommand(featureBasePackage, exampleSpec));
        write(pkgPortIn.resolve("Search" + exampleName + "Query.java"), searchQuery(featureBasePackage, exampleSpec));
        write(pkgPortIn.resolve("Create" + exampleName + "UseCase.java"), createUseCase(featureBasePackage, exampleName));
        write(pkgPortIn.resolve("Update" + exampleName + "UseCase.java"), updateUseCase(featureBasePackage, exampleName));
        write(pkgPortIn.resolve("Get" + exampleName + "ByIdUseCase.java"), getByIdUseCase(featureBasePackage, exampleName));
        write(pkgPortIn.resolve("Search" + exampleName + "UseCase.java"), searchUseCase(featureBasePackage, exampleName));
        write(pkgPortOut.resolve(exampleName + "Repository.java"), repositoryPort(featureBasePackage, exampleSpec));
        write(pkgAppService.resolve("Create" + exampleName + "Service.java"), createService(featureBasePackage, exampleSpec));
        write(pkgAppService.resolve("Update" + exampleName + "Service.java"), updateService(featureBasePackage, exampleSpec));
        write(pkgAppService.resolve("Get" + exampleName + "ByIdService.java"), getByIdService(featureBasePackage, exampleName));
        write(pkgAppService.resolve("Search" + exampleName + "Service.java"), searchService(featureBasePackage, exampleSpec));
        write(pkgOutMemory.resolve("InMemory" + exampleName + "Repository.java"), memoryRepository(featureBasePackage, exampleSpec, !h2Mode));
        if (h2Mode) {
            write(pkgOutJpa.resolve(exampleName + "JpaEntity.java"), jpaEntity(featureBasePackage, exampleSpec));
            write(pkgOutJpa.resolve("SpringData" + exampleName + "Repository.java"), springDataRepository(featureBasePackage, exampleName));
            write(pkgOutJpa.resolve(exampleName + "JpaRepositoryAdapter.java"), jpaRepositoryAdapter(featureBasePackage, exampleSpec));
        }
        write(pkgRestDto.resolve("Create" + exampleName + "Request.java"), createRequest(featureBasePackage, exampleSpec));
        write(pkgRestDto.resolve("Update" + exampleName + "Request.java"), updateRequest(featureBasePackage, exampleSpec));
        write(pkgRestDto.resolve(exampleName + "Response.java"), responseDto(featureBasePackage, exampleSpec));
        write(pkgRest.resolve(exampleName + "Controller.java"), restController(featureBasePackage, exampleSpec, exampleRoute));
        write(testApplication.resolve("CreateAndUpdate" + exampleName + "ServiceTest.java"), applicationServiceTest(featureBasePackage, exampleSpec));
        write(testRest.resolve(exampleName + "ControllerTest.java"), controllerTest(featureBasePackage, rootBasePackage, exampleSpec, projectClassName, exampleRoute));
    }

    /**
     * Resuelve y crea una carpeta a partir de un package Java.
     *
     * @param base carpeta base de trabajo
     * @param pkg package Java a materializar
     * @return ruta final del package
     * @throws IOException si falla la creacion de directorios
     */
    private static Path pkg(Path base, String pkg) throws IOException {
        Path path = base.resolve(pkg.replace('.', '/'));
        Files.createDirectories(path);
        return path;
    }

    /**
     * Escribe un archivo de texto reemplazando su contenido si ya existe.
     *
     * @param path archivo destino
     * @param content texto a persistir
     * @throws IOException si falla la escritura
     */
    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Elimina de forma recursiva la carpeta destino para evitar restos de generaciones anteriores.
     *
     * @param root carpeta a limpiar
     * @throws IOException si falla el borrado
     */
    private static void deleteIfExists(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    /**
     * Convierte un nombre de proyecto a UpperCamelCase preservando casos ya validos.
     *
     * @param value texto original
     * @return nombre listo para clases Java
     */
    private static String toUpperCamelPreservingExisting(String value) {
        String trimmed = value.trim();
        if (trimmed.matches("[A-Z][A-Za-z0-9]*")) {
            return trimmed;
        }
        return GenRunner.upperCamel(trimmed);
    }

    /**
     * Convierte un artifactId en una estructura de package segura para Java.
     *
     * @param value artifactId original
     * @return package derivado
     */
    private static String toPackage(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("\\.+", ".")
                .replaceAll("^\\.|\\.$", "");
        if (normalized.isBlank()) {
            return "app";
        }
        String[] parts = normalized.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if (!Character.isLetter(parts[i].charAt(0))) {
                parts[i] = "pkg" + parts[i];
            }
        }
        return String.join(".", parts);
    }

    /**
     * Une el groupId con el package derivado del artifactId evitando segmentos duplicados contiguos.
     *
     * @param groupId groupId Maven
     * @param artifactPackage package derivado del artifactId
     * @return package base sin repeticiones en el borde
     */
    private static String mergePackages(String groupId, String artifactPackage) {
        String[] left = groupId.split("\\.");
        String[] right = artifactPackage.split("\\.");
        int overlap = 0;
        int maxOverlap = Math.min(left.length, right.length);

        for (int candidate = 1; candidate <= maxOverlap; candidate++) {
            boolean matches = true;
            for (int i = 0; i < candidate; i++) {
                if (!left[left.length - candidate + i].equals(right[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                overlap = candidate;
            }
        }

        StringBuilder builder = new StringBuilder(groupId);
        for (int i = overlap; i < right.length; i++) {
            builder.append('.').append(right[i]);
        }
        return builder.toString();
    }

    /**
     * Genera el contenido del `.gitignore` base del scaffold.
     *
     * @return contenido textual del archivo
     */
    private static String gitignore() {
        return """
                target/
                !.mvn/wrapper/maven-wrapper.jar

                .idea/
                *.iml
                .project
                .classpath
                .settings/
                .DS_Store

                *.log
                """;
    }

    /**
     * Genera la configuracion basica de `.editorconfig`.
     *
     * @return contenido textual del archivo
     */
    private static String editorConfig() {
        return """
                root = true

                [*]
                charset = utf-8
                end_of_line = lf
                insert_final_newline = true
                indent_style = space
                indent_size = 2
                trim_trailing_whitespace = true

                [*.java]
                indent_size = 4
                """;
    }

    /**
     * Genera un Dockerfile minimo para compilar y ejecutar el proyecto creado.
     *
     * @param artifactId artifactId del proyecto generado
     * @return contenido textual del Dockerfile
     */
    private static String dockerfile(String artifactId) {
        return """
                FROM maven:3.9.11-eclipse-temurin-21 AS build
                WORKDIR /workspace
                COPY . .
                RUN mvn -q -DskipTests package

                FROM eclipse-temurin:21-jre
                WORKDIR /app
                COPY --from=build /workspace/target/%s-0.0.1-SNAPSHOT.jar app.jar
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                """.formatted(artifactId);
    }

    /**
     * Crea el README del proyecto generado con instrucciones de uso y endpoints.
     *
     * @param request datos base de generacion
     * @param basePackage package raiz del proyecto generado
     * @return contenido textual del README
     */
    private static String readme(GeneratorRequest request, String basePackage, boolean featureMode) {
        StringBuilder endpoints = new StringBuilder();
        for (ExampleSpec exampleSpec : request.exampleSpecs()) {
            String exampleName = exampleSpec.name();
            String route = GenRunner.toKebab(exampleName);
            String createPayload = sampleRequestPayload(exampleSpec.fields(), false);
            String updatePayload = sampleRequestPayload(exampleSpec.fields(), true);
            String filterQuery = sampleFilterQuery(exampleSpec.fields());
            endpoints.append("""
                    ### %s

                    ```bash
                    curl -X POST "http://localhost:8080/api/v1/%s" \\
                      -H "Content-Type: application/json" \\
                      -d '%s'

                    curl "http://localhost:8080/api/v1/%s%s"

                    curl "http://localhost:8080/api/v1/%s/{id}"

                    curl -X PUT "http://localhost:8080/api/v1/%s/{id}" \\
                      -H "Content-Type: application/json" \\
                      -d '%s'
                    ```

                    """.formatted(exampleName, route, createPayload, route, filterQuery, route, route, updatePayload));
        }

        return """
                # %s

                Proyecto generado con arquitectura hexagonal para Spring Boot 3 y Java 21.

                ## Que incluye

                - Entidades de dominio con `id`, `createdAt` y `updatedAt`.
                - CRUD basico con POST, PUT, GET por id y GET por filtros.
                - Casos de uso y puertos separados por capa.
                - Adaptadores REST con DTOs.
                - Repositorios en memoria para pruebas rapidas.
                - Persistencia configurable en `%s`.
                - `package-info.java` en los paquetes principales.

                ## Estructura

                - `domain`: modelo de dominio.
                - `application`: casos de uso y puertos.
                - `infrastructure`: adaptadores de entrada y salida.
                - `bootstrap`: punto de arranque de Spring Boot.

                Modo de estructura: `%s`

                Package base generado: `%s`

                ## Ejecutar

                ```bash
                mvn spring-boot:run
                ```

                ## Endpoints generados

                %s
                ## Notas

                - `artifactId`: `%s`
                - `groupId`: `%s`
                - `persistence`: `%s`
                - entidades de ejemplo: `%s`
                """.formatted(
                request.projectName(),
                request.persistenceMode(),
                featureMode ? "feature" : "layered",
                basePackage,
                endpoints.toString(),
                request.artifactId(),
                request.groupId(),
                request.persistenceMode(),
                request.exampleSpecs().stream().map(ExampleSpec::name).reduce((a, b) -> a + ", " + b).orElse(""));
    }

    /**
     * Genera el `pom.xml` del scaffold Spring Boot.
     *
     * @param groupId groupId del proyecto
     * @param artifactId artifactId del proyecto
     * @param projectName nombre visible del proyecto
     * @return contenido textual del POM
     */
    private static String pomXml(String groupId, String artifactId, String projectName, boolean h2Mode) {
        String persistenceDependencies = h2Mode
                ? """
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-data-jpa</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>com.h2database</groupId>
                      <artifactId>h2</artifactId>
                      <scope>runtime</scope>
                    </dependency>
                """
                : "";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>

                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.5.6</version>
                    <relativePath/>
                  </parent>

                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>0.0.1-SNAPSHOT</version>
                  <name>%s</name>
                  <description>Hexagonal Architecture Service</description>

                  <properties>
                    <java.version>21</java.version>
                  </properties>

                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-web</artifactId>
                    </dependency>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-validation</artifactId>
                    </dependency>
                %s
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-test</artifactId>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>

                  <build>
                    <plugins>
                      <plugin>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-maven-plugin</artifactId>
                      </plugin>
                    </plugins>
                  </build>
                </project>
                """.formatted(groupId, artifactId, projectName, persistenceDependencies);
    }

    /**
     * Genera la configuracion `application.yml` del proyecto resultante.
     *
     * @param artifactId nombre tecnico de la aplicacion
     * @return contenido textual del YAML
     */
    private static String appYaml(String artifactId, boolean h2Mode) {
        if (h2Mode) {
            return """
                    server:
                      port: 8080
                    spring:
                      application:
                        name: %s
                      datasource:
                        url: jdbc:h2:mem:%s-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
                        driver-class-name: org.h2.Driver
                        username: sa
                        password:
                      jpa:
                        hibernate:
                          ddl-auto: none
                        open-in-view: false
                      sql:
                        init:
                          mode: always
                    logging:
                      level:
                        root: INFO
                    management:
                      endpoints:
                        web:
                          exposure:
                            include: health,info
                    """.formatted(artifactId, artifactId.replace('-', '_'));
        }
        return """
                server:
                  port: 8080
                spring:
                  application:
                    name: %s
                logging:
                  level:
                    root: INFO
                """.formatted(artifactId);
    }

    /**
     * Genera el archivo `package-info.java` para documentar un package.
     *
     * @param packageName nombre del package
     * @param description descripcion corta del package
     * @return contenido textual del archivo
     */
    private static String packageInfo(String packageName, String description) {
        return """
                /**
                 * %s
                 */
                package %s;
                """.formatted(description, packageName);
    }

    /**
     * Genera la entidad de dominio principal del ejemplo.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual de la clase
     */
    private static String domainEntity(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".domain;\n\n"
                + importsForFields(exampleSpec.fields(), true)
                + "import java.util.UUID;\n\n"
                + "public record " + exampleName + "(UUID id, " + joinedFieldDeclarations(exampleSpec.fields()) + ", Instant createdAt, Instant updatedAt) {\n\n"
                + "    public static " + exampleName + " create(" + joinedFieldDeclarations(exampleSpec.fields()) + ") {\n"
                + "        validate(" + joinedFieldNames(exampleSpec.fields()) + ");\n"
                + "        Instant now = Instant.now();\n"
                + "        return new " + exampleName + "(UUID.randomUUID(), " + joinedFieldNames(exampleSpec.fields()) + ", now, now);\n"
                + "    }\n\n"
                + "    public " + exampleName + " update(" + joinedFieldDeclarations(exampleSpec.fields()) + ") {\n"
                + "        validate(" + joinedFieldNames(exampleSpec.fields()) + ");\n"
                + "        return new " + exampleName + "(id, " + joinedFieldNames(exampleSpec.fields()) + ", createdAt, Instant.now());\n"
                + "    }\n\n"
                + "    private static void validate(" + joinedFieldDeclarations(exampleSpec.fields()) + ") {\n"
                + stringValidations(exampleSpec.fields())
                + "    }\n"
                + "}\n";
    }

    private static String createCommand(String basePackage, ExampleSpec exampleSpec) {
        return "package " + basePackage + ".application.port.in;\n\n"
                + importsForFields(exampleSpec.fields(), false)
                + "public record Create" + exampleSpec.name() + "Command(" + joinedFieldDeclarations(exampleSpec.fields()) + ") {\n}\n";
    }

    private static String updateCommand(String basePackage, ExampleSpec exampleSpec) {
        return "package " + basePackage + ".application.port.in;\n\n"
                + importsForFields(exampleSpec.fields(), false)
                + "import java.util.UUID;\n\n"
                + "public record Update" + exampleSpec.name() + "Command(UUID id, " + joinedFieldDeclarations(exampleSpec.fields()) + ") {\n}\n";
    }

    private static String searchQuery(String basePackage, ExampleSpec exampleSpec) {
        return "package " + basePackage + ".application.port.in;\n\n"
                + importsForFields(exampleSpec.fields(), false)
                + "public record Search" + exampleSpec.name() + "Query(" + joinedFieldDeclarations(exampleSpec.fields()) + ") {\n}\n";
    }

    /**
     * Genera el puerto de entrada para la operacion de creacion.
     *
     * @param basePackage package raiz
     * @param exampleName nombre de la entidad
     * @return contenido textual del puerto
     */
    private static String createUseCase(String basePackage, String exampleName) {
        return """
                package %s.application.port.in;

                import %s.domain.%s;

                public interface Create%sUseCase {

                    %s create(Create%sCommand command);
                }
                """.formatted(basePackage, basePackage, exampleName, exampleName, exampleName, exampleName);
    }

    /**
     * Genera el puerto de entrada para la operacion de actualizacion.
     *
     * @param basePackage package raiz
     * @param exampleName nombre de la entidad
     * @return contenido textual del puerto
     */
    private static String updateUseCase(String basePackage, String exampleName) {
        return """
                package %s.application.port.in;

                import %s.domain.%s;

                public interface Update%sUseCase {

                    %s update(Update%sCommand command);
                }
                """.formatted(basePackage, basePackage, exampleName, exampleName, exampleName, exampleName);
    }

    /**
     * Genera el puerto de entrada para consultar una entidad por identificador.
     *
     * @param basePackage package raiz
     * @param exampleName nombre de la entidad
     * @return contenido textual del puerto
     */
    private static String getByIdUseCase(String basePackage, String exampleName) {
        return """
                package %s.application.port.in;

                import java.util.Optional;
                import java.util.UUID;
                import %s.domain.%s;

                public interface Get%sByIdUseCase {

                    Optional<%s> getById(UUID id);
                }
                """.formatted(basePackage, basePackage, exampleName, exampleName, exampleName);
    }

    /**
     * Genera el puerto de entrada para busquedas filtradas.
     *
     * @param basePackage package raiz
     * @param exampleName nombre de la entidad
     * @return contenido textual del puerto
     */
    private static String searchUseCase(String basePackage, String exampleName) {
        return """
                package %s.application.port.in;

                import java.util.List;
                import %s.domain.%s;

                public interface Search%sUseCase {

                    List<%s> search(Search%sQuery query);
                }
                """.formatted(basePackage, basePackage, exampleName, exampleName, exampleName, exampleName);
    }

    /**
     * Genera el contrato del repositorio que usara la aplicacion.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del puerto de salida
     */
    private static String repositoryPort(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return """
                package %s.application.port.out;

                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;
                import %s.application.port.in.Search%sQuery;
                import %s.domain.%s;

                public interface %sRepository {

                    %s save(%s entity);

                    Optional<%s> findById(UUID id);

                    List<%s> search(Search%sQuery query);
                }
                """.formatted(
                basePackage,
                basePackage, exampleName,
                basePackage, exampleName,
                exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName, exampleName);
    }

    /**
     * Genera el servicio de aplicacion encargado de crear entidades.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del servicio
     */
    private static String createService(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return """
                package %s.application.service;

                import org.springframework.stereotype.Service;
                import %s.application.port.in.Create%sCommand;
                import %s.application.port.in.Create%sUseCase;
                import %s.application.port.out.%sRepository;
                import %s.domain.%s;

                @Service
                public class Create%sService implements Create%sUseCase {

                    private final %sRepository repository;

                    public Create%sService(%sRepository repository) {
                        this.repository = repository;
                    }

                    @Override
                    public %s create(Create%sCommand command) {
                        %s entity = %s.create(%s);
                        return repository.save(entity);
                    }
                }
                """.formatted(
                basePackage,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName, exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName,
                commandAccessList(exampleSpec.fields()));
    }

    /**
     * Genera el servicio de aplicacion encargado de actualizar entidades.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del servicio
     */
    private static String updateService(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return """
                package %s.application.service;

                import org.springframework.stereotype.Service;
                import java.util.NoSuchElementException;
                import %s.application.port.in.Update%sCommand;
                import %s.application.port.in.Update%sUseCase;
                import %s.application.port.out.%sRepository;
                import %s.domain.%s;

                @Service
                public class Update%sService implements Update%sUseCase {

                    private final %sRepository repository;

                    public Update%sService(%sRepository repository) {
                        this.repository = repository;
                    }

                    @Override
                    public %s update(Update%sCommand command) {
                        %s current = repository.findById(command.id())
                                .orElseThrow(() -> new NoSuchElementException("%s not found"));

                        return repository.save(current.update(%s));
                    }
                }
                """.formatted(
                basePackage,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName, exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName,
                commandAccessList(exampleSpec.fields()));
    }

    /**
     * Genera el servicio de aplicacion para consultas por identificador.
     *
     * @param basePackage package raiz
     * @param exampleName nombre de la entidad
     * @return contenido textual del servicio
     */
    private static String getByIdService(String basePackage, String exampleName) {
        return """
                package %s.application.service;

                import org.springframework.stereotype.Service;
                import java.util.Optional;
                import java.util.UUID;
                import %s.application.port.in.Get%sByIdUseCase;
                import %s.application.port.out.%sRepository;
                import %s.domain.%s;

                @Service
                public class Get%sByIdService implements Get%sByIdUseCase {

                    private final %sRepository repository;

                    public Get%sByIdService(%sRepository repository) {
                        this.repository = repository;
                    }

                    @Override
                    public Optional<%s> getById(UUID id) {
                        return repository.findById(id);
                    }
                }
                """.formatted(
                basePackage,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName, exampleName,
                exampleName);
    }

    /**
     * Genera el servicio de aplicacion para busquedas filtradas.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del servicio
     */
    private static String searchService(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return """
                package %s.application.service;

                import org.springframework.stereotype.Service;
                import java.util.List;
                import %s.application.port.in.Search%sQuery;
                import %s.application.port.in.Search%sUseCase;
                import %s.application.port.out.%sRepository;
                import %s.domain.%s;

                @Service
                public class Search%sService implements Search%sUseCase {

                    private final %sRepository repository;

                    public Search%sService(%sRepository repository) {
                        this.repository = repository;
                    }

                    @Override
                    public List<%s> search(Search%sQuery query) {
                        return repository.search(query);
                    }
                }
                """.formatted(
                basePackage,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                basePackage, exampleName,
                exampleName, exampleName,
                exampleName,
                exampleName, exampleName,
                exampleName, exampleName);
    }

    /**
     * Genera una implementacion en memoria del repositorio del ejemplo.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del repositorio
     */
    private static String memoryRepository(String basePackage, ExampleSpec exampleSpec, boolean beanEnabled) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".infrastructure.adapters.out.memory;\n\n"
                + (beanEnabled ? "import org.springframework.stereotype.Repository;\n" : "")
                + "import java.util.ArrayList;\n"
                + "import java.util.Comparator;\n"
                + "import java.util.List;\n"
                + "import java.util.Optional;\n"
                + "import java.util.UUID;\n"
                + "import java.util.concurrent.ConcurrentHashMap;\n"
                + "import " + basePackage + ".application.port.in.Search" + exampleName + "Query;\n"
                + "import " + basePackage + ".application.port.out." + exampleName + "Repository;\n"
                + "import " + basePackage + ".domain." + exampleName + ";\n\n"
                + (beanEnabled ? "@Repository\n" : "")
                + "public class InMemory" + exampleName + "Repository implements " + exampleName + "Repository {\n\n"
                + "    private final ConcurrentHashMap<UUID, " + exampleName + "> store = new ConcurrentHashMap<>();\n\n"
                + "    @Override\n"
                + "    public " + exampleName + " save(" + exampleName + " entity) {\n"
                + "        store.put(entity.id(), entity);\n"
                + "        return entity;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public Optional<" + exampleName + "> findById(UUID id) {\n"
                + "        return Optional.ofNullable(store.get(id));\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public List<" + exampleName + "> search(Search" + exampleName + "Query query) {\n"
                + "        return store.values().stream()\n"
                + "                .filter(entity -> " + searchPredicate(exampleSpec.fields()) + ")\n"
                + "                .sorted(Comparator.comparing(" + exampleName + "::createdAt))\n"
                + "                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);\n"
                + "    }\n"
                + "}\n";
    }

    private static String jpaEntity(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".infrastructure.adapters.out.jpa;\n\n"
                + "import jakarta.persistence.Column;\n"
                + "import jakarta.persistence.Entity;\n"
                + "import jakarta.persistence.Id;\n"
                + "import jakarta.persistence.Table;\n"
                + importsForFields(exampleSpec.fields(), true)
                + "import java.util.UUID;\n"
                + "import " + basePackage + ".domain." + exampleName + ";\n\n"
                + "@Entity\n"
                + "@Table(name = \"" + safeTableName(exampleName) + "\")\n"
                + "public class " + exampleName + "JpaEntity {\n\n"
                + "    @Id\n"
                + "    private UUID id;\n"
                + jpaFieldDeclarations(exampleSpec.fields())
                + "    @Column(nullable = false)\n"
                + "    private Instant createdAt;\n\n"
                + "    @Column(nullable = false)\n"
                + "    private Instant updatedAt;\n\n"
                + "    protected " + exampleName + "JpaEntity() {\n"
                + "    }\n\n"
                + "    private " + exampleName + "JpaEntity(UUID id, " + joinedFieldDeclarations(exampleSpec.fields()) + ", Instant createdAt, Instant updatedAt) {\n"
                + "        this.id = id;\n"
                + assignmentBlock(exampleSpec.fields(), "this.", "", ";\n")
                + "        this.createdAt = createdAt;\n"
                + "        this.updatedAt = updatedAt;\n"
                + "    }\n\n"
                + "    public static " + exampleName + "JpaEntity fromDomain(" + exampleName + " entity) {\n"
                + "        return new " + exampleName + "JpaEntity(entity.id(), " + entityFieldAccessList(exampleSpec.fields()) + ", entity.createdAt(), entity.updatedAt());\n"
                + "    }\n\n"
                + "    public " + exampleName + " toDomain() {\n"
                + "        return new " + exampleName + "(id, " + joinedFieldNames(exampleSpec.fields()) + ", createdAt, updatedAt);\n"
                + "    }\n"
                + "}\n";
    }

    private static String springDataRepository(String basePackage, String exampleName) {
        return """
                package %s.infrastructure.adapters.out.jpa;

                import java.util.UUID;
                import org.springframework.data.jpa.repository.JpaRepository;

                public interface SpringData%sRepository extends JpaRepository<%sJpaEntity, UUID> {
                }
                """.formatted(basePackage, exampleName, exampleName);
    }

    private static String jpaRepositoryAdapter(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".infrastructure.adapters.out.jpa;\n\n"
                + "import java.util.ArrayList;\n"
                + "import java.util.Comparator;\n"
                + "import java.util.List;\n"
                + "import java.util.Optional;\n"
                + "import java.util.UUID;\n"
                + "import org.springframework.context.annotation.Primary;\n"
                + "import org.springframework.stereotype.Repository;\n"
                + "import " + basePackage + ".application.port.in.Search" + exampleName + "Query;\n"
                + "import " + basePackage + ".application.port.out." + exampleName + "Repository;\n"
                + "import " + basePackage + ".domain." + exampleName + ";\n\n"
                + "@Primary\n"
                + "@Repository\n"
                + "public class " + exampleName + "JpaRepositoryAdapter implements " + exampleName + "Repository {\n\n"
                + "    private final SpringData" + exampleName + "Repository repository;\n\n"
                + "    public " + exampleName + "JpaRepositoryAdapter(SpringData" + exampleName + "Repository repository) {\n"
                + "        this.repository = repository;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public " + exampleName + " save(" + exampleName + " entity) {\n"
                + "        return repository.save(" + exampleName + "JpaEntity.fromDomain(entity)).toDomain();\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public Optional<" + exampleName + "> findById(UUID id) {\n"
                + "        return repository.findById(id).map(" + exampleName + "JpaEntity::toDomain);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public List<" + exampleName + "> search(Search" + exampleName + "Query query) {\n"
                + "        return repository.findAll().stream()\n"
                + "                .map(" + exampleName + "JpaEntity::toDomain)\n"
                + "                .filter(entity -> " + searchPredicate(exampleSpec.fields()) + ")\n"
                + "                .sorted(Comparator.comparing(" + exampleName + "::createdAt))\n"
                + "                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * Genera el DTO de entrada para la operacion POST.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del request
     */
    private static String createRequest(String basePackage, ExampleSpec exampleSpec) {
        return "package " + basePackage + ".infrastructure.adapters.in.rest.dto;\n\n"
                + validationImports(exampleSpec.fields())
                + "public record Create" + exampleSpec.name() + "Request(" + requestFieldDeclarations(exampleSpec.fields()) + ") {\n}\n";
    }

    /**
     * Genera el DTO de entrada para la operacion PUT.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del request
     */
    private static String updateRequest(String basePackage, ExampleSpec exampleSpec) {
        return "package " + basePackage + ".infrastructure.adapters.in.rest.dto;\n\n"
                + validationImports(exampleSpec.fields())
                + "public record Update" + exampleSpec.name() + "Request(" + requestFieldDeclarations(exampleSpec.fields()) + ") {\n}\n";
    }

    /**
     * Genera el DTO de salida que expone la API REST.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del response
     */
    private static String responseDto(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".infrastructure.adapters.in.rest.dto;\n\n"
                + importsForFields(exampleSpec.fields(), true)
                + "import java.util.UUID;\n"
                + "import " + basePackage + ".domain." + exampleName + ";\n\n"
                + "public record " + exampleName + "Response(UUID id, " + joinedFieldDeclarations(exampleSpec.fields()) + ", Instant createdAt, Instant updatedAt) {\n\n"
                + "    public static " + exampleName + "Response fromDomain(" + exampleName + " entity) {\n"
                + "        return new " + exampleName + "Response(entity.id(), " + entityFieldAccessList(exampleSpec.fields()) + ", entity.createdAt(), entity.updatedAt());\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * Genera el controlador REST con las operaciones CRUD basicas.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @param route segmento principal de la URL
     * @return contenido textual del controlador
     */
    private static String restController(String basePackage, ExampleSpec exampleSpec, String route) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".infrastructure.adapters.in.rest;\n\n"
                + "import jakarta.validation.Valid;\n"
                + "import org.springframework.http.HttpStatus;\n"
                + "import org.springframework.web.bind.annotation.GetMapping;\n"
                + "import org.springframework.web.bind.annotation.PathVariable;\n"
                + "import org.springframework.web.bind.annotation.PostMapping;\n"
                + "import org.springframework.web.bind.annotation.PutMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestBody;\n"
                + "import org.springframework.web.bind.annotation.RequestMapping;\n"
                + "import org.springframework.web.bind.annotation.RequestParam;\n"
                + "import org.springframework.web.bind.annotation.ResponseStatus;\n"
                + "import org.springframework.web.bind.annotation.RestController;\n"
                + "import org.springframework.web.server.ResponseStatusException;\n"
                + importsForFields(exampleSpec.fields(), false)
                + "import java.util.List;\n"
                + "import java.util.UUID;\n"
                + "import " + basePackage + ".application.port.in.Create" + exampleName + "Command;\n"
                + "import " + basePackage + ".application.port.in.Create" + exampleName + "UseCase;\n"
                + "import " + basePackage + ".application.port.in.Search" + exampleName + "Query;\n"
                + "import " + basePackage + ".application.port.in.Search" + exampleName + "UseCase;\n"
                + "import " + basePackage + ".application.port.in.Get" + exampleName + "ByIdUseCase;\n"
                + "import " + basePackage + ".application.port.in.Update" + exampleName + "Command;\n"
                + "import " + basePackage + ".application.port.in.Update" + exampleName + "UseCase;\n"
                + "import " + basePackage + ".domain." + exampleName + ";\n"
                + "import " + basePackage + ".infrastructure.adapters.in.rest.dto.Create" + exampleName + "Request;\n"
                + "import " + basePackage + ".infrastructure.adapters.in.rest.dto." + exampleName + "Response;\n"
                + "import " + basePackage + ".infrastructure.adapters.in.rest.dto.Update" + exampleName + "Request;\n\n"
                + "@RestController\n"
                + "@RequestMapping(\"/api/v1/" + route + "\")\n"
                + "public class " + exampleName + "Controller {\n\n"
                + "    private final Create" + exampleName + "UseCase createUseCase;\n"
                + "    private final Update" + exampleName + "UseCase updateUseCase;\n"
                + "    private final Get" + exampleName + "ByIdUseCase getByIdUseCase;\n"
                + "    private final Search" + exampleName + "UseCase searchUseCase;\n\n"
                + "    public " + exampleName + "Controller(\n"
                + "            Create" + exampleName + "UseCase createUseCase,\n"
                + "            Update" + exampleName + "UseCase updateUseCase,\n"
                + "            Get" + exampleName + "ByIdUseCase getByIdUseCase,\n"
                + "            Search" + exampleName + "UseCase searchUseCase\n"
                + "    ) {\n"
                + "        this.createUseCase = createUseCase;\n"
                + "        this.updateUseCase = updateUseCase;\n"
                + "        this.getByIdUseCase = getByIdUseCase;\n"
                + "        this.searchUseCase = searchUseCase;\n"
                + "    }\n\n"
                + "    @PostMapping\n"
                + "    @ResponseStatus(HttpStatus.CREATED)\n"
                + "    public " + exampleName + "Response create(@Valid @RequestBody Create" + exampleName + "Request request) {\n"
                + "        " + exampleName + " created = createUseCase.create(new Create" + exampleName + "Command(" + requestAccessList(exampleSpec.fields()) + "));\n"
                + "        return " + exampleName + "Response.fromDomain(created);\n"
                + "    }\n\n"
                + "    @PutMapping(\"/{id}\")\n"
                + "    public " + exampleName + "Response update(@PathVariable UUID id, @Valid @RequestBody Update" + exampleName + "Request request) {\n"
                + "        try {\n"
                + "            " + exampleName + " updated = updateUseCase.update(new Update" + exampleName + "Command(id, " + requestAccessList(exampleSpec.fields()) + "));\n"
                + "            return " + exampleName + "Response.fromDomain(updated);\n"
                + "        } catch (IllegalArgumentException exception) {\n"
                + "            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);\n"
                + "        } catch (java.util.NoSuchElementException exception) {\n"
                + "            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);\n"
                + "        }\n"
                + "    }\n\n"
                + "    @GetMapping(\"/{id}\")\n"
                + "    public " + exampleName + "Response getById(@PathVariable UUID id) {\n"
                + "        return getByIdUseCase.getById(id)\n"
                + "                .map(" + exampleName + "Response::fromDomain)\n"
                + "                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"" + exampleName + " not found\"));\n"
                + "    }\n\n"
                + "    @GetMapping\n"
                + "    public List<" + exampleName + "Response> find(" + requestParams(exampleSpec.fields()) + ") {\n"
                + "        return searchUseCase.search(new Search" + exampleName + "Query(" + joinedFieldNames(exampleSpec.fields()) + ")).stream()\n"
                + "                .map(" + exampleName + "Response::fromDomain)\n"
                + "                .toList();\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * Genera la clase principal de Spring Boot del proyecto scaffold.
     *
     * @param basePackage package raiz
     * @param projectClassName nombre de la clase Application
     * @return contenido textual de la clase principal
     */
    private static String appMain(String basePackage, String projectClassName, boolean h2Mode) {
        if (h2Mode) {
            return """
                    package %s.bootstrap;

                    import org.springframework.boot.SpringApplication;
                    import org.springframework.boot.autoconfigure.SpringBootApplication;
                    import org.springframework.boot.autoconfigure.domain.EntityScan;
                    import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

                    @SpringBootApplication(scanBasePackages = "%s")
                    @EntityScan(basePackages = "%s")
                    @EnableJpaRepositories(basePackages = "%s")
                    public class %sApplication {

                        public static void main(String[] args) {
                            SpringApplication.run(%sApplication.class, args);
                        }
                    }
                    """.formatted(basePackage, basePackage, basePackage, basePackage, projectClassName, projectClassName);
        }
        return """
                package %s.bootstrap;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication(scanBasePackages = "%s")
                public class %sApplication {

                    public static void main(String[] args) {
                        SpringApplication.run(%sApplication.class, args);
                    }
                }
                """.formatted(basePackage, basePackage, projectClassName, projectClassName);
    }

    /**
     * Genera el test basico de arranque del contexto Spring.
     *
     * @param basePackage package raiz
     * @param projectClassName nombre de la clase Application
     * @return contenido textual del test
     */
    private static String applicationContextTest(String basePackage, String projectClassName) {
        return """
                package %s.bootstrap;

                import org.junit.jupiter.api.Test;
                import org.springframework.boot.test.context.SpringBootTest;

                @SpringBootTest(classes = %sApplication.class)
                class %sApplicationTests {

                    @Test
                    void contextLoads() {
                    }
                }
                """.formatted(basePackage, projectClassName, projectClassName);
    }

    /**
     * Genera un test de aplicacion para verificar create, update y search.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @return contenido textual del test
     */
    private static String applicationServiceTest(String basePackage, ExampleSpec exampleSpec) {
        String exampleName = exampleSpec.name();
        return "package " + basePackage + ".application.service;\n\n"
                + "import org.junit.jupiter.api.Test;\n"
                + "import java.util.List;\n"
                + "import static org.assertj.core.api.Assertions.assertThat;\n"
                + "import " + basePackage + ".application.port.in.Create" + exampleName + "Command;\n"
                + "import " + basePackage + ".application.port.in.Search" + exampleName + "Query;\n"
                + "import " + basePackage + ".application.port.in.Update" + exampleName + "Command;\n"
                + "import " + basePackage + ".infrastructure.adapters.out.memory.InMemory" + exampleName + "Repository;\n"
                + "import " + basePackage + ".domain." + exampleName + ";\n\n"
                + "class CreateAndUpdate" + exampleName + "ServiceTest {\n\n"
                + "    @Test\n"
                + "    void shouldCreateUpdateAndFilterEntities() {\n"
                + "        InMemory" + exampleName + "Repository repository = new InMemory" + exampleName + "Repository();\n"
                + "        Create" + exampleName + "Service createService = new Create" + exampleName + "Service(repository);\n"
                + "        Update" + exampleName + "Service updateService = new Update" + exampleName + "Service(repository);\n"
                + "        Get" + exampleName + "ByIdService getByIdService = new Get" + exampleName + "ByIdService(repository);\n"
                + "        Search" + exampleName + "Service searchService = new Search" + exampleName + "Service(repository);\n\n"
                + "        " + exampleName + " created = createService.create(new Create" + exampleName + "Command(" + testValueList(exampleSpec.fields(), false) + "));\n"
                + "        " + exampleName + " updated = updateService.update(new Update" + exampleName + "Command(created.id(), " + testValueList(exampleSpec.fields(), true) + "));\n"
                + "        List<" + exampleName + "> filtered = searchService.search(new Search" + exampleName + "Query(" + searchValueList(exampleSpec.fields()) + "));\n\n"
                + "        assertThat(getByIdService.getById(created.id())).contains(updated);\n"
                + "        assertThat(filtered).containsExactly(updated);\n"
                + "        assertThat(updated.updatedAt()).isAfterOrEqualTo(updated.createdAt());\n"
                + "    }\n"
                + "}\n";
    }

    /**
     * Genera un test HTTP que recorre el flujo CRUD del controlador.
     *
     * @param basePackage package raiz
     * @param exampleSpec nombre de la entidad
     * @param projectClassName nombre de la clase Application
     * @param route segmento principal de la URL
     * @return contenido textual del test
     */
    private static String controllerTest(String basePackage, String rootBasePackage, ExampleSpec exampleSpec, String projectClassName, String route) {
        String exampleName = exampleSpec.name();
        return """
                package %s.infrastructure.adapters.in.rest;

                import com.fasterxml.jackson.databind.JsonNode;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.http.MediaType;
                import org.springframework.test.web.servlet.MockMvc;
                import %s.bootstrap.%sApplication;

                import static org.assertj.core.api.Assertions.assertThat;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

                @SpringBootTest(classes = %sApplication.class)
                @AutoConfigureMockMvc
                class %sControllerTest {

                    @Autowired
                    private MockMvc mockMvc;

                    @Autowired
                    private ObjectMapper objectMapper;

                    @Test
                    void shouldExecuteCrudFlow() throws Exception {
                        String createResponse = mockMvc.perform(post("/api/v1/%s")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("%s"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.%s").value(%s))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                        JsonNode createdJson = objectMapper.readTree(createResponse);
                        String id = createdJson.get("id").asText();

                        mockMvc.perform(get("/api/v1/%s/{id}", id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(id))
                                .andExpect(jsonPath("$.%s").value(%s));

                        mockMvc.perform(get("/api/v1/%s")%s)
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(id));

                        String updateResponse = mockMvc.perform(put("/api/v1/%s/{id}", id)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("%s"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.%s").value(%s))
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                        JsonNode updatedJson = objectMapper.readTree(updateResponse);
                        assertThat(updatedJson.get("updatedAt").asText()).isNotBlank();
                    }

                    @Test
                    void shouldRejectBlankValueOnCreate() throws Exception {
                        mockMvc.perform(post("/api/v1/%s")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("%s"))
                                .andExpect(status().isBadRequest());
                    }

                    @Test
                    void shouldReturnNotFoundForMissingId() throws Exception {
                        mockMvc.perform(get("/api/v1/%s/{id}", "6f4f0699-b65b-4f17-a4bf-111111111111"))
                                .andExpect(status().isNotFound());
                    }
                }
                """.formatted(
                basePackage,
                rootBasePackage,
                projectClassName,
                projectClassName,
                exampleName,
                route,
                jsonPayload(exampleSpec.fields(), false, false),
                exampleSpec.fields().getFirst().name(),
                jsonAssertLiteral(exampleSpec.fields().getFirst(), false),
                route,
                exampleSpec.fields().getFirst().name(),
                jsonAssertLiteral(exampleSpec.fields().getFirst(), false),
                route,
                searchParams(exampleSpec.fields()),
                route,
                jsonPayload(exampleSpec.fields(), true, false),
                exampleSpec.fields().getFirst().name(),
                jsonAssertLiteral(exampleSpec.fields().getFirst(), true),
                route,
                jsonPayload(exampleSpec.fields(), false, true),
                route);
    }

    private static String importsForFields(List<ExampleField> fields, boolean includeTimestamps) {
        StringBuilder builder = new StringBuilder();
        if (includeTimestamps) {
            builder.append("import java.time.Instant;\n");
        }
        if (fields.stream().anyMatch(field -> field.type().equals("BigDecimal"))) {
            builder.append("import java.math.BigDecimal;\n");
        }
        if (fields.stream().anyMatch(field -> field.type().equals("UUID"))) {
            builder.append("import java.util.UUID;\n");
        }
        if (fields.stream().anyMatch(field -> field.type().equals("LocalDate"))) {
            builder.append("import java.time.LocalDate;\n");
        }
        if (fields.stream().anyMatch(field -> field.type().equals("Instant")) && !includeTimestamps) {
            builder.append("import java.time.Instant;\n");
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String validationImports(List<ExampleField> fields) {
        if (fields.stream().noneMatch(field -> field.type().equals("String"))) {
            return importsForFields(fields, false);
        }
        return "import jakarta.validation.constraints.NotBlank;\n" + importsForFields(fields, false) + "\n";
    }

    private static String joinedFieldDeclarations(List<ExampleField> fields) {
        return fields.stream().map(field -> field.type() + " " + field.name()).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String requestFieldDeclarations(List<ExampleField> fields) {
        return fields.stream()
                .map(field -> (field.type().equals("String") ? "@NotBlank " : "") + field.type() + " " + field.name())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String joinedFieldNames(List<ExampleField> fields) {
        return fields.stream().map(ExampleField::name).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String commandAccessList(List<ExampleField> fields) {
        return fields.stream().map(field -> "command." + field.name() + "()").reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String requestAccessList(List<ExampleField> fields) {
        return fields.stream().map(field -> "request." + field.name() + "()").reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String entityFieldAccessList(List<ExampleField> fields) {
        return fields.stream().map(field -> "entity." + field.name() + "()").reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String stringValidations(List<ExampleField> fields) {
        StringBuilder builder = new StringBuilder();
        for (ExampleField field : fields) {
            if (!field.type().equals("String")) {
                continue;
            }
            builder.append("        if (").append(field.name()).append(" == null || ").append(field.name()).append(".isBlank()) {\n");
            builder.append("            throw new IllegalArgumentException(\"").append(field.name()).append(" must not be blank\");\n");
            builder.append("        }\n");
        }
        return builder.toString();
    }

    private static String searchPredicate(List<ExampleField> fields) {
        return fields.stream().map(HexScaffoldService::fieldPredicate).reduce((a, b) -> a + " && " + b).orElse("true");
    }

    private static String fieldPredicate(ExampleField field) {
        if (field.type().equals("String")) {
            return "(query." + field.name() + "() == null || entity." + field.name() + "().toLowerCase().contains(query." + field.name() + "().toLowerCase()))";
        }
        return "(query." + field.name() + "() == null || entity." + field.name() + "().equals(query." + field.name() + "()))";
    }

    private static String requestParams(List<ExampleField> fields) {
        return fields.stream()
                .map(field -> "@RequestParam(required = false) " + field.type() + " " + field.name())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String testValueList(List<ExampleField> fields, boolean updated) {
        return fields.stream().map(field -> javaLiteral(field, updated)).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String searchValueList(List<ExampleField> fields) {
        return fields.stream()
                .map(field -> field.type().equals("String") ? javaLiteral(field, true) : "null")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private static String jsonPayload(List<ExampleField> fields, boolean updated, boolean blankFirstString) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < fields.size(); i++) {
            ExampleField field = fields.get(i);
            builder.append("\\\"").append(field.name()).append("\\\":");
            if (blankFirstString && i == 0 && field.type().equals("String")) {
                builder.append("\\\"\\\"");
            } else {
                builder.append(jsonLiteral(field, updated));
            }
            if (i < fields.size() - 1) {
                builder.append(",");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    private static String searchParams(List<ExampleField> fields) {
        StringBuilder builder = new StringBuilder();
        for (ExampleField field : fields) {
            if (!field.type().equals("String")) {
                continue;
            }
            builder.append(".param(\"").append(field.name()).append("\", ").append(jsonAssertLiteral(field, false)).append(")");
            break;
        }
        return builder.toString();
    }

    private static String javaLiteral(ExampleField field, boolean updated) {
        return switch (field.type()) {
            case "String" -> "\"" + (updated ? field.name() + "Updated" : capitalize(field.name())) + "\"";
            case "Integer" -> updated ? "42" : "21";
            case "Long" -> updated ? "42L" : "21L";
            case "Boolean" -> updated ? "false" : "true";
            case "BigDecimal" -> "new java.math.BigDecimal(\"" + (updated ? "99.99" : "49.99") + "\")";
            case "UUID" -> "java.util.UUID.fromString(\"" + (updated ? "11111111-1111-1111-1111-111111111112" : "11111111-1111-1111-1111-111111111111") + "\")";
            case "Instant" -> "java.time.Instant.parse(\"2026-01-0" + (updated ? "2" : "1") + "T10:15:30Z\")";
            case "LocalDate" -> "java.time.LocalDate.parse(\"2026-01-0" + (updated ? "2" : "1") + "\")";
            default -> throw new IllegalArgumentException("Tipo no soportado: " + field.type());
        };
    }

    private static String jsonLiteral(ExampleField field, boolean updated) {
        return switch (field.type()) {
            case "String", "UUID", "Instant", "LocalDate" -> "\\\"" + jsonAssertLiteral(field, updated).replace("\"", "") + "\\\"";
            case "Integer", "Long", "BigDecimal" -> jsonAssertLiteral(field, updated);
            case "Boolean" -> updated ? "false" : "true";
            default -> throw new IllegalArgumentException("Tipo no soportado: " + field.type());
        };
    }

    private static String jsonAssertLiteral(ExampleField field, boolean updated) {
        return switch (field.type()) {
            case "String" -> "\"" + (updated ? field.name() + "Updated" : capitalize(field.name())) + "\"";
            case "Integer" -> updated ? "42" : "21";
            case "Long" -> updated ? "42" : "21";
            case "Boolean" -> updated ? "false" : "true";
            case "BigDecimal" -> updated ? "99.99" : "49.99";
            case "UUID" -> "\"" + (updated ? "11111111-1111-1111-1111-111111111112" : "11111111-1111-1111-1111-111111111111") + "\"";
            case "Instant" -> "\"" + "2026-01-0" + (updated ? "2" : "1") + "T10:15:30Z\"";
            case "LocalDate" -> "\"" + "2026-01-0" + (updated ? "2" : "1") + "\"";
            default -> throw new IllegalArgumentException("Tipo no soportado: " + field.type());
        };
    }

    private static String sampleRequestPayload(List<ExampleField> fields, boolean updated) {
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < fields.size(); i++) {
            ExampleField field = fields.get(i);
            builder.append("\"").append(field.name()).append("\":").append(sampleJsonValue(field, updated));
            if (i < fields.size() - 1) {
                builder.append(", ");
            }
        }
        builder.append("}");
        return builder.toString();
    }

    private static String sampleFilterQuery(List<ExampleField> fields) {
        for (ExampleField field : fields) {
            if (field.type().equals("String")) {
                return "?" + field.name() + "=" + capitalize(field.name()).toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    private static String sampleJsonValue(ExampleField field, boolean updated) {
        return switch (field.type()) {
            case "String", "UUID", "Instant", "LocalDate" -> "\"" + jsonAssertLiteral(field, updated).replace("\"", "") + "\"";
            case "Integer", "Long", "BigDecimal", "Boolean" -> jsonAssertLiteral(field, updated);
            default -> throw new IllegalArgumentException("Tipo no soportado: " + field.type());
        };
    }

    private static String schemaSql(List<ExampleSpec> exampleSpecs) {
        StringBuilder builder = new StringBuilder();
        for (ExampleSpec exampleSpec : exampleSpecs) {
            builder.append("create table if not exists ")
                    .append(safeTableName(exampleSpec.name()))
                    .append(" (\n")
                    .append("    id uuid primary key,\n");

            for (ExampleField field : exampleSpec.fields()) {
                builder.append("    ")
                        .append(toSnakeCase(field.name()))
                        .append(" ")
                        .append(sqlType(field))
                        .append(" not null,\n");
            }

            builder.append("    created_at timestamp with time zone not null,\n")
                    .append("    updated_at timestamp with time zone not null\n")
                    .append(");\n\n");
        }
        return builder.toString();
    }

    private static String dataSql(List<ExampleSpec> exampleSpecs) {
        StringBuilder builder = new StringBuilder();
        for (ExampleSpec exampleSpec : exampleSpecs) {
            for (int i = 1; i <= 5; i++) {
                builder.append("insert into ")
                        .append(safeTableName(exampleSpec.name()))
                        .append(" (id, ");

                for (int fieldIndex = 0; fieldIndex < exampleSpec.fields().size(); fieldIndex++) {
                    builder.append(toSnakeCase(exampleSpec.fields().get(fieldIndex).name())).append(", ");
                }

                builder.append("created_at, updated_at) values (")
                        .append(seedId(exampleSpec.name(), i))
                        .append(", ");

                for (int fieldIndex = 0; fieldIndex < exampleSpec.fields().size(); fieldIndex++) {
                    builder.append(sqlSeedValue(exampleSpec.fields().get(fieldIndex), i));
                    if (fieldIndex < exampleSpec.fields().size() - 1) {
                        builder.append(", ");
                    }
                }

                if (!exampleSpec.fields().isEmpty()) {
                    builder.append(", ");
                }

                builder.append("'2026-01-0").append(i).append("T10:15:30Z', ")
                        .append("'2026-01-0").append(i).append("T10:15:30Z');\n");
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static boolean isH2(String persistenceMode) {
        return "h2".equalsIgnoreCase(persistenceMode);
    }

    private static String jpaFieldDeclarations(List<ExampleField> fields) {
        StringBuilder builder = new StringBuilder();
        for (ExampleField field : fields) {
            builder.append("    @Column");
            if (field.type().equals("BigDecimal")) {
                builder.append("(nullable = false, precision = 19, scale = 2)");
            } else {
                builder.append("(nullable = false)");
            }
            builder.append("\n");
            builder.append("    private ").append(field.type()).append(" ").append(field.name()).append(";\n\n");
        }
        return builder.toString();
    }

    private static String assignmentBlock(List<ExampleField> fields, String leftPrefix, String rightPrefix, String suffix) {
        StringBuilder builder = new StringBuilder();
        for (ExampleField field : fields) {
            builder.append("        ")
                    .append(leftPrefix)
                    .append(field.name())
                    .append(" = ")
                    .append(rightPrefix)
                    .append(field.name())
                    .append(suffix);
        }
        return builder.toString();
    }

    private static String safeTableName(String exampleName) {
        return GenRunner.toKebab(exampleName).replace('-', '_') + "_records";
    }

    private static String toSnakeCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }

    private static String sqlType(ExampleField field) {
        return switch (field.type()) {
            case "String" -> "varchar(255)";
            case "Integer" -> "integer";
            case "Long" -> "bigint";
            case "Boolean" -> "boolean";
            case "BigDecimal" -> "numeric(19,2)";
            case "UUID" -> "uuid";
            case "Instant" -> "timestamp with time zone";
            case "LocalDate" -> "date";
            default -> throw new IllegalArgumentException("Tipo no soportado: " + field.type());
        };
    }

    private static String sqlSeedValue(ExampleField field, int index) {
        return switch (field.type()) {
            case "String" -> field.name().toLowerCase(Locale.ROOT).contains("email")
                    ? "'sample" + index + "@example.com'"
                    : "'sample-" + index + "'";
            case "Integer" -> Integer.toString(20 + index);
            case "Long" -> Long.toString(100L + index);
            case "Boolean" -> index % 2 == 0 ? "false" : "true";
            case "BigDecimal" -> String.format(Locale.ROOT, "%.2f", 10.0 + index);
            case "UUID" -> seedId(field.name(), index);
            case "Instant" -> "'2026-01-0" + index + "T10:15:30Z'";
            case "LocalDate" -> "'2026-01-0" + index + "'";
            default -> throw new IllegalArgumentException("Tipo no soportado: " + field.type());
        };
    }

    private static String seedId(String prefix, int index) {
        int hash = Math.abs(prefix.toLowerCase(Locale.ROOT).hashCode()) % 10_000;
        return "'00000000-0000-0000-" + String.format(Locale.ROOT, "%04d", hash) + "-" + String.format(Locale.ROOT, "%012d", index) + "'";
    }
}
