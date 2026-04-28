package com.ignacioaris.hexprojectgeneratorapp;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ProjectGeneratorService {

    private static final int MAX_EXAMPLES = 10;
    private static final int MAX_TEXT_LENGTH = 180;
    private static final int MAX_STRUCTURE_LENGTH = 800;
    private static final Pattern PROJECT_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9 _-]{1,79}");
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+");
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Set<String> SUPPORTED_PERSISTENCE = Set.of("memory", "h2");

    private final HexScaffoldService scaffoldService;

    public ProjectGeneratorService(HexScaffoldService scaffoldService) {
        this.scaffoldService = scaffoldService;
    }

    public ApiGeneratorRequest defaults() {
        return new ApiGeneratorRequest(
                LocalGeneratorPreset.PROJECT,
                LocalGeneratorPreset.GROUP_ID,
                LocalGeneratorPreset.ARTIFACT_ID,
                LocalGeneratorPreset.PERSISTENCE,
                List.of(
                        new ApiExampleRequest(LocalGeneratorPreset.EXAMPLE_1, LocalGeneratorPreset.STRUCTURE_1),
                        new ApiExampleRequest(LocalGeneratorPreset.EXAMPLE_2, LocalGeneratorPreset.STRUCTURE_2)
                )
        );
    }

    public PreviewResponse preview(ApiGeneratorRequest request) {
        GeneratorRequest normalized = normalize(request, Path.of("."));
        String packagePath = normalized.groupId().replace('.', '/');
        List<String> folders = List.of(
                "src/main/java/" + packagePath + "/domain",
                "src/main/java/" + packagePath + "/application",
                "src/main/java/" + packagePath + "/infrastructure",
                "src/main/resources"
        );
        List<String> entities = normalized.exampleSpecs().stream().map(ExampleSpec::name).toList();
        return new PreviewResponse(normalized.projectName(), folders, entities);
    }

    public GeneratedZip generateZip(ApiGeneratorRequest request) throws IOException {
        Path tempDirectory = Files.createTempDirectory("hex-generator-");
        try {
            GeneratorRequest normalized = normalize(request, tempDirectory);
            Path projectRoot = scaffoldService.generate(normalized);
            byte[] zip = zipDirectory(projectRoot);
            return new GeneratedZip(normalized.projectName() + ".zip", zip);
        } finally {
            deleteIfExists(tempDirectory);
        }
    }

    private GeneratorRequest normalize(ApiGeneratorRequest request, Path outputDirectory) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            throw new ApiValidationException(List.of("Request body is required."));
        }

        String project = trim(request.project());
        String groupId = trim(request.groupId()).toLowerCase(Locale.ROOT);
        String artifactId = trim(request.artifactId());
        String persistence = trim(request.persistence()).toLowerCase(Locale.ROOT);

        if (project.isBlank()) {
            errors.add("project is required.");
        } else if (project.length() > MAX_TEXT_LENGTH || !PROJECT_PATTERN.matcher(project).matches()) {
            errors.add("project must start with a letter and contain only letters, numbers, spaces, dashes or underscores.");
        }

        if (groupId.isBlank()) {
            errors.add("groupId is required.");
        } else if (groupId.length() > MAX_TEXT_LENGTH || !GROUP_ID_PATTERN.matcher(groupId).matches()) {
            errors.add("groupId must look like a Java package, for example com.example.demo.");
        }

        if (artifactId.isBlank() && !project.isBlank()) {
            artifactId = GenRunner.toKebab(project);
        } else if (!artifactId.isBlank()) {
            artifactId = GenRunner.toKebab(artifactId);
        }
        if (artifactId.isBlank() || artifactId.length() > MAX_TEXT_LENGTH || !ARTIFACT_ID_PATTERN.matcher(artifactId).matches()) {
            errors.add("artifactId must be empty or kebab-case, for example demo-hex-project.");
        }

        if (persistence.isBlank()) {
            errors.add("persistence is required.");
        } else if (!SUPPORTED_PERSISTENCE.contains(persistence)) {
            errors.add("persistence must be one of: memory, h2.");
        }

        List<ExampleSpec> examples = normalizeExamples(request.examples(), errors);
        if (!errors.isEmpty()) {
            throw new ApiValidationException(errors);
        }
        return new GeneratorRequest(project, groupId, artifactId, examples, persistence, outputDirectory);
    }

    private static List<ExampleSpec> normalizeExamples(List<ApiExampleRequest> examples, List<String> errors) {
        if (examples == null || examples.isEmpty()) {
            errors.add("examples must contain at least one entity.");
            return List.of();
        }
        if (examples.size() > MAX_EXAMPLES) {
            errors.add("examples cannot contain more than " + MAX_EXAMPLES + " entities.");
            return List.of();
        }

        List<ExampleSpec> normalized = new ArrayList<>();
        for (int i = 0; i < examples.size(); i++) {
            ApiExampleRequest example = examples.get(i);
            String prefix = "examples[" + i + "]";
            String name = example == null ? "" : trim(example.name());
            String structure = example == null ? "" : trim(example.structure());

            if (name.isBlank()) {
                errors.add(prefix + ".name is required.");
                continue;
            }
            if (name.length() > MAX_TEXT_LENGTH || !name.matches("[A-Z][A-Za-z0-9]*")) {
                errors.add(prefix + ".name must be a valid Java class identifier, for example User.");
            }
            if (structure.isBlank()) {
                errors.add(prefix + ".structure is required.");
                continue;
            }
            if (structure.length() > MAX_STRUCTURE_LENGTH) {
                errors.add(prefix + ".structure is too long.");
                continue;
            }

            try {
                normalized.add(new ExampleSpec(name, parseStructure(structure)));
            } catch (IllegalArgumentException exception) {
                errors.add(prefix + ".structure " + exception.getMessage());
            }
        }
        return normalized;
    }

    private static List<ExampleField> parseStructure(String rawStructure) {
        List<ExampleField> fields = new ArrayList<>();
        for (String token : rawStructure.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int separator = trimmed.indexOf(':');
            if (separator <= 0 || separator == trimmed.length() - 1) {
                throw new IllegalArgumentException("must use field:Type entries separated by commas.");
            }
            fields.add(new ExampleField(
                    trimmed.substring(0, separator).trim(),
                    trimmed.substring(separator + 1).trim()
            ));
        }
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("must contain at least one field.");
        }
        return fields;
    }

    private static byte[] zipDirectory(Path root) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.filter(Files::isRegularFile).toList()) {
                    String entryName = root.relativize(path).toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private static void deleteIfExists(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
