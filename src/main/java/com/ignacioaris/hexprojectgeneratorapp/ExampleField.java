package com.ignacioaris.hexprojectgeneratorapp;

import java.util.Set;

/**
 * Representa un campo declarado dentro de la estructura de una entidad de ejemplo.
 *
 * @param name nombre del campo
 * @param type tipo Java soportado
 */
public record ExampleField(String name, String type) {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "String", "Integer", "Long", "Boolean", "BigDecimal", "UUID", "Instant", "LocalDate"
    );

    public ExampleField {
        if (name == null || !name.matches("[a-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("El nombre del campo debe ser camelCase y empezar por minuscula.");
        }
        if (type == null || !SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Tipo no soportado para el campo '" + name + "': " + type);
        }
    }
}
