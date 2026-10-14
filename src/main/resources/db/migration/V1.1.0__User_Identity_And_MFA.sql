-- Creazione della tabella Tenants (Aziende)
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name VARCHAR(255) NOT NULL,
    vat_number VARCHAR(50) NOT NULL,
    billing_data JSONB NOT NULL, -- PEC, SDI, Indirizzo
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Creazione della tabella Users (Utenti)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    mfa_secret VARCHAR(128), -- Chiave TOTP crittografata a riposo dall'applicazione
    mfa_configured BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_backup_codes VARCHAR(255)[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Creazione della tabella Tenant Memberships (Tabella di giunzione molti-a-molti utenti-tenants)
CREATE TABLE tenant_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL, -- OWNER, EDITOR, VIEWER
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_tenant_membership UNIQUE (user_id, tenant_id)
);

-- Creazione della tabella User Invitations (Inviti collaboratori)
CREATE TABLE user_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Creazione di indici per ottimizzazione delle query di login e risoluzione tenant
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_tenant_memberships_user ON tenant_memberships(user_id);
CREATE INDEX idx_tenant_memberships_tenant ON tenant_memberships(tenant_id);
CREATE INDEX idx_user_invitations_token ON user_invitations(token);
