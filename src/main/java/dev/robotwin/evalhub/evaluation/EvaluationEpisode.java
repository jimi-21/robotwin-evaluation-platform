package dev.robotwin.evalhub.evaluation;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_episode")
public class EvaluationEpisode {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_run_id", nullable = false)
    private EvaluationRun evaluationRun;

    private int episodeIndex;
    private int sceneSeed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EpisodeResult result;

    private int steps;
    private BigDecimal jitter;

    @Column(columnDefinition = "TEXT")
    private String instruction;
    private String videoUri;
    private String armTag;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    protected EvaluationEpisode() {
    }

    public EvaluationEpisode(EvaluationRun evaluationRun, int episodeIndex, int sceneSeed, EpisodeResult result,
            int steps, BigDecimal jitter, String instruction, String videoUri, String armTag, String failureReason) {
        this.id = UUID.randomUUID();
        this.evaluationRun = evaluationRun;
        this.episodeIndex = episodeIndex;
        this.sceneSeed = sceneSeed;
        this.result = result;
        this.steps = steps;
        this.jitter = jitter;
        this.instruction = instruction;
        this.videoUri = videoUri;
        this.armTag = armTag;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public EvaluationRun getEvaluationRun() {
        return evaluationRun;
    }

    public int getEpisodeIndex() {
        return episodeIndex;
    }

    public int getSceneSeed() {
        return sceneSeed;
    }

    public EpisodeResult getResult() {
        return result;
    }

    public int getSteps() {
        return steps;
    }

    public BigDecimal getJitter() {
        return jitter;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getVideoUri() {
        return videoUri;
    }

    public String getArmTag() {
        return armTag;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
