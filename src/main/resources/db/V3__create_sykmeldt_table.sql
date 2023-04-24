CREATE TABLE sykmeldt (
    pasient_fnr VARCHAR primary key not null,
    pasient_navn VARCHAR not null,
    startdato_sykefravaer DATE not null,
    latest_tom DATE not null
);
