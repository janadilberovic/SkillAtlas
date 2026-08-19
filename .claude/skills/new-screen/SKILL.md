---
name: new-screen
description: >-
  Build or extend a screen in the SkillAtlas Angular 20 frontend — component,
  template, styles, route, nav entry, and the core/api seam behind it — following
  this project's established conventions. Use this whenever touching anything under
  frontend/src/app/features/, adding a route or an API token, wiring a button to an
  endpoint, or changing how a screen loads or filters data — even if the request just
  says "add a page", "show the imported people", or "put a filter on that list".
  The canonical reference is features/people/people-list.component.*.
---

# Building a SkillAtlas screen

Angular 20, standalone components only — there are no NgModules. Read [CLAUDE.md](../../../CLAUDE.md) § Frontend first; this skill is the long form of that section.

**Mirror an existing screen rather than inventing a shape.** Pick by shape, not by feature:

| Screen shape | Mirror |
|---|---|
| list + filters + pagination | `features/people/people-list.component.*` |
| detail / read-heavy | `features/profile/person-profile.component.*` |
| admin write (create, delete) | `features/skills/skills-catalog.component.*` |
| search with a result panel | `features/finder/expert-finder.component.*` |

## Files a screen touches

A screen is never one file. The last two lines are the ones that get forgotten — the screen works, and it is nowhere in the menu.

```
features/<area>/<name>.component.ts      standalone, sa- selector, inject(), signals
features/<area>/<name>.component.html    @if / @for / @empty only
features/<area>/<name>.component.css     tokens only  (NOTE: .css, not .scss)
core/api/api.ts                          abstract class = DI token   (new endpoint only)
core/api/http-api.ts                     Http* implementation        (new endpoint only)
app.config.ts                            one { provide: XApi, useClass: HttpXApi } line
app.routes.ts                            lazy loadComponent; adminGuard if admin-only
shared/components/app-shell/app-shell.component.html    nav link
```

## The API seam (the rule that matters)

Components inject the **abstract classes** from `core/api/api.ts` and **never inject `HttpClient`**. That seam is why a screen can land before its backend.

```ts
// core/api/api.ts — the token and its query shape live together
export abstract class ImportApi {
  abstract run(input: ImportRequest): Observable<ImportResult>;
}
export interface ImportRequest { dryRun?: boolean; }
```
```ts
// core/api/http-api.ts — @Injectable() with NO providedIn; bound explicitly below
@Injectable()
export class HttpImportApi extends ImportApi {
  private readonly http = inject(HttpClient);
  run(input: ImportRequest): Observable<ImportResult> {
    return this.http.post<ImportResult>(`${BASE}/people/import-vacayay`, input);
  }
}
```
```ts
// app.config.ts — one line, under the `// --- API seam (live) ---` banner
{ provide: ImportApi, useClass: HttpImportApi },
```

`BASE` is the module-level `const BASE = environment.apiBaseUrl` already at the top of `http-api.ts` (`/api/v1`, proxied to `:8080` in dev). Optional query params are appended only when truthy:

```ts
let params = new HttpParams().set('page', String(query.page ?? 0));
if (query.search) params = params.set('search', query.search);
```

Two traps:

- **Forgetting the `app.config.ts` line compiles and builds.** It fails at runtime with `NullInjectorError: No provider for ImportApi`, the first time someone opens the screen.
- **`mock-api.ts` is bound nowhere and must stay unbound.** Do not wire a screen to it to make it look finished. A screen either shows real data or says it is waiting.

**A screen whose endpoint does not exist yet renders `WaitingForApiComponent`** — route it there with a comment naming the spec section:

```ts
{ path: 'import',
  // Backend not built yet (E3).
  loadComponent: () => import('./shared/components/waiting-for-api/waiting-for-api.component')
    .then((m) => m.WaitingForApiComponent) },
```

## Component shape

```ts
@Component({
  selector: 'sa-people-list',          // sa- prefix, always
  standalone: true,
  imports: [FormsModule, RouterLink, SelectComponent, SkeletonComponent],
  templateUrl: './people-list.component.html',
  styleUrl: './people-list.component.css',   // singular styleUrl, .css sibling
})
export class PeopleListComponent {
  private readonly peopleApi = inject(PeopleApi);   // inject(), never constructor params
  private readonly size = 6;

  search = '';                          // plain field — it is an [(ngModel)] target
  readonly page = signal(0);            // everything else is a signal
  readonly data = signal<Page<Person> | null>(null);
  readonly loading = signal(false);

  readonly subtitle = computed(() => { ... });   // derived state is computed

  constructor() { this.load(); }        // loading starts in the constructor, not ngOnInit
}
```

The `[(ngModel)]` exception is the non-obvious part: `people-list` holds both `search = ''` and `readonly page = signal(0)` in the same class, and that is correct. Two-way form bindings stay plain; all other screen state is a signal.

**The repo uses a bare `.subscribe()`** — no `async` pipe, no `toSignal`, no `takeUntilDestroyed`, anywhere. A new screen that introduces them is not more modern, it is inconsistent.

## Template rules

Control flow is **100% `@if` / `@for` / `@empty`**. There is no `*ngIf`, no `*ngFor` and no `CommonModule` import in the entire codebase — do not be the first.

```html
@if (loading()) {
  <sa-skeleton [rows]="6" />
} @else if (data()) {
  @for (p of data()!.content; track p.id) { ... }
  @empty { <p class="dim pad">No people match those filters.</p> }
}
```

`@for` requires `track`. Reach for the shared primitives before writing anything new:

| Selector | Use for | Inputs |
|---|---|---|
| `sa-select` | **every dropdown** — the native popup cannot be themed | `options: SelectOption[]`, `placeholder`, `value` (model) |
| `sa-skeleton` | the loading state | `rows` |
| `sa-empty-state` | a rich empty panel | `eyebrow`, `title`, `message` + content slot |
| `sa-avatar` | person initials | `initials`, `size` |
| `sa-level-bar` | a 0–5 skill level | `level`, `width` |

`sa-select` is wired one-way-in / event-out when the target is a plain field:

```html
<sa-select placeholder="All teams" [options]="teamOptions()" [value]="team" (valueChange)="onTeamChange($event)" />
```

## The three states every screen has

| State | Pattern |
|---|---|
| loading | `<sa-skeleton [rows]="N" />` — never a spinner |
| error | `readonly error = signal<string \| null>(null)` + `subscribe({ next, error })` + `@if (error()) { <p class="error small">{{ error() }}</p> }` |
| empty | `@empty { ... }`, or `<sa-empty-state>` for a full panel |

Mirror `expert-finder.component.ts` for the error arm — it degrades to a readable sentence rather than dumping the HTTP body:

```ts
error: (err) => this.error.set(err?.error?.error ?? 'Search failed. Check the API and try again.')
```

## Styling — Nocturne

All colour, radius and font values are CSS variables on `:root` in [`styles.scss`](../../../frontend/src/styles.scss). **Never hardcode a hex in a component**, and never add a palette array in a `.ts` file — a palette belongs in `styles.scss` next to `--node-person` / `--node-skill` / `--node-project` / `--node-team`.

Surfaces `--bg-page --bg-screen --surface --surface-2 --surface-accent --surface-mute` · borders `--border --border-strong` · text `--text --text-2 --text-muted --text-dim` · accent `--accent --accent-text --accent-2 --accent-deep` · geometry `--radius --radius-lg --shell-max --font`.

Reuse the shared classes before writing CSS: `.btn` / `.btn-ghost` / `.btn-sm` / `.btn-block`, `.field`, `.label`, `.card` / `.card-flat`, `.tag` / `.tag-outline` / `.tag-mute`, `.eyebrow`, `.col-head`, `.muted` / `.dim`, `.divider`.

Component styles have a hard budget of 8 kb (warning) / 16 kb (error) from `angular.json`.

## Routing and guards

Every route is lazy — there is not one eager `component:` entry in `app.routes.ts`:

```ts
{ path: 'people', canActivate: [adminGuard],
  loadComponent: () => import('./features/people/people-list.component').then((m) => m.PeopleListComponent) },
```

`authGuard` sits **once**, on the shell route — do not repeat it on children. `adminGuard` goes on each admin-only child.

**Guards are UX, not security.** The server refuses regardless, and a slice is not done because the menu item is hidden.

An admin-only screen also needs its nav link gated:

```html
@if (auth.isAdmin()) { <a routerLink="/people" routerLinkActive="active">People</a> }
```

## Pagination

Server-side, via `Page<T>` (`content`, `page`, `size`, `totalElements`, `totalPages`). The server caps `size` at 100 — asking for more silently gets 100 back.

Mirror `people-list`'s `rangeLabel()` and `pageNumbers()` rather than inventing a pager.

## Verifying

**There is no test framework in `frontend/`** — no `*.spec.ts`, no Karma, no Jest, no ESLint. So the gate is:

```bash
npm run build
```

`ng build` defaults to the production configuration, which is what makes this meaningful: full AOT `strictTemplates` typecheck plus the bundle budgets. A template typo that `ng serve` tolerates fails here. CI runs exactly this.

Then look at it. `npm start` serves on `:4200` and proxies `/api` to `:8080` via `proxy.conf.json`, so the backend must be running (`preview_start {name:"backend"}`). Log in as the seeded admin `admin@skillatlas.dev` / `Password123!`.

## Definition of done

- `npm run build` green.
- Screen renders against a live backend, not mocks.
- Loading, error and empty states all reachable and all styled.
- No hex, no `HttpClient`, no `<select>`, no `*ngIf` in the new code.
- New API token bound in `app.config.ts`; route lazy; admin routes guarded **and** their nav link gated.
- Every colour a `var(--...)`.
