package com.ignacioaris.hexprojectgeneratorapp;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class GeneratorApiController {

    private final ProjectGeneratorService generatorService;

    public GeneratorApiController(ProjectGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/defaults")
    public ApiGeneratorRequest defaults() {
        return generatorService.defaults();
    }

    @PostMapping("/preview")
    public PreviewResponse preview(@RequestBody ApiGeneratorRequest request) {
        return generatorService.preview(request);
    }

    @PostMapping(value = "/generate", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@RequestBody ApiGeneratorRequest request) throws IOException {
        GeneratedZip zip = generatorService.generateZip(request);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(zip.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(zip.content());
    }
}
