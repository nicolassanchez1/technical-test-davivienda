-- Accent-insensitive Spanish full-text search configuration.
-- Searching "especificacion tecnica" must find "Especificación técnica", so every indexed
-- lexeme is passed through unaccent before the Spanish stemmer.

CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TEXT SEARCH CONFIGURATION es_unaccent (COPY = spanish);

ALTER TEXT SEARCH CONFIGURATION es_unaccent
    ALTER MAPPING FOR hword, hword_part, word WITH unaccent, spanish_stem;
