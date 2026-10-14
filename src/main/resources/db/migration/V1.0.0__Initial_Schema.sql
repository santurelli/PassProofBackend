-- Abilitazione dell'estensione pgvector (se non presente)
CREATE EXTENSION IF NOT EXISTS vector;

-- Creazione della tabella per la Knowledge Base vettoriale dei Tenant
CREATE TABLE tenant_knowledge_vectors (
    id BIGSERIAL PRIMARY KEY,
    tenant_id UUID NOT NULL,
    document_reference VARCHAR(255),
    content TEXT NOT NULL,
    embedding vector(1536) NOT NULL, -- Ottimizzato per OpenAI text-embedding-3-small
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Creazione dell'indice HNSW per ricerche vettoriali rapide
CREATE INDEX hnsw_tenant_vector_idx ON tenant_knowledge_vectors 
USING hnsw (embedding vector_cosine_ops);

-- Attivazione della Row-Level Security (RLS) sulla tabella
ALTER TABLE tenant_knowledge_vectors ENABLE ROW LEVEL SECURITY;

-- Definizione della policy di isolamento basata sulla variabile di sessione runtime
CREATE POLICY tenant_isolation_policy ON tenant_knowledge_vectors
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
