package com.ignacioaris.hexprojectgeneratorapp;

import java.util.List;

public record ApiGeneratorRequest(
        String project,
        String groupId,
        String artifactId,
        String persistence,
        List<ApiExampleRequest> examples
) {
}
