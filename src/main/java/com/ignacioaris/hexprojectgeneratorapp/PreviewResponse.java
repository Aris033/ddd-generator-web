package com.ignacioaris.hexprojectgeneratorapp;

import java.util.List;

public record PreviewResponse(String project, List<String> folders, List<String> entities) {
}
