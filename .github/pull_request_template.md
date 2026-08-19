<!-- Filled by /slice step 7. Keep the sections; delete the italic prompts. -->

## Why

_The problem, in the reviewer's terms — not "implements E4.2", but what was missing or wrong._

## What changed

**Backend**
_By area, with the non-obvious decisions and the reasoning behind them._

**Frontend**
_Screens, the API seam wiring, anything that needed a new shared primitive._

**Tests**
_What was added and at which level._

## Reviewer notes

_Anything deliberate that looks wrong at a glance. Anything deferred, and why._

## Security

Which of the mandated cases from spec §5 apply to this slice, and how each is covered.
`file:line` evidence for every "covered" — a claim with no line number is not a finding.

| Case | Applies | How it is covered |
|---|---|---|
| Cypher injection | | |
| IDOR / ownership | | |
| Mass assignment | | |
| Soft delete | | |
| Passwords & secrets | | |
| XSS | | |

## Tests

_Counts and what they actually assert — "14 ITs" says nothing; "14 ITs covering 401/403/404, the
soft-delete filter and the injection payload" does._

## Roadmap

Step **[N]** — status updated in `docs/roadmap.md` in this PR.
