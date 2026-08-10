# SkillAtlas Nocturne — Angular frontend

Dark-themed ("Nocturne") UI for SkillAtlas, built from the Claude Design doc. This pass covers
the **core flow** with **mocked data**; when the Java API grows the missing endpoints, the mock
services are swapped for real HTTP ones behind the same interfaces — no component changes.

## Run

```bash
cd frontend
npm install
npm start          # ng serve --proxy-config proxy.conf.json → http://localhost:4200
```

Demo logins (mocked): `admin@skillatlas.dev` (admin) or `sara.ilic@firma.rs` (member) — password
`Password123!` for both. The admin sees People, Dashboard and the VacaYAY import; the member is
role-gated (try `/people` as the member → 403 screen).

`npm run build` produces a production bundle in `dist/`.

## Screens (this pass)

| Route | Screen | Notes |
| --- | --- | --- |
| `/login` | Sign in (2e) | generic error, no public sign-up |
| `/finder` | Expert finder (2a) | AND-across-skills ranking, partial + empty states |
| `/people` | People admin (2f) | search / team / skill filters, pagination — **admin** |
| `/people/:id`, `/me` | Person profile / My skills (2c) | own profile is editable (Add skill, level 1–5 validation) |
| `/graph` | Graph explorer (2b) | interactive SVG, hover-highlight, selection panel |
| `/dashboard` | Skill-gap dashboard (2d) | stats, gap-by-team, bus factor, mapping queue — **admin** |
| `/skills` | Skills catalog (2h) | inline create, delete-impact card, most-wanted — **admin** |
| `/projects` | Projects list | cards → detail |
| `/projects/:id` | Project detail (2i) | USES + staffing (WORKED_ON), coverage via finder, archive/assign — admin actions |
| (modal) | Mentor matching (2g) | opened from "Suggest a mentor" (profile) and the dashboard bus-factor; ranked candidates → confirm |
| `/forbidden` | 403 | role gate |
| `/coming-soon` | stub | Account / change-password (2k), States gallery (2j) — remaining second pass |

## The mock → real API seam

Components depend only on the **abstract API classes** in [`src/app/core/api/api.ts`](src/app/core/api/api.ts)
(`AuthApi`, `PeopleApi`, `SkillApi`, `ProjectApi`, `TeamApi`, `FinderApi`, `GraphApi`, `MentoringApi`, `DashboardApi`).

- Today [`app.config.ts`](src/app/app.config.ts) binds each token to a `Mock*` class in
  [`core/api/mock-api.ts`](src/app/core/api/mock-api.ts), which serves data from
  [`core/mock/mock-data.ts`](src/app/core/mock/mock-data.ts) with a simulated delay.
- **To go live:** write `Http*` implementations of the same abstract classes (using `HttpClient`
  + `environment.apiBaseUrl`), swap the `useClass` bindings in `app.config.ts`, set
  `environment.useMocks = false`, and delete `mock-data.ts` / `mock-api.ts`.

TypeScript models in [`core/models/models.ts`](src/app/core/models/models.ts) mirror the existing
Java `*Response` records exactly. Fields marked `// PLANNED` (person `team`/`topSkills`/`knows`/
`wantsToLearn`/`projects`/`mentorships`, skill `knownBy`/`wantedBy`, project `members`, plus
`FinderResult`, `GraphData`, `Team`) document the shape the backend must grow to for the finder,
graph, profile, teams, and dashboard features.

## Backend note

`com.skillatlas.config.CorsConfig` (+ `http.cors(...)` in `SecurityConfig`) allows the dev origin
`http://localhost:4200`. Not exercised while mocked, but required once the real API is wired.
Override allowed origins via `security.cors.allowed-origins`.
