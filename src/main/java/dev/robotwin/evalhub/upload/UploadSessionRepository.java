package dev.robotwin.evalhub.upload;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadSessionRepository extends JpaRepository<UploadSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from UploadSession session where session.id = :id")
    Optional<UploadSession> findLockedById(@Param("id") UUID id);
}
