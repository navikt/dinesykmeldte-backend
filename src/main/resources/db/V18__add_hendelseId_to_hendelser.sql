CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
alter table hendelser add column hendelse_id UUID unique not null default uuid_generate_v4();