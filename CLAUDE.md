# SkillAtlas — conventions

Graph platform for mapping a company's knowledge (people ↔ skills ↔ projects ↔ teams).
Stack: **Java 21 + Spring Boot 3 + Spring Data Neo4j**, with an **Angular 20** client in [`frontend/`](frontend/).
The database is a graph (Neo4j), not relational.

> **Before starting a task, check the project spec** — [`docs/spec.md`](docs/spec.md), extracted from `SkillAtlas_Dokumentacija_za_praktikante.pdf` (root, authoritative for figures and tables). It is the source of truth for domain fields, business rules, feature scope, and the API catalog. When a request is ambiguous or looks incomplete, read the relevant spec section before writing code.
>
> Build order and what is already done: [`docs/roadmap.md`](docs/roadmap.md).

## Architecture
Feature-based packages under `com.skillatlas.<feature>` (people, skills, projects, teams, finder, graph, mentoring).
Layers within a feature:

- `*Controller` — REST, `/api/v1/...`, input validation, mapping to DTOs. **No Cypher.**
- `*Service` — business rules.
- `*Repository` — **the only place Cypher is allowed.** Spring Data Neo4j interface (+ `@Query` when needed).
- `dto/` — input/output shapes + Bean Validation (`@Valid`, `@NotNull`, `@Min`...).

## Comments (write far fewer than feels natural)
Default to **no comment**. Write one only when it explains a *why* the code cannot: a non-obvious
decision, a gotcha, a business rule from the spec, a trap that would otherwise be re-introduced.
Never restate what the line already says, never add section-divider banners, and never write a
Javadoc that re-spells the method name. If a comment explains *what* the code does, rename instead.
One tight sentence beats a paragraph; match the sparse density of the file you are editing.

## Graph modeling (careful!)
- `level` (1–5) is a **property on the `KNOWS` relationship**, not on the `Person` or `Skill` node.
- Nodes: `Person`, `Skill`, `Project`, `Team`. Relationships: `KNOWS`, `WANTS_TO_LEARN`, `WORKED_ON`, `USES`, `MEMBER_OF`, `MENTORS`.
- Do not add nodes/relationships for features you are not building yet.

## Business rules (key)
- **Soft delete**: people are never hard-deleted (`isDeleted` + `deletedAt`). Every read query must include a soft-delete filter.
- Level is an integer 1–5; out of range → validation error (server-side too).
- Import from VacaYAY is **idempotent** — dedup by email (`MERGE`, not `CREATE`).

## Security (not optional)
- **Cypher injection**: always use parameters (`$param`), never string concat. Test: the input `React'}) DETACH DELETE (n) //` through search must not delete anything.
- **IDOR**: ownership check on the server (`id == currentUserId` from the token); a role is not enough.
- **Mass assignment**: bind to a DTO; `role`/`verified` are not set through the profile endpoint.
- Passwords: bcrypt/argon2, never plain, never in logs.
- Neo4j credentials via env (`.env`), never in the repo nor in the agent's context as a plain value.

## Non-functional
- Server-side validation on every write.
- Pagination on every list; graph endpoint with `LIMIT`.
- No N+1 (calling the database inside a loop). Unique constraints on `Person.email` and `Skill.name`.

## Frontend (`frontend/`)
Angular 20, standalone components only — there are no NgModules. Mirror an existing screen rather
than inventing a shape; `features/people/people-list.component.ts` is the reference.

**Components** — `sa-` selector prefix, `standalone: true`, `templateUrl` + `styleUrl` as sibling
files (inline `template:` only for the small `shared/components/*`). Dependencies via `inject()`,
not constructor parameters. Screen state is **signals** (`signal`, `computed`), not plain fields —
except two-way `[(ngModel)]` form bindings, which stay plain.

**The API seam is the rule that matters.** Components inject the abstract classes from
[`core/api/api.ts`](frontend/src/app/core/api/api.ts) (`PeopleApi`, `FinderApi`, …) and **never
inject `HttpClient` directly**. Real calls live in `core/api/http-api.ts`; the binding is one line
per token in `app.config.ts`. That seam is why the backend can land after the screen.

**A screen whose endpoint does not exist yet renders `WaitingForApiComponent`** — route it there in
`app.routes.ts` with a `// Backend not built yet (E5.1).` comment naming the spec section. Do not
wire it to `mock-api.ts` to make it look finished — that file is no longer bound to any token and
should stay unbound; a screen either shows real data or says it is waiting.

**Routing** — every route is lazy (`loadComponent: () => import(...)`). `authGuard` sits on the
shell route, `adminGuard` on the admin-only children. Guards are **UX, not security** — the server
enforces access regardless, and a slice is not done because the menu item is hidden.

**Styling — Nocturne.** All colour, radius and font values are CSS variables on `:root` in
[`styles.scss`](frontend/src/styles.scss). Never hardcode a hex in a component. Reuse the shared
primitives before writing new CSS: `.btn` / `.btn-ghost` / `.btn-sm`, `.field`, `.label`, `.card` /
`.card-flat`, `.tag` / `.tag-outline` / `.tag-mute`, `.eyebrow`, `.muted` / `.dim`, `.divider`.
Use `sa-select` (`shared/components/select/`) instead of a native `<select>` — the native popup
cannot be themed.

**Verifying** — `npm run build` in `frontend/` is the check that counts; it defaults to the
production configuration, so it runs the full AOT template typecheck and enforces the bundle
budgets from `angular.json`. Node 22 (`.nvmrc`). CI runs it on every PR.

## Git workflow (branching)
- **Always branch each feature directly off `main`.** One feature = one branch = one PR targeting `main`.
- **Never stack branches** — do not open a branch off another feature branch, and never point a PR's base at anything other than `main` (no "PR into PR", no nested/stacked PRs).
- Keep branches short-lived: merge to `main`, then branch the next feature fresh from the updated `main`.
- Before starting a feature, `git checkout main && git pull` so the new branch starts from the latest `main`.
