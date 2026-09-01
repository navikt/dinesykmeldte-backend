# PDL-feil har satt en Kafka-partisjon i karantene

Alerten `PdlKafkaPartitionQuarantined` betyr at et PDL-oppslag fortsatt feilet etter det
lokale retrybudsjettet. Retrybare feil får først korte forsøk; permanente feil går direkte til
15 minutters cooldown. Én mislykket half-open probe setter partisjonen i karantene. Den
feilede offseten er ikke committet, og andre Kafka-partisjoner behandles videre.

Karantene-state ligger i consumer-instansen. En rebalance, en generell consumer-restart,
pod-restart eller deploy kan derfor flytte partisjonen og starte et nytt avgrenset retrybudsjett.
Dette er tilsiktet for å bevare Kafka-rekkefølge og unngå automatisk datatap; terminal counter
og `kafka_pdl_retry_exhausted` gir historikk selv om den aktive gauge-serien forsvinner.

## Undersøk

1. Finn den nyeste strukturerte logghendelsen med
   `event_type=kafka_pdl_retry_exhausted`. Feltene `kafka_topic`, `kafka_partition` og
   `kafka_offset` identifiserer karantenen uten å eksponere meldingsinnhold.
2. Finn den tilhørende `event_type=pdl_personoppslag_failed` for samme Kafka-koordinater.
   Bruk `error_code`, `retryable`, `upstream_status` og `cause_type` til å avgjøre om
   årsaken er PDL, tokenoppsett, nettverk eller responsformat.
3. Kontroller PDL-status og relevante applikasjonsendringer. Ikke logg eller kopier
   Kafka-meldingen, fødselsnummer, token eller HTTP-responsbody.

## Frigi partisjonen

1. Rett eller avklar årsaken først. Ikke commit eller hopp over offseten uten en eksplisitt
   domenebeslutning om at meldingen kan tapes.
2. Finn podden med
   `dinesykmeldte_backend_kafka_pdl_quarantined_partitions > 0` og restart den kontrollert.
   Ved restart eller rebalance leveres den ucommittede offseten på nytt og får et nytt, lokalt
   retrybudsjett.
3. Verifiser at karantenemetrikken går tilbake til `0`, at offseten blir committet og at
   Kafka-laggen synker. Hvis samme offset går tilbake i karantene, la alerten stå og eskaler
   med Kafka-koordinatene og PDL-feilkoden.

Kontakt team-esyfo i `#esyfo` ved behov.
