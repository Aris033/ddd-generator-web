package com.ignacioaris.hexprojectgeneratorapp;

import java.util.List;

/**
 * Define una entidad de ejemplo junto con la estructura de campos que debe generar.
 *
 * @param name nombre de la entidad
 * @param fields campos tipados de la entidad
 */
public record ExampleSpec(String name, List<ExampleField> fields) {

    public ExampleSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del example es obligatorio.");
        }
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Cada example debe tener al menos un campo.");
        }
        fields = List.copyOf(fields);
    }
}
