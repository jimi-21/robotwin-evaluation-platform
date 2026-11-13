package dev.robotwin.evalhub.upload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, UUID> {

    Optional<UploadChunk> findByUploadSessionIdAndPartNumber(UUID uploadSessionId, int partNumber);

    List<UploadChunk> findByUploadSessionIdOrderByPartNumberAsc(UUID uploadSessionId);
}
