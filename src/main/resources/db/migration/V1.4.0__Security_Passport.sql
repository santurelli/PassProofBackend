-- Creazione della tabella per il profilo pubblico di sicurezza (Trust Profile)
CREATE TABLE trust_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE REFERENCES tenants(id) ON DELETE CASCADE,
    public_name VARCHAR(255) NOT NULL,
    company_description TEXT,
    security_measures JSONB NOT NULL,
    published BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Abilitazione RLS
ALTER TABLE trust_profiles ENABLE ROW LEVEL SECURITY;

-- Policy di isolamento per tenant
CREATE POLICY trust_profiles_tenant_isolation ON trust_profiles
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);

-- Creazione della tabella per le firme NDA (Accordo di Riservatezza)
CREATE TABLE nda_signatures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    signed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Abilitazione RLS
ALTER TABLE nda_signatures ENABLE ROW LEVEL SECURITY;

-- Policy di isolamento per tenant
CREATE POLICY nda_signatures_tenant_isolation ON nda_signatures
    FOR ALL USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
