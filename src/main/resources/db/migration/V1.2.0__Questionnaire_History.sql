-- Creazione della tabella per lo storico dei questionari completati
CREATE TABLE processed_questionnaires (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    questions_count INTEGER NOT NULL,
    data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Abilitazione della sicurezza Row-Level Security (RLS)
ALTER TABLE processed_questionnaires ENABLE ROW LEVEL SECURITY;

-- Definizione della policy di isolamento per tenant
CREATE POLICY processed_questionnaires_tenant_isolation ON processed_questionnaires
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
