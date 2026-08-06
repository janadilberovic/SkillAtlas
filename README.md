# SkillAtlas

Graf platforma za mapiranje znanja firme — ko šta zna, ko može biti mentor, gde su rupe u znanju.
**Stack:** Java 21 · Spring Boot 4 · Spring Data Neo4j.

## Pokretanje (~5 min)

### 1. Neo4j baza
Dve opcije:

- **Neo4j Desktop** (već instaliran): napravi bazu, startuj je (bolt na `7687`).
- **Docker**: `docker compose up -d` → Browser na http://localhost:7474

### 2. Env
```bash
cp .env.example .env
```
Popuni `NEO4J_PASSWORD` (i ostalo ako treba). `.env` je u `.gitignore`.

### 3. Pokreni app
Windows (PowerShell):
```bash
.\mvnw.cmd spring-boot:run
```
Provera da baza radi: http://localhost:8080/actuator/health → `neo4j` status `UP`.

## Build / test
```bash
.\mvnw.cmd clean verify
```

## Struktura (feature-based)
Paketi pod `com.skillatlas.<feature>`; slojevi `Controller → Service → Repository (Cypher samo ovde) + dto`.
Detaljne konvencije: [CLAUDE.md](CLAUDE.md). Domenski model i feature spec: dokumentacija za praktikante.
