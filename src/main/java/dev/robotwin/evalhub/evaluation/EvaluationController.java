package dev.robotwin.evalhub.evaluation;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final EvaluationService service;

    public EvaluationController(EvaluationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EvaluationResponse> create(@Valid @RequestBody CreateEvaluationRequest request) {
        EvaluationResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/evaluations/" + response.id())).body(response);
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<EvaluationResponse> start(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.start(id));
    }

    @PostMapping("/{id}/results")
    public EvaluationResponse importResults(@PathVariable UUID id, @Valid @RequestBody ScoreAuditImportRequest request) {
        return service.importResults(id, request);
    }

    @GetMapping
    public List<EvaluationResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public EvaluationResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/episodes")
    public List<EvaluationEpisodeResponse> episodes(@PathVariable UUID id) {
        return service.episodes(id);
    }
}
