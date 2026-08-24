# SkillAtlas

Graph platform for mapping a company's knowledge — who knows what, who can mentor, and where the knowledge gaps are.
**Stack:** Java 21 · Spring Boot 3 · Spring Data Neo4j.

## Getting started (~5 min)

### 1. Neo4j database
Two options:

- **Neo4j Desktop** (already installed): create a database and start it (Bolt on `7687`).
- **Docker**: `docker compose up -d` → Browser at http://localhost:7474

### 2. Blob storage for profile pictures
Profile pictures live in Azure Blob Storage, not in Neo4j. Locally that is **Azurite**, Microsoft's
emulator, which the same compose file starts:
```bash
docker compose up -d azurite     # blob endpoint on :10000
```
Without it the app still runs; only avatar upload fails. Against a real storage account, set
`AZURE_STORAGE_CONNECTION_STRING` and nothing else changes.

### 3. Environment
```bash
cp .env.example .env
```
Fill in `NEO4J_PASSWORD` (and the rest if needed). `.env` is in `.gitignore`.

### 4. Run the app
Windows (PowerShell):
```bash
.\mvnw.cmd spring-boot:run
```
Verify the database is connected: http://localhost:8080/actuator/health → `neo4j` status `UP`.

### 5. Demo data
On first start `DevSeeder` fills an empty database with six teams, ~30 skills and ~40 people whose
KNOWS levels are spread over 1–5 (plus two soft-deleted people, so you can see them *not* show up).
Everyone signs in with `Password123!`; the admin is `admin@skillatlas.dev`.

It only creates what is missing, so restarting never duplicates or overwrites your own edits. Turn
it off with `skillatlas.seed.enabled=false`.

## Build / test

Unit tests only — no database needed, ~1 min:
```bash
.\mvnw.cmd test
```

Everything, including the integration tests — **needs Neo4j and Azurite running**
(`docker compose up -d`, or Neo4j Desktop plus `docker compose up -d azurite`):
```bash
.\mvnw.cmd clean verify
```
Integration tests are the `*IT` classes; they read `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD`
from the environment and force `skillatlas.seed.enabled=false`, so they never touch demo data.

CI runs both on every pull request — see [.github/workflows/ci.yml](.github/workflows/ci.yml).

## Structure (feature-based)
Packages under `com.skillatlas.<feature>`; layers `Controller → Service → Repository (Cypher only here) + dto`.
Detailed conventions: [CLAUDE.md](CLAUDE.md). Domain model and feature spec: intern documentation.
