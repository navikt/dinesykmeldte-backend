create index sykmelding_fnr_orgnummer_idx on sykmelding (pasient_fnr, orgnummer);
create index sykmelding_latest_tom_idx on sykmelding(latest_tom);
