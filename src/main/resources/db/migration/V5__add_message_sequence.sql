ALTER TABLE messages ADD COLUMN sequence_number BIGINT;

WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY created_at, id) AS sequence_number
    FROM messages
)
UPDATE messages AS message
SET sequence_number = numbered.sequence_number
FROM numbered
WHERE message.id = numbered.id;

ALTER TABLE messages ALTER COLUMN sequence_number SET NOT NULL;
ALTER TABLE messages ADD CONSTRAINT chk_messages_sequence_positive CHECK (sequence_number > 0);
ALTER TABLE messages ADD CONSTRAINT uq_messages_conversation_sequence UNIQUE (conversation_id, sequence_number);
