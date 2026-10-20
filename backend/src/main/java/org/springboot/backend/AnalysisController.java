package org.springboot.backend;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
class AnalysisController {
    private final AnalysisService service;
    private final AnalysisExports exports;
    private final RepoMedicConfig.Settings settings;

    AnalysisController(AnalysisService service, AnalysisExports exports, RepoMedicConfig.Settings settings) {
        this.service = service; this.exports = exports; this.settings = settings;
    }

    @GetMapping("/health")
    Map<String, Object> health() { return Map.of("status", "ok", "aiEnabled", settings.aiEnabled()); }

    @GetMapping("/analyses")
    List<Analysis> list() { return service.list(); }

    @PostMapping("/analyses/demo")
    Analysis demo() { return service.demo(); }

    @PostMapping(value = "/analyses/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Analysis upload(@RequestPart("file") MultipartFile file) { return service.upload(file); }

    record ImportRequest(String url) { }

    @PostMapping(value = "/analyses/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    Analysis importRepository(@RequestBody ImportRequest request) { return service.importGitHub(request.url()); }

    @GetMapping("/analyses/{id}")
    Analysis get(@PathVariable String id) { return service.get(id); }

    @PostMapping("/analyses/{id}/validate")
    Analysis validate(@PathVariable String id) { return service.validate(id); }

    @GetMapping("/analyses/{id}/report")
    ResponseEntity<String> report(@PathVariable String id) {
        return download(exports.report(id), "text/markdown", "repomedic-" + id + ".md");
    }

    @GetMapping("/analyses/{id}/patch")
    ResponseEntity<String> patch(@PathVariable String id) {
        return download(exports.patch(id), "text/plain", "repomedic-" + id + ".patch");
    }

    private static ResponseEntity<String> download(String body, String type, String name) {
        return ResponseEntity.ok().contentType(new MediaType(MediaType.parseMediaType(type), StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
