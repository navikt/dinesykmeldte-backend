DO
$$
BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'dinesykmeldte-kafka-user')
        THEN
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "dinesykmeldte-kafka-user";
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO "dinesykmeldte-kafka-user";
END IF;
END
$$;
