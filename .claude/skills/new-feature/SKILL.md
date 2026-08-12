---
name: new-feature
description: >-
  Build a new backend feature slice in the SkillAtlas codebase (Java 21 + Spring
  Boot 3 + Spring Data Neo4j) — repository, service, controller, DTOs, exceptions,
  security — following this project's established conventions. Use this whenever
  adding or extending a feature under com.skillatlas.<feature> (skills, projects,
  teams, finder, graph, mentoring), building CRUD/REST endpoints, writing a Neo4j
  repository or Cypher query, or wiring auth guards — even if the request just
  says "add an endpoint" or "let admins manage projects" without naming a slice.
  The canonical reference is the fully-built `people` feature; mirror it.
---

# Building a SkillAtlas feature slice

SkillAtlas maps company knowledge as a graph (people ↔ skills ↔ projects ↔ teams) on Neo4j. Read [CLAUDE.md](../../../CLAUDE.md) and [docs/spec.md](../../../docs/spec.md) before starting — the spec is the source of truth for fields, rules, and the API catalog.

The **`people` feature is the reference implementation**. When in doubt, open those files and mirror their structure:
`src/main/java/com/skillatlas/people/` — `domain/`, `enums/`, `dto/`, `exception/`, `PeopleRepository`, `PeopleService`, `PeopleController`.

## Package layout (feature-based)

Everything for a feature lives under `com.skillatlas.<feature>`. The slice layers, and where each file goes:

```
com.skillatlas.<feature>
├── <Feature>Controller.java   REST, /api/v1/..., @Valid, security guards. NO Cypher.
├── <Feature>Service.java      business rules, @Transactional. NO Cypher.
├── <Feature>Repository.java   Neo4jRepository — the ONLY place Cypher is allowed.
├── domain/                    @Node entities + @RelationshipProperties classes
├── enums/                     enums   (NOTE: "enum" is a reserved word — the folder is "enums")
├── dto/                       request records (validation) + response records
└── exception/                 domain exceptions (RuntimeException)
```

Build the slice bottom-up: **domain → repository → service → controller**. Verify it boots after each layer (see Verifying).

## Layer rules

### Domain (`domain/`)
- Nodes: `@Node("X")`, `@Getter @Setter @NoArgsConstructor`, id = `@Id @GeneratedValue(generatorClass = UUIDStringGenerator.class) private String id;`.
- `level`-style data that varies per pair belongs on the **relationship**, never on a node.
- Relationships **with** properties → a `@RelationshipProperties` class. Its id MUST be the internal id:
  ```java
  @RelationshipId
  private Long id;   // NOT a UUID generator — Neo4j rejects a custom generator on a relationship id at startup
  ```
  This is a real startup-crash trap. Node ids use the UUID generator; relationship ids use the plain `Long` internal id.
- Relationships **without** properties → reference the target node directly (`@Relationship(type="USES") private Set<Skill> uses`). Only convert to a properties class if it later gains a property.
- Model relationships **one-directional** from their natural owner to avoid package cycles and runaway subgraph loading. Traverse the other way with a Cypher query when a feature needs it.
- Don't add nodes/relationships for features you aren't building.

### Repository (`<Feature>Repository.java`)
- `extends Neo4jRepository<Entity, String>` — gives `save`/`findById`/`count` for free.
- **Every read filters soft-delete** where the entity is soft-deletable (people): name derived queries `...AndDeletedFalse` / `...DeletedFalse`. This is the most-forgotten rule.
- Prefer derived query methods (method name → Cypher). When they aren't enough (search, ranking, multi-hop), write `@Query("MATCH ... WHERE ... = $param ...")` — **always parameterized (`$param`), never string concatenation** (Cypher injection). Cypher lives here and nowhere else.

### Service (`<Feature>Service.java`)
- `@Service`, constructor injection, `@Transactional` on writes / `@Transactional(readOnly = true)` on reads.
- Holds the business rules; delegates all persistence to the repository (`repository.save(...)` is the actual write).
- Soft delete = set `deleted = true` + `deletedAt = Instant.now()`, then save. **Never hard-delete people.**
- Enforce uniqueness before insert (`existsBy...` → throw a domain exception).
- Hash passwords with the injected `PasswordEncoder` — never store or log plain.
- Updates are **mass-assignment-safe**: copy only the fields the caller is allowed to change. Never let `role`/`verified`/`active` be set through a profile update just because the field exists.

### DTOs (`dto/`)
- Request records carry Bean Validation (`@NotBlank`, `@Email`, `@Size`, `@NotNull`). Keep separate requests for different trust levels (e.g. admin-create vs profile-update — the profile one simply omits `role`/`email`).
- Response records expose **only safe fields** via a `from(Entity)` factory. **Never** serialize the entity directly — that would leak `passwordHash`, soft-delete fields, and the relationship graph. See `PersonResponse`.
- Paginated lists return `com.skillatlas.common.PageResponse<T>` (`PageResponse.from(page.map(Dto::from))`).

### Controller (`<Feature>Controller.java`)
- `@RestController @RequestMapping("/api/v1/<feature>")`. Map entities → response DTOs; `@Valid @RequestBody` on writes.
- Every list is **paginated** with a capped size (see `PeopleController` — `MAX_PAGE_SIZE`).
- Authorization:
  - Coarse (admin-only writes): `@PreAuthorize("hasRole('ADMIN')")`.
  - Ownership / IDOR ("only your own X"): compare `SecurityUtil.currentUserId()` (from the token) to the resource owner **on the server** — role is not enough, and identity must come from the token, never a path/query param. Throw `AccessDeniedException` (→ 403) on mismatch.
- Let exceptions bubble to `com.skillatlas.common.GlobalExceptionHandler` (404/409/401/403/400). Don't write ad-hoc error bodies.

## Security checklist (from CLAUDE.md §5 — not optional)
- Cypher injection: `$param` always, never string concat. The search input `React'}) DETACH DELETE (n) //` must delete nothing.
- IDOR: server-side ownership check from the token.
- Mass assignment: bind to DTOs; privileged fields not settable via profile endpoints.
- Passwords: BCrypt/argon2, never plain, never in logs.
- Secrets (Neo4j creds, JWT secret) via env, never in the repo.

## Tests (every slice ships both kinds)

A slice is not done when it compiles and curls correctly. Two test classes, mirroring the finder slice:

**`<Feature>ServiceTest`** — pure Mockito, **no database**, so it stays in `mvnw test` (~seconds). Mirror `src/test/java/com/skillatlas/finder/FinderServiceTest.java`: `@ExtendWith(MockitoExtension.class)`, mock the repository, assert the business rules — input normalization, the validation boundaries from spec §4.1 (level 1–5, "can't want to learn what you know at 5"), and that invalid input is rejected *before* the repository is touched.

**`<Feature>IT extends AbstractNeo4jIT`** — real Cypher against a real Neo4j, run by failsafe under `mvnw verify`. Mirror `src/test/java/com/skillatlas/finder/FinderIT.java`. Cover, at minimum:

- **the security cases the spec mandates** — no token → 401, wrong role → 403, someone else's resource → 403 (IDOR), missing/soft-deleted → 404;
- **soft delete** — a deleted person never appears in a list, search, or projection;
- **pagination** — the cap holds and the count query agrees with the page query;
- **Cypher injection** — `React'}) DETACH DELETE (n) //` through any free-text parameter returns an empty result *and leaves the node count unchanged*. Assert the count, not just the empty list — the empty list alone would also be true if the database had been wiped.

Fixture discipline, because the suite runs against whatever local Neo4j is configured — possibly a dev database with real data:
- build fixtures in `@BeforeEach` with a **per-run UUID suffix** on every name, so parallel data can't collide;
- delete exactly what you created in `@AfterEach` (`MATCH (n) WHERE n.id IN $ids DETACH DELETE n`);
- assert **relatively** (`countPeople()` equals the count taken before the test), never absolutely — an absolute count assumes an empty database and will fail on anyone else's machine.

Naming matters mechanically: `*Test` → surefire (`mvnw test`, no database), `*IT` → failsafe (`mvnw verify`, needs Neo4j). A database-dependent test named `*Test` breaks the fast stage for everyone, in CI and locally.

## Verifying (the loop that catches real bugs)

Compilation is not enough — Neo4j entity mapping and repository query derivation are validated at **startup**, and auth only shows under a live request. After each layer, boot against the running Neo4j and, for endpoints, curl them. Windows/PowerShell pattern used in this repo:

```powershell
$env:NEO4J_PASSWORD = "rootpass"   # matches local Neo4j Desktop instance; app reads it via ${NEO4J_PASSWORD}
.\mvnw.cmd -q -o compile
# boot in a background job, wait for "Started SkillatlasApplication", then curl.exe the endpoints, then Stop-Job
```

Log in as the seeded admin (`admin@skillatlas.dev` / `Password123!`) to get a token, then exercise the endpoints. Confirm the security-relevant cases explicitly: no token → 401, wrong role → 403, soft-deleted resource → 404/filtered out of lists.

## Definition of done
- App boots clean against Neo4j.
- Reads filter soft-delete; lists paginate.
- Writes validated server-side; admin-guarded; ownership-checked where the spec requires it.
- No Cypher outside the repository; all queries parameterized.
- Responses never leak `passwordHash` or internal fields.
- Endpoints verified with real requests, including 401/403/404 paths.
- **`.\mvnw.cmd -B verify` green** — unit tests and integration tests, including the injection and soft-delete cases above.
