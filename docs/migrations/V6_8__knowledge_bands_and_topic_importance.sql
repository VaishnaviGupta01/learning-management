-- Phases 6-8 changes for databases created before them.
-- Hibernate (JPA_DDL_AUTO=update) adds new columns but never rewrites CHECK constraints,
-- so an existing database must be patched once. Safe to run more than once.
--
--   docker compose exec -T postgres psql -U lms -d lms < docs/migrations/V6_8__knowledge_bands_and_topic_importance.sql

-- Knowledge bands: DEVELOPING -> NEEDS_PRACTICE / MODERATE
ALTER TABLE student_topic_knowledge DROP CONSTRAINT IF EXISTS student_topic_knowledge_classification_check;
UPDATE student_topic_knowledge SET classification = 'NEEDS_PRACTICE' WHERE classification = 'DEVELOPING';
ALTER TABLE student_topic_knowledge ADD CONSTRAINT student_topic_knowledge_classification_check
    CHECK (classification IN ('NOT_STARTED', 'WEAK', 'NEEDS_PRACTICE', 'MODERATE', 'STRONG'));

-- Configurable topic importance used by the recommendation engine
ALTER TABLE topics ADD COLUMN IF NOT EXISTS importance DOUBLE PRECISION NOT NULL DEFAULT 0.5;
