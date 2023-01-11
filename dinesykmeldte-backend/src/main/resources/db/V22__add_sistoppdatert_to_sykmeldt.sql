alter table sykmeldt add column sist_oppdatert DATE;

create index sykmeldt_sist_oppdatert_idx on sykmeldt(sist_oppdatert);