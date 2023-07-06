UPDATE soknad
SET soknad = jsonb_set(soknad, '{sporsmal}', (
    SELECT jsonb_agg(sporsmal)
    FROM jsonb_array_elements(soknad->'sporsmal') sporsmal
    WHERE sporsmal->>'tag' <> 'YRKESSKADE_V2'
))
WHERE soknad->'sporsmal' @> '[{"tag": "YRKESSKADE_V2"}]'
  and timestamp > '2023-07-05' and timestamp < '2023-07-07';