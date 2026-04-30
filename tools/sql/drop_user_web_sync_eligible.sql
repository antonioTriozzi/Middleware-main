-- Colonna non mappata in JPA (User.java). Esegui sul DB del middleware (es. testtesi).
-- Se ricevi "Unknown column", verifica il nome esatto: SHOW COLUMNS FROM `user` LIKE '%sync%';

ALTER TABLE `user` DROP COLUMN `web_sync_eligible`;
