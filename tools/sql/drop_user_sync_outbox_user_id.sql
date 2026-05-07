-- Rimuove colonna ridondante: l'id utente middleware resta nel JSON payload se serve all'app web.
ALTER TABLE user_sync_outbox DROP COLUMN user_id;
