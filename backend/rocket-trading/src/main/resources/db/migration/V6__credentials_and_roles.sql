-- BR-01 / 9.3: password sign-in (OAuth-only users keep a NULL hash) and role-limited internal access
ALTER TABLE client_profiles ADD COLUMN password_hash TEXT;
ALTER TABLE client_profiles ADD COLUMN user_role VARCHAR(20) NOT NULL DEFAULT 'CLIENT';
ALTER TABLE client_profiles ADD CONSTRAINT supported_user_role
    CHECK (user_role IN ('CLIENT', 'ANALYST', 'OPERATIONS'));

-- Every staff read of a client's trade record is itself recorded and can never be edited
CREATE TABLE staff_access_log (
    access_id           BIGSERIAL PRIMARY KEY,
    staff_client_id     BIGINT NOT NULL REFERENCES client_profiles(client_id),
    resource            TEXT NOT NULL,
    accessed_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER protect_staff_access BEFORE UPDATE OR DELETE ON staff_access_log
    FOR EACH ROW EXECUTE FUNCTION prevent_record_changes();
