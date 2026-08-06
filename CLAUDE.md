# SkillAtlas — konvencije

Graf platforma za mapiranje znanja firme (ljudi ↔ skillovi ↔ projekti ↔ timovi).
Stack: **Java 21 + Spring Boot 4 + Spring Data Neo4j**. Baza je graf (Neo4j), ne relaciona.

## Arhitektura
Feature-based paketi pod `com.skillatlas.<feature>` (people, skills, projects, teams, finder, graph, mentoring).
Unutar feature-a slojevi:

- `*Controller` — REST, `/api/v1/...`, validacija ulaza, mapiranje na DTO. **Bez Cypher-a.**
- `*Service` — poslovna pravila.
- `*Repository` — **jedino mesto gde sme Cypher.** Spring Data Neo4j interfejs (+ `@Query` po potrebi).
- `dto/` — ulazni/izlazni oblici + Bean Validation (`@Valid`, `@NotNull`, `@Min`...).

## Graf modelovanje (pazi!)
- `level` (1–5) je **property na `KNOWS` relaciji**, ne na `Person` ni `Skill` čvoru.
- Čvorovi: `Person`, `Skill`, `Project`, `Team`. Relacije: `KNOWS`, `WANTS_TO_LEARN`, `WORKED_ON`, `USES`, `MEMBER_OF`, `MENTORS`.
- Ne dodaji čvorove/relacije za feature-e koje još ne gradiš.

## Poslovna pravila (ključna)
- **Soft delete**: osobe se nikad ne hard-delete-uju (`isDeleted` + `deletedAt`). Svaki read upit mora imati soft-delete filter.
- Level je ceo broj 1–5; van opsega → validaciona greška (i server-side).
- Import iz VacaYAY-a je **idempotentan** — dedup po emailu (`MERGE`, ne `CREATE`).

## Security (nije opciono)
- **Cypher injection**: uvek parametri (`$param`), nikad string concat. Test: input `React'}) DETACH DELETE (n) //` kroz pretragu ne sme ništa da obriše.
- **IDOR**: ownership provera na serveru (`id == currentUserId` iz tokena), rola nije dovoljna.
- **Mass assignment**: bind na DTO; `role`/`verified` se ne postavljaju kroz profil endpoint.
- Lozinke: bcrypt/argon2, nikad plain, nikad u logovima.
- Neo4j kredencijali kroz env (`.env`), nikad u repo ni u agentov kontekst kao plain vrednost.

## Non-functional
- Validacija server-side na svakom write-u.
- Paginacija na svakoj listi; graf endpoint sa `LIMIT`.
- Bez N+1 (poziv bazi u petlji). Unique constraints na `Person.email` i `Skill.name`.
