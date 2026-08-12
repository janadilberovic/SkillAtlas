# SkillAtlas — roadmap

Redoslijed izgradnje i status. **Ovo je fajl koji `/slice` čita** — kad se korak završi, ovdje se
ažurira status u istom PR-u.

Radna petlja: **jedan feature = jedna grana s `main` = jedan PR na `main`.** Nikad stacked —
vidi `CLAUDE.md` § *Git workflow*. Detalji feature-a su u [`spec.md`](spec.md).

**Zašto ovaj redoslijed:** finder, mentoring i dashboard su prazni dok ne postoje `KNOWS` veze.
Zato prvo ide unos znanja (E2.3), pa tek onda stvari koje ga čitaju.

| # | Korak | Spec | Status |
|---|---|---|---|
| [0] | Test infra + unique constraints | §04.4 | ✅ PR #6 |
| [1] | *(spojen u [0])* | — | ✅ |
| [2] | E2.3 · Moji skillovi — stvara `KNOWS` / `WANTS_TO_LEARN` | [E2.3](spec.md#e2--ljudi-skillovi-timovi-projekti-core) | ✅ PR #8 |
| [3] | E4.1 · Expert finder | [E4.1](spec.md#e4--expert-finder-i-pretraga-core--srce-aplikacije) | ✅ PR #10, dorada u #12 |
| **[4]** | **E4.2 · Bogat profil osobe** | [E4.2](spec.md#e4--expert-finder-i-pretraga-core--srce-aplikacije) | ⬅️ **sljedeći** |
| [5] | E5.1 · Graf endpoint + explorer | [E5.1](spec.md#e5--graf-vizualizacija-core) | ⬜ |
| [6] | E6.1/2/3 · Mentoring, learning path, dashboard | [E6](spec.md#e6--preporuke-i-dashboard-core-sječivo-po-tempu) | ⬜ |
| [7] | E3 · Import iz VacaYAY-a | [E3](spec.md#e3--import-iz-vacayay-a-core) | ⬜ |
| [8] | Dorade: change-password, logout, people search/filter, person↔team | §04.3 | ⬜ |
| [9] | Dopuna testova | §04.5 | ⬜ |
| [10] | Jedan Advanced (E7–E13) | [Advanced](spec.md#advanced-bira-se-jedan) | ⬜ |

Podrška uz gornje: graf domenski model i people/skills slice-ovi (#1, #2), projekti i timovi
(#3, #4), Angular frontend (#5), branching pravilo u `CLAUDE.md` (#7), CODEOWNERS (#9),
Nocturne form kontrole + `sa-select` (#11).

---

## [0] Test infra + constraints — gotovo

`SchemaInitializer` (unique constraints na `Person.email`, `Skill.name`, `Team.name`),
`AbstractNeo4jIT`, failsafe → `mvn verify`.

> **Odstupanje od prvobitnog plana:** Testcontainers je **izbačen** — docker-java ne može do
> Docker Desktopa preko Windows named pipe-a na ovoj mašini. Umjesto toga `AbstractNeo4jIT`
> čita `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` iz env-a, pa radi i s Neo4j Desktopom
> i s compose instancom i u CI-ju. **Ne vraćati Testcontainers** — to bi razdvojilo harness na
> dvije grane.

## [2] E2.3 · Moji skillovi — gotovo

`KNOWS` / `WANTS_TO_LEARN`, owner-only endpointi, IDOR guard, pravila iz `spec.md` §4.1.

## [3] E4.1 · Expert finder — gotovo

`GET /api/v1/experts` — AND preko `count(DISTINCT s) = $requiredCount`, rangiranje `sum(r.level)`,
soft-delete filter, obavezan Cypher-injection test. Dorada u PR #12: minimum level po skillu
(`skills=neo4j>=4,docker`), typeahead tag input, `GET /experts/coverage` (bus factor).

## [4] E4.2 · Bogat profil osobe — sljedeći

`GET /api/v1/people/{id}` se proširuje s plitkog `PersonResponse` na agregirani profil:
skillovi + leveli, želje, projekti + uloge + period, timovi, mentorstva **u oba smjera**,
mini-graf okoline (1–2 hopa, s `LIMIT`).

Predložene odluke (**nisu još potvrđene**):
1. Proširiti postojeći `GET /people/{id}` umjesto novog `/profile` — spec doslovno kaže da je to profil.
2. `COLLECT {}` subqueryji umjesto niza `OPTIONAL MATCH` — inače se redovi množe (5 skillova × 3 projekta × 2 menteeja = 30 redova). Traži Neo4j ≥ 5.6; provjeriti `CALL dbms.components()`.
3. Susjedstvo vraća backend (kepovano), a pravi force-graph vizual dolazi u [5].

Zamke: `Mentors.skillId` je skalar (treba `OPTIONAL MATCH (s:Skill {id: m.skillId})`);
soft-delete filter i na drugoj strani mentorstva; prazne grane vraćaju prazne liste, ne `null`.

Van opsega: dugmad „U grafu" (traži [5]) i „Predloži mentora" (traži [6]).

## [5] E5.1 · Graf endpoint

`GET /api/v1/graph?types=&team=&limit=` — podskup čvorova i veza za force-graph. **Mora imati
`LIMIT`.** Preuzima oblik `GraphNode`/`GraphEdge` uveden u [4].

## [6] E6 · Mentoring, learning path, dashboard

Mentor matching po pravilima iz `spec.md` §4.3 (kandidat `KNOWS` level ≥ 3, ne sam sebi,
rangiranje po levelu pa po opterećenosti), `POST /mentorships` tek na adminovu potvrdu —
prvi write `MENTORS` relacije. Learning path preko `shortestPath`. Dashboard: metrike +
skill gap po timu + bus factor + „čeka mapiranje skillova".

## [7] E3 · VacaYAY import

`POST /api/v1/people/import-vacayay`, idempotentno preko `MERGE` po emailu. MCP ugao: generisati
DTO-ve iz live OpenAPI spec-a starog sistema.

## [8] Dorade

Change-password, logout, people search/filter po timu i skillu, person↔team dodjela.
Uz to: migrirati preostale native `<select>`-ove (people, skills, projects, graph filteri) na
`sa-select`.

## [9] Dopuna testova

Popuniti rupe koje su nastale usput; e2e happy-path ako ostane vremena.

## [10] Jedan Advanced

Bira se na kraju iz E7–E13. `E8 · Staffing predlog` je najzanimljiviji Cypher u projektu.
