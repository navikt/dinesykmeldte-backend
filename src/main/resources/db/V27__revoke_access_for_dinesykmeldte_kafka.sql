REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM "dinesykmeldte-write-user";
REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM "dinesykmeldte-kafka-user";
REVOKE ALL PRIVILEGES ON database "dinesykmeldte-backend" FROM "dinesykmeldte-write-user";
REVOKE ALL PRIVILEGES ON database "dinesykmeldte-backend" FROM "dinesykmeldte-kafka-user";

