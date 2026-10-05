-- V4__make_date_of_birth_nullable.sql
-- Purpose: Make date_of_birth nullable to support OAuth users who don't provide DOB
-- Reason: GitHub OAuth doesn't return date of birth, so we need to allow NULL values

ALTER TABLE client_profiles
ALTER COLUMN date_of_birth DROP NOT NULL;
