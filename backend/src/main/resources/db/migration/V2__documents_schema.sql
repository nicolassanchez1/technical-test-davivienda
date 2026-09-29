CREATE TABLE documents (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    title             text         NOT NULL,
    author            text         NOT NULL,
    category          text         NOT NULL,
    tags              text[]       NOT NULL DEFAULT '{}',
    version           text         NOT NULL,
    original_filename text         NOT NULL,
    mime_type         text         NOT NULL,
    size_bytes        bigint       NOT NULL,
    storage_key       text         NOT NULL,
    sha256            char(64)     NOT NULL,
    status            text         NOT NULL,
    error_code        text,
    error_message     text,
    page_count        integer,
    chunk_count       integer,
    processing_ms     bigint,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    indexed_at        timestamptz,

    CONSTRAINT documents_sha256_unique UNIQUE (sha256),
    CONSTRAINT documents_status_allowed
        CHECK (status IN ('PROCESANDO', 'INDEXADO', 'ERROR')),
    CONSTRAINT documents_category_allowed
        CHECK (category IN ('MANUAL', 'SPECIFICATION', 'ARCHITECTURE_GUIDE', 'OTHER')),
    CONSTRAINT documents_error_code_allowed
        CHECK (error_code IS NULL OR error_code IN (
            'PDF_NO_TEXT_LAYER', 'UNSUPPORTED_FORMAT', 'CORRUPT_FILE',
            'EMPTY_CONTENT', 'PROCESSING_FAILED')),
    -- A failure must say why, and only a failed document may carry a reason.
    CONSTRAINT documents_error_code_matches_status
        CHECK ((status = 'ERROR') = (error_code IS NOT NULL)),
    CONSTRAINT documents_size_bytes_positive CHECK (size_bytes > 0)
);

CREATE INDEX documents_status_idx   ON documents (status);
CREATE INDEX documents_category_idx ON documents (category);
CREATE INDEX documents_author_idx   ON documents (author);
CREATE INDEX documents_tags_idx     ON documents USING gin (tags);
CREATE INDEX documents_created_at_idx ON documents (created_at DESC, id DESC);

-- Documents are split into chunks so that ts_headline re-parses a bounded amount of text,
-- a single tsvector stays under the 1 MB limit, and lexeme positions never saturate.
CREATE TABLE document_chunks (
    id            bigint    GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id   uuid      NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index   integer   NOT NULL,
    page          integer,
    heading       text,
    content       text      NOT NULL,
    search_vector tsvector  NOT NULL,

    CONSTRAINT document_chunks_order_unique UNIQUE (document_id, chunk_index),
    CONSTRAINT document_chunks_index_not_negative CHECK (chunk_index >= 0)
);

CREATE INDEX document_chunks_search_vector_idx ON document_chunks USING gin (search_vector);
CREATE INDEX document_chunks_document_idx      ON document_chunks (document_id, chunk_index);
