package com.ignacioaris.hexprojectgeneratorapp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GenRunner implements CommandLineRunner {

    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final List<String> SUPPORTED_PERSISTENCE_MODES = List.of("memory", "h2");

    private final HexScaffoldService generator;
    private final boolean localGenerationEnabled;

    /**
     * Crea el runner con el servicio encargado de generar el scaffold.
     *
     * @param generator servicio principal de generacion
     */
    public GenRunner(HexScaffoldService generator,
                     @Value("${generator.local.enabled:false}") boolean localGenerationEnabled) {
        this.generator = generator;
        this.localGenerationEnabled = localGenerationEnabled;
    }

    /**
     * Procesa los argumentos de entrada y lanza la generacion del proyecto.
     *
     * @param args argumentos de linea de comandos
     * @throws Exception si falla la generacion
     */
    @Override
    public void run(String... args) throws Exception {
        Map<String, String> cli = parseArgs(args);
        GeneratorRequest request;

        boolean explicitLocalMode = Boolean.parseBoolean(cli.getOrDefault("generate-local", "false"))
                || localGenerationEnabled;

        if (cli.isEmpty() && !explicitLocalMode) {
            return;
        }

        if ((cli.isEmpty() || explicitLocalMode) && LocalGeneratorPreset.ENABLED) {
            request = presetRequest();
            System.out.println("Usando configuracion local de LocalGeneratorPreset.");
        } else if (cli.isEmpty()) {
            printUsage();
            return;
        } else {
            request = normalize(cli);
        }

        Path outDir = generator.generate(request);

        System.out.println("\nProyecto hexagonal generado en: " + outDir.toAbsolutePath());
        if (isInsideTarget(outDir)) {
            System.out.println("Copia esa carpeta fuera de 'target' o usa --output para generarlo directamente en tu workspace.");
        }
        System.out.println();
    }

    /**
     * Normaliza y valida los argumentos de entrada antes de construir la peticion.
     *
     * @param cli mapa de argumentos parseados
     * @return peticion lista para el generador
     */
    private static GeneratorRequest normalize(Map<String, String> cli) {
        String project = required(cli, "project", "p", "El parametro --project es obligatorio.");
        String groupId = cli.getOrDefault("groupId", cli.getOrDefault("g", "com.example")).trim();
        String artifactId = cli.getOrDefault("artifactId", cli.getOrDefault("a", ""));
        String output = cli.getOrDefault("output", cli.getOrDefault("o", "")).trim();
        List<ExampleSpec> exampleSpecs = parseExampleSpecs(cli);
        String persistenceMode = normalizePersistenceMode(cli.getOrDefault("persistence", "memory"));

        project = normalizeProjectName(project);
        groupId = normalizeGroupId(groupId);
        artifactId = artifactId.isBlank() ? toKebab(project) : toKebab(artifactId);
        validateArtifactId(artifactId);
        Path outputDirectory = output.isBlank()
                ? Paths.get("target", "generated-sources", "hex")
                : Paths.get(output);

        return new GeneratorRequest(project, groupId, artifactId, exampleSpecs, persistenceMode, outputDirectory);
    }

    /**
     * Construye una peticion directamente desde el preset local preservando su estructura tipada.
     *
     * @return peticion lista para generar el proyecto
     */
    private static GeneratorRequest presetRequest() {
        String project = normalizeProjectName(LocalGeneratorPreset.PROJECT);
        String groupId = normalizeGroupId(LocalGeneratorPreset.GROUP_ID);
        String artifactId = LocalGeneratorPreset.ARTIFACT_ID.isBlank()
                ? toKebab(project)
                : toKebab(LocalGeneratorPreset.ARTIFACT_ID);
        validateArtifactId(artifactId);
        Path outputDirectory = LocalGeneratorPreset.OUTPUT_DIR == null || LocalGeneratorPreset.OUTPUT_DIR.isBlank()
                ? Paths.get("target", "generated-sources", "hex")
                : Paths.get(LocalGeneratorPreset.OUTPUT_DIR.trim());
        return new GeneratorRequest(
                project,
                groupId,
                artifactId,
                presetExamples(),
                normalizePersistenceMode(LocalGeneratorPreset.PERSISTENCE),
                outputDirectory
        );
    }

    /**
     * Obtiene un parametro obligatorio aceptando tambien su alias corto.
     *
     * @param cli mapa de argumentos parseados
     * @param key nombre largo del parametro
     * @param alias alias corto del parametro
     * @param message mensaje de error si falta
     * @return valor del parametro requerido
     */
    private static String required(Map<String, String> cli, String key, String alias, String message) {
        String value = cli.getOrDefault(key, cli.getOrDefault(alias, "")).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /**
     * Convierte la entrada de examples en una lista normalizada de nombres de entidad.
     *
     * @param cli mapa de argumentos parseados
     * @return lista de entidades a generar
     */
    private static List<ExampleSpec> parseExampleSpecs(Map<String, String> cli) {
        String rawExamples = cli.getOrDefault("example", cli.getOrDefault("e", "Example")).trim();
        List<ExampleSpec> examples = new ArrayList<>();
        for (String item : rawExamples.split(",")) {
            String normalized = upperCamel(item.trim());
            if (!normalized.isBlank()) {
                examples.add(new ExampleSpec(normalized, defaultFields()));
            }
        }
        if (examples.isEmpty()) {
            examples.add(new ExampleSpec("Example", defaultFields()));
        }
        return examples;
    }

    /**
     * Devuelve la estructura minima por defecto para ejemplos sin definicion explicita.
     *
     * @return lista de campos base
     */
    private static List<ExampleField> defaultFields() {
        return List.of(new ExampleField("value", "String"));
    }

    /**
     * Limpia el nombre del proyecto y verifica que no este vacio.
     *
     * @param value nombre recibido
     * @return nombre listo para usarse
     */
    private static String normalizeProjectName(String value) {
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("El nombre del proyecto no puede estar vacio.");
        }
        return trimmed;
    }

    /**
     * Valida y normaliza el groupId a formato Maven.
     *
     * @param value groupId recibido
     * @return groupId normalizado
     */
    private static String normalizeGroupId(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!GROUP_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("El groupId debe parecerse a 'com.ejemplo.app'.");
        }
        return normalized;
    }

    /**
     * Comprueba que el artifactId use kebab-case.
     *
     * @param value artifactId a validar
     */
    private static void validateArtifactId(String value) {
        if (!ARTIFACT_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("El artifactId debe estar en kebab-case, por ejemplo 'creator-service'.");
        }
    }

    /**
     * Valida y normaliza el modo de persistencia configurado.
     *
     * @param value valor recibido por CLI o preset
     * @return modo de persistencia en minusculas
     */
    private static String normalizePersistenceMode(String value) {
        String normalized = value == null || value.isBlank() ? "memory" : value.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PERSISTENCE_MODES.contains(normalized)) {
            throw new IllegalArgumentException("La persistencia debe ser 'memory' o 'h2'.");
        }
        return normalized;
    }

    /**
     * Indica si la ruta generada queda dentro de la carpeta target.
     *
     * @param outDir ruta final del scaffold
     * @return true si la salida esta bajo target
     */
    private static boolean isInsideTarget(Path outDir) {
        Path normalized = outDir.normalize();
        return normalized.toString().contains("target");
    }

    /**
     * Muestra un resumen de uso cuando faltan argumentos.
     */
    private static void printUsage() {
        System.out.println("""
                Uso:
                  java -jar hexprojectgeneratorappapp-<ver>.jar --project=Nombre [--groupId=com.ejemplo] [--artifactId=nombre-kebab] [--example=Clase] [--persistence=memory|h2] [--output=ruta]

                Ejemplos:
                  java -jar target/hexprojectgeneratorappapp-0.0.1-SNAPSHOT.jar \\
                    --project=CreatorService --groupId=com.influtrack --example=Greeting --persistence=h2

                  mvn spring-boot:run "-Dspring-boot.run.arguments=--project=CreatorService --groupId=com.influtrack --persistence=h2 --output=C:\\\\Users\\\\Ignacio\\\\IdeaProjects"
                """);
    }

    /**
     * Convierte los argumentos estilo --clave=valor en un mapa simple.
     *
     * @param args argumentos recibidos por la JVM
     * @return mapa de argumentos parseados
     */
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String a : args) {
            if (!a.startsWith("--")) {
                continue;
            }
            String kv = a.substring(2);
            int eq = kv.indexOf('=');
            if (eq > 0) {
                out.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            } else {
                out.put(kv.trim(), "true");
            }
        }
        return out;
    }

    /**
     * Construye los argumentos a partir del preset local.
     *
     * @return mapa de argumentos simulados desde configuracion local
     */
    private static Map<String, String> presetArgs() {
        Map<String, String> out = new LinkedHashMap<>();
        putIfPresent(out, "project", LocalGeneratorPreset.PROJECT);
        putIfPresent(out, "groupId", LocalGeneratorPreset.GROUP_ID);
        putIfPresent(out, "artifactId", LocalGeneratorPreset.ARTIFACT_ID);
        List<ExampleSpec> presetExamples = presetExamples();
        putIfPresent(out, "example", presetExamples.stream().map(ExampleSpec::name).reduce((a, b) -> a + "," + b).orElse("Example"));
        putIfPresent(out, "persistence", LocalGeneratorPreset.PERSISTENCE);
        putIfPresent(out, "output", LocalGeneratorPreset.OUTPUT_DIR);
        return out;
    }

    /**
     * Agrupa todos los campos EXAMPLE_n definidos en el preset local.
     *
     * @return lista separada por comas con los ejemplos activos
     */
    private static List<ExampleSpec> presetExamples() {
        List<Field> exampleFields = new ArrayList<>();
        for (Field field : LocalGeneratorPreset.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!field.getName().matches("EXAMPLE_\\d+")) {
                continue;
            }
            exampleFields.add(field);
        }

        exampleFields.sort(Comparator.comparingInt(field ->
                Integer.parseInt(field.getName().substring("EXAMPLE_".length()))));

        List<ExampleSpec> examples = new ArrayList<>();
        for (Field field : exampleFields) {
            try {
                Object value = field.get(null);
                if (value instanceof String text && !text.isBlank()) {
                    String suffix = field.getName().substring("EXAMPLE_".length());
                    examples.add(new ExampleSpec(upperCamel(text.trim()), parseStructure(presetStructureFor(suffix))));
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("No se pudo leer " + field.getName(), exception);
            }
        }
        if (examples.isEmpty()) {
            examples.add(new ExampleSpec("Example", defaultFields()));
        }
        return examples;
    }

    /**
     * Recupera la estructura tipada asociada a un `EXAMPLE_n`.
     *
     * @param suffix indice del example
     * @return estructura cruda o la estructura por defecto
     */
    private static String presetStructureFor(String suffix) {
        try {
            Field structureField = LocalGeneratorPreset.class.getDeclaredField("STRUCTURE_" + suffix);
            Object value = structureField.get(null);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Si no existe una estructura declarada, se usa la minima por defecto.
        }
        return "value:String";
    }

    /**
     * Convierte una estructura textual a una lista de campos tipados validados.
     *
     * @param rawStructure estructura en formato campo:Tipo
     * @return lista de campos tipados
     */
    private static List<ExampleField> parseStructure(String rawStructure) {
        List<ExampleField> fields = new ArrayList<>();
        for (String token : rawStructure.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw new IllegalArgumentException("La estructura debe usar formato campo:Tipo.");
            }
            fields.add(new ExampleField(
                    trimmed.substring(0, separator).trim(),
                    trimmed.substring(separator + 1).trim()
            ));
        }
        if (fields.isEmpty()) {
            return defaultFields();
        }
        return fields;
    }

    /**
     * Inserta un valor en el mapa solo si tiene contenido util.
     *
     * @param out mapa destino
     * @param key clave a insertar
     * @param value valor potencial
     */
    private static void putIfPresent(Map<String, String> out, String key, String value) {
        if (value != null && !value.isBlank()) {
            out.put(key, value.trim());
        }
    }

    /**
     * Convierte un texto arbitrario a kebab-case.
     *
     * @param s texto original
     * @return texto en kebab-case
     */
    static String toKebab(String s) {
        String spaced = s
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2");
        String normalized = spaced.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-");
        return normalized.replaceAll("^-|-$", "");
    }

    /**
     * Convierte un texto arbitrario a UpperCamelCase.
     *
     * @param s texto original
     * @return texto normalizado como identificador Java
     */
    static String upperCamel(String s) {
        String trimmed = s.trim();
        if (trimmed.isBlank()) {
            return "Example";
        }
        if (trimmed.matches("[A-Z][A-Za-z0-9]*")) {
            return trimmed;
        }

        String[] parts = trimmed.split("[^A-Za-z0-9]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        String value = builder.isEmpty() ? "Example" : builder.toString();
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            value = "X" + value;
        }
        return value;
    }
}
