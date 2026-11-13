package dev.robotwin.evalhub.evaluation;

import dev.robotwin.evalhub.model.ModelVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_run")
public class EvaluationRun {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersion modelVersion;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvaluationStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(columnDefinition = "TEXT")
    private String scoreSnapshot;

    private int attempt;
    private int maxAttempts;
    private Integer successCount;
    private Integer failureCount;
    private Integer timeoutCount;
    private BigDecimal successRate;
    private BigDecimal meanSuccessSteps;
    private BigDecimal meanJitter;
    private BigDecimal totalScore;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    @Version
    private long version;

    protected EvaluationRun() {
    }

    public EvaluationRun(ModelVersion modelVersion, String name, String configSnapshot, int maxAttempts) {
        this.id = UUID.randomUUID();
        this.modelVersion = modelVersion;
        this.name = name;
        this.configSnapshot = configSnapshot;
        this.maxAttempts = maxAttempts;
        this.status = EvaluationStatus.PENDING;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void start() {
        this.status = EvaluationStatus.RUNNING;
        this.attempt++;
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.errorMessage = null;
        this.scoreSnapshot = null;
        this.totalScore = null;
        this.successCount = null;
        this.failureCount = null;
        this.timeoutCount = null;
        this.successRate = null;
        this.meanSuccessSteps = null;
        this.meanJitter = null;
    }

    public void succeed() {
        this.status = EvaluationStatus.SUCCEEDED;
        this.finishedAt = Instant.now();
        this.errorMessage = null;
    }

    public void fail(String message) {
        this.status = EvaluationStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ModelVersion getModelVersion() {
        return modelVersion;
    }

    public String getName() {
        return name;
    }

    public EvaluationStatus getStatus() {
        return status;
    }

    public String getConfigSnapshot() {
        return configSnapshot;
    }

    public String getScoreSnapshot() {
        return scoreSnapshot;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Integer getSuccessCount() {
        return successCount;
    }

    public Integer getFailureCount() {
        return failureCount;
    }

    public Integer getTimeoutCount() {
        return timeoutCount;
    }

    public BigDecimal getSuccessRate() {
        return successRate;
    }

    public BigDecimal getMeanSuccessSteps() {
        return meanSuccessSteps;
    }

    public BigDecimal getMeanJitter() {
        return meanJitter;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setMetrics(int successCount, int failureCount, int timeoutCount, BigDecimal successRate,
            BigDecimal meanSuccessSteps, BigDecimal meanJitter) {
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.timeoutCount = timeoutCount;
        this.successRate = successRate;
        this.meanSuccessSteps = meanSuccessSteps;
        this.meanJitter = meanJitter;
    }

    public void setScoreSnapshot(String scoreSnapshot, BigDecimal totalScore) {
        this.scoreSnapshot = scoreSnapshot;
        this.totalScore = totalScore;
    }
}
