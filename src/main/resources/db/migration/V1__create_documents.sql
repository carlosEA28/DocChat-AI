
CREATE TABLE documents (
                           id UUID PRIMARY KEY,
                           filename VARCHAR(255) NOT NULL,
                           s3_key VARCHAR(512) NOT NULL UNIQUE,
                           content_type VARCHAR(100) NOT NULL,
                           size BIGINT NOT NULL,
                           status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                           created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                           CONSTRAINT chk_document_size CHECK (size > 0),
    CONSTRAINT chk_document_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')
    )
);
