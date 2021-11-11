CREATE TABLE hendelser (
    hendelse_id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id                    VARCHAR                  NOT NULL,
    pasient_fnr           VARCHAR                  NOT NULL,
    orgnummer             VARCHAR                  NOT NULL,
    oppgavetype           VARCHAR                  NOT NULL,
    lenke                 VARCHAR                  NULL,
    tekst                 VARCHAR                  NULL,
    timestamp             TIMESTAMP WITH TIME ZONE NOT NULL,
    utlopstidspunkt       TIMESTAMP WITH TIME ZONE NULL,
    ferdigstilt           BOOLEAN                  NULL,
    ferdigstilt_timestamp TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX hendelser_fnr_orgnr_idx ON hendelser (pasient_fnr, orgnummer);
CREATE INDEX hendelser_id_oppgt_idx ON hendelser (id, oppgavetype);
