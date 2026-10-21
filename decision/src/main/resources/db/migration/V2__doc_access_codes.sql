CREATE TABLE doc_access_codes (id uuid PRIMARY KEY, document_id uuid NOT NULL REFERENCES documents(id), code varchar(12) NOT NULL, expires_at timestamptz NOT NULL, used_at timestamptz);
CREATE INDEX idx_doc_access_document ON doc_access_codes(document_id);
