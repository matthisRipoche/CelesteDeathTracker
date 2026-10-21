-- Connexion via Discord : un compte peut exister sans email ni mot de passe (créé par Discord).
-- Les contraintes UNIQUE acceptent plusieurs NULL : plusieurs comptes Discord sans email sont possibles.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- discord_id = identifiant numérique stable (le pseudo, lui, peut changer).
ALTER TABLE users ADD COLUMN discord_id       VARCHAR(32) UNIQUE;
ALTER TABLE users ADD COLUMN discord_username VARCHAR(64);
ALTER TABLE users ADD COLUMN discord_avatar   VARCHAR(64);
