
CREATE TABLE conversations (
                               id UUID PRIMARY KEY,
                               document_id UUID NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               CONSTRAINT fk_conversations_document
                                   FOREIGN KEY (document_id)
                                       REFERENCES documents(id)
                                       ON DELETE CASCADE
);

CREATE INDEX idx_conversations_document_id
    ON conversations(document_id);
