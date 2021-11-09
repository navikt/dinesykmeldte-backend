CREATE TABLE hendelser(
    id VARCHAR primary key not null,
    pasient_fnr VARCHAR not null,
    orgnummer VARCHAR not null,
    oppgavetype VARCHAR not null,
    lenke VARCHAR null,
    tekst VARCHAR null,
    timestamp TIMESTAMP with time zone not null,
    utlopstidspunkt TIMESTAMP with time zone null,
    ferdigstilt BOOLEAN null,
    ferdigstilt_timestamp TIMESTAMP with time zone null
);

create index hendelser_fnr_orgnr_idx on hendelser(pasient_fnr, orgnummer);
