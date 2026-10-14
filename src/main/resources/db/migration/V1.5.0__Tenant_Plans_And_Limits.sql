-- Aggiunta dei campi per la gestione del piano tariffario e dei limiti commerciali
ALTER TABLE tenants ADD COLUMN plan_tier VARCHAR(50) NOT NULL DEFAULT 'STARTER';
ALTER TABLE tenants ADD COLUMN max_users INTEGER NOT NULL DEFAULT 3;
ALTER TABLE tenants ADD COLUMN max_questionnaires_per_month INTEGER NOT NULL DEFAULT 5;
