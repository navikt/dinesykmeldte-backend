CREATE TABLE hendelser(
    id                    VARCHAR                  NOT NULL,
    pasient_fnr           VARCHAR                  NOT NULL,
    orgnummer             VARCHAR                  NOT NULL,
    oppgavetype           VARCHAR                  NOT NULL,
    lenke                 VARCHAR                  NULL,
    tekst                 VARCHAR                  NULL,
    timestamp             TIMESTAMP WITH TIME ZONE NOT NULL,
    utlopstidspunkt       TIMESTAMP WITH TIME ZONE NULL,
    ferdigstilt           BOOLEAN                  NULL,
    ferdigstilt_timestamp TIMESTAMP WITH TIME ZONE NULL,
    PRIMARY KEY (id, oppgavetype)
);

CREATE INDEX hendelser_fnr_orgnr_idx ON hendelser (pasient_fnr, orgnummer);
