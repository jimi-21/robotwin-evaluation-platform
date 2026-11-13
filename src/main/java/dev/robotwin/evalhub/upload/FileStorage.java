package dev.robotwin.evalhub.upload;

import dev.robotwin.evalhub.common.ApiException;
import dev.robotwin.evalhub.config.StorageProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class FileStorage {

    private final Path root;

    public FileStorage(StorageProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
    }

    public Path saveChunk(UUID uploadId, int partNumber, byte[] content) {
        Path directory = root.resolve("uploads").resolve(uploadId.toString()).resolve("parts");
        try {
            Files.createDirectories(directory);
            Path temporary = directory.resolve(partNumber + "-" + UUID.randomUUID() + ".tmp");
            Files.write(temporary, content);
            Path target = directory.resolve(partNumber + ".part");
            move(temporary, target);
            return target;
        } catch (IOException exception) {
            throw storageError("Could not store upload chunk", exception);
        }
    }

    public StoredFile assemble(UUID uploadId, String fileName, List<UploadChunk> chunks, long expectedSize) {
        Path directory = root.resolve("uploads").resolve(uploadId.toString());
        Path temporary = directory.resolve("assembling-" + UUID.randomUUID() + ".tmp");
        Path target = directory.resolve("files").resolve(fileName);
        MessageDigest digest = sha256();
        long total = 0;
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream output = Files.newOutputStream(temporary)) {
                for (UploadChunk chunk : chunks) {
                    Path source = Path.of(chunk.getStoragePath());
                    try (InputStream input = Files.newInputStream(source);
                            DigestInputStream digestInput = new DigestInputStream(input, digest)) {
                        long copied = digestInput.transferTo(output);
                        total += copied;
                        if (copied != chunk.getChunkSize()) {
                            throw new IOException("Chunk size changed after upload: " + chunk.getPartNumber());
                        }
                    }
                }
            }
            if (total != expectedSize) {
                throw new IOException("Assembled file size does not match the declared size");
            }
            move(temporary, target);
            return new StoredFile(target, HexFormat.of().formatHex(digest.digest()), total);
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw storageError("Could not assemble uploaded file", exception);
        }
    }

    public void deleteChunk(UploadChunk chunk) {
        deleteQuietly(Path.of(chunk.getStoragePath()));
    }

    public void deleteFile(Path path) {
        deleteQuietly(path);
    }

    private String hash(byte[] content) {
        return HexFormat.of().formatHex(sha256().digest(content));
    }

    public String hashBytes(byte[] content) {
        return hash(content);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private ApiException storageError(String message, IOException exception) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", message, exception);
    }

    public record StoredFile(Path path, String sha256, long size) {
    }
}
