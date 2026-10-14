-- Aggiunta della colonna status alla tabella processed_questionnaires per la gestione delle bozze
ALTER TABLE processed_questionnaires ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED';
