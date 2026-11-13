CREATE TABLE model_version (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(100) NOT NULL,
    base_model VARCHAR(200),
    artifact_uri VARCHAR(1000) NOT NULL,
    artifact_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_model_name_version UNIQUE (name, version)
);

CREATE TABLE evaluation_run (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    model_version_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    config_snapshot TEXT NOT NULL,
    score_snapshot TEXT,
    attempt INT NOT NULL,
    max_attempts INT NOT NULL,
    success_count INT,
    failure_count INT,
    timeout_count INT,
    success_rate DECIMAL(10,6),
    mean_success_steps DECIMAL(12,2),
    mean_jitter DECIMAL(12,6),
    total_score DECIMAL(12,4),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    version BIGINT NOT NULL,
    CONSTRAINT fk_evaluation_model FOREIGN KEY (model_version_id) REFERENCES model_version (id)
);
CREATE INDEX idx_evaluation_status ON evaluation_run (status);
CREATE INDEX idx_evaluation_model ON evaluation_run (model_version_id);

CREATE TABLE evaluation_episode (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    evaluation_run_id VARCHAR(36) NOT NULL,
    episode_index INT NOT NULL,
    scene_seed INT NOT NULL,
    result VARCHAR(20) NOT NULL,
    steps INT NOT NULL,
    jitter DECIMAL(12,6),
    instruction TEXT,
    video_uri VARCHAR(1000),
    arm_tag VARCHAR(50),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_episode_run FOREIGN KEY (evaluation_run_id) REFERENCES evaluation_run (id),
    CONSTRAINT uk_episode_run_index UNIQUE (evaluation_run_id, episode_index),
    CONSTRAINT uk_episode_run_seed UNIQUE (evaluation_run_id, scene_seed)
);
CREATE INDEX idx_episode_run ON evaluation_episode (evaluation_run_id);

CREATE TABLE upload_session (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    total_size BIGINT NOT NULL,
    chunk_size INT NOT NULL,
    total_chunks INT NOT NULL,
    expected_sha256 VARCHAR(64) NOT NULL,
    actual_sha256 VARCHAR(64),
    uploaded_bytes BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    storage_path VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL
);
CREATE INDEX idx_upload_status ON upload_session (status);

CREATE TABLE upload_chunk (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    upload_session_id VARCHAR(36) NOT NULL,
    part_number INT NOT NULL,
    chunk_size BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chunk_upload FOREIGN KEY (upload_session_id) REFERENCES upload_session (id),
    CONSTRAINT uk_chunk_upload_part UNIQUE (upload_session_id, part_number)
);
CREATE INDEX idx_chunk_upload ON upload_chunk (upload_session_id);

CREATE TABLE audit_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    entity_type VARCHAR(40) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    action VARCHAR(80) NOT NULL,
    actor VARCHAR(100) NOT NULL,
    request_id VARCHAR(100),
    detail_json TEXT,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_audit_entity ON audit_log (entity_type, entity_id, created_at);
