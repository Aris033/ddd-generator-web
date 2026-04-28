package com.ignacioaris.hexprojectgeneratorapp;

import java.util.List;

public class ApiValidationException extends RuntimeException {

    private final List<String> errors;

    public ApiValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
