package dev.robotwin.evalhub.upload;

import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UploadProgressResponse> initialize(@Valid @RequestBody CreateUploadRequest request) {
        UploadProgressResponse response = service.initialize(request);
        return ResponseEntity.created(URI.create("/api/v1/uploads/" + response.uploadId())).body(response);
    }

    @PutMapping(value = "/{uploadId}/chunks/{partNumber}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public UploadProgressResponse uploadChunk(@PathVariable UUID uploadId, @PathVariable int partNumber,
            @RequestHeader("X-Chunk-SHA256") String sha256, @RequestBody byte[] content) {
        return service.uploadChunk(uploadId, partNumber, sha256, content);
    }

    @GetMapping("/{uploadId}")
    public UploadProgressResponse progress(@PathVariable UUID uploadId) {
        return service.progress(uploadId);
    }

    @PostMapping("/{uploadId}/complete")
    public UploadProgressResponse complete(@PathVariable UUID uploadId) {
        return service.complete(uploadId);
    }

    @GetMapping("/{uploadId}/content")
    public ResponseEntity<UrlResource> download(@PathVariable UUID uploadId) throws Exception {
        UploadService.DownloadFile file = service.download(uploadId);
        UrlResource resource = new UrlResource(file.path().toUri());
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-SHA256", file.sha256())
                .body(resource);
    }
}
