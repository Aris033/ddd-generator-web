package com.ignacioaris.hexprojectgeneratorapp;

import java.nio.file.Path;
import java.util.List;

/**
 * Agrupa los datos necesarios para generar un nuevo proyecto.
 *
 * @param projectName nombre visible del proyecto
 * @param groupId groupId Maven base
 * @param artifactId artifactId Maven y carpeta final
 * @param exampleSpecs entidades de ejemplo con su estructura tipada
 * @param persistenceMode estrategia de persistencia a generar
 * @param outputDirectory carpeta base donde se escribira el scaffold
 */
public record GeneratorRequest(
        String projectName,
        String groupId,
        String artifactId,
        List<ExampleSpec> exampleSpecs,
        String persistenceMode,
        Path outputDirectory
) {
}
