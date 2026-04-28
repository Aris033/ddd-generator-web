package com.ignacioaris.hexprojectgeneratorapp;

/**
 * Configuracion local para lanzar el generador sin argumentos por consola.
 * Edita estos valores y ejecuta `mvn spring-boot:run`.
 */
public final class LocalGeneratorPreset {

    public static final boolean ENABLED = true;

    public static final String PROJECT = "DemoHexProject";
    public static final String GROUP_ID = "com.ignacio.demo";
    public static final String ARTIFACT_ID = "";
    public static final String EXAMPLE_1 = "User";
    public static final String STRUCTURE_1 = "name:String,email:String,age:Integer,birthDate:LocalDate";
    public static final String EXAMPLE_2 = "Order";
    public static final String STRUCTURE_2 = "code:String,total:BigDecimal,paid:Boolean,createdOn:LocalDate";
    public static final String PERSISTENCE = "h2";
    public static final String OUTPUT_DIR = "target/generated-sources/hex";

    private LocalGeneratorPreset() {
    }
}
