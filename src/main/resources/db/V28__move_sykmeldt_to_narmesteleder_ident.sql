-- Move one sykmeldt's rows at one employer to the ident on the narmesteleder relation.
-- Idents are read from the rows and never appear in this file or in the log output.
-- No-op if the rows don't match, e.g. in dev and in tests.
DO
$$
DECLARE
    v_fra          text;
    v_til          text;
    v_orgnummer    text;
    v_nl_fra       int;
    v_sykmeldinger int;
    v_soknader     int;
    v_hendelser    int;
BEGIN
    SELECT s.pasient_fnr, nl.pasient_fnr, nl.orgnummer
    INTO v_fra, v_til, v_orgnummer
    FROM narmesteleder nl
             JOIN sykmelding s ON s.orgnummer = nl.orgnummer AND s.pasient_fnr <> nl.pasient_fnr
    WHERE nl.narmeste_leder_id = 'c7f0903c-2b6e-426e-9a8a-6a615d5f36df'
      AND s.sykmelding_id = '7502f6a5-5ea4-44ec-8065-9f989a1843dc';

    IF NOT FOUND THEN
        RAISE NOTICE 'V28: no matching rows, skipping';
        RETURN;
    END IF;

    SELECT count(*) INTO v_nl_fra FROM narmesteleder WHERE pasient_fnr = v_fra AND orgnummer = v_orgnummer;
    IF v_nl_fra > 0 THEN
        RAISE NOTICE 'V28: % narmesteleder relation(s) on the old ident at the same employer, skipping', v_nl_fra;
        RETURN;
    END IF;

    -- The sykmeldt row with the latest latest_tom wins
    INSERT INTO sykmeldt (pasient_fnr, pasient_navn, startdato_sykefravaer, latest_tom, sist_oppdatert)
    SELECT v_til, pasient_navn, startdato_sykefravaer, latest_tom, sist_oppdatert
    FROM sykmeldt
    WHERE pasient_fnr = v_fra
    ON CONFLICT (pasient_fnr) DO UPDATE
        SET pasient_navn          = excluded.pasient_navn,
            startdato_sykefravaer = excluded.startdato_sykefravaer,
            latest_tom            = excluded.latest_tom,
            sist_oppdatert        = GREATEST(sykmeldt.sist_oppdatert, excluded.sist_oppdatert)
    WHERE excluded.latest_tom > sykmeldt.latest_tom;

    UPDATE sykmelding SET pasient_fnr = v_til WHERE pasient_fnr = v_fra AND orgnummer = v_orgnummer;
    GET DIAGNOSTICS v_sykmeldinger = ROW_COUNT;
    UPDATE soknad SET pasient_fnr = v_til WHERE pasient_fnr = v_fra AND orgnummer = v_orgnummer;
    GET DIAGNOSTICS v_soknader = ROW_COUNT;
    UPDATE hendelser SET pasient_fnr = v_til WHERE pasient_fnr = v_fra AND orgnummer = v_orgnummer;
    GET DIAGNOSTICS v_hendelser = ROW_COUNT;

    RAISE NOTICE 'V28: moved % sykmelding(er), % soknad(er), % hendelse(r)', v_sykmeldinger, v_soknader, v_hendelser;
END
$$;
