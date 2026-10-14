-- Migrazione V1.6.0: Forza la Row-Level Security anche per gli account proprietari delle tabelle (Owner / Superuser)
-- Impedisce il bypass automatico di RLS da parte delle connessioni del connection pool dell'applicazione

ALTER TABLE tenant_knowledge_vectors FORCE ROW LEVEL SECURITY;
ALTER TABLE processed_questionnaires FORCE ROW LEVEL SECURITY;
ALTER TABLE trust_profiles FORCE ROW LEVEL SECURITY;
ALTER TABLE nda_signatures FORCE ROW LEVEL SECURITY;
