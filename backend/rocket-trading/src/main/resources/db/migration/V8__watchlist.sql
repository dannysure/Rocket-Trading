-- BR-18: a client's watchlist of instruments they follow but may not yet hold
CREATE TABLE client_watchlist (
    client_id           BIGINT NOT NULL REFERENCES client_profiles(client_id),
    instrument_id       BIGINT NOT NULL REFERENCES financial_instruments(instrument_id),
    added_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id, instrument_id)
);
