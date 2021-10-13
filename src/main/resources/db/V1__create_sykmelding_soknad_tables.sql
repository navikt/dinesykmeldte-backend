CREATE TABLE narmesteleder (
    narmeste_leder_id VARCHAR primary key not null,
    pasient_fnr VARCHAR not null,
    leder_fnr VARCHAR not null,
    orgnummer VARCHAR not null
);

CREATE TABLE sykmelding (
    sykmelding_id VARCHAR primary key not null,
    pasient_fnr VARCHAR not null,
    pasient_navn VARCHAR not null,
    orgnummer VARCHAR not null,
    orgnavn VARCHAR null,
    startdato_sykefravaer DATE not null,
    sykmelding JSONB not null,
    lest BOOLEAN not null,
    timestamp TIMESTAMP with time zone not null,
    latest_tom DATE not null
);

CREATE TABLE soknad (
    soknad_id VARCHAR primary key not null,
    sykmelding_id VARCHAR not null,
    pasient_fnr VARCHAR not null,
    orgnummer VARCHAR not null,
    soknad JSONB not null,
    sendt_dato DATE null,
    lest BOOLEAN not null,
    timestamp TIMESTAMP with time zone not null,
    latest_tom DATE not null
);

create index sykmelding_fnr_idx on sykmelding(pasient_fnr);
create index soknad_fnr_idx on soknad(pasient_fnr);
create index narmesteleder_lederfnr_idx on narmesteleder(leder_fnr);
