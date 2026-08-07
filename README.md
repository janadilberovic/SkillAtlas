# SkillAtlas

Graph platform for mapping a company's knowledge — who knows what, who can mentor, and where the knowledge gaps are.
**Stack:** Java 21 · Spring Boot 3 · Spring Data Neo4j.

## Getting started (~5 min)

### 1. Neo4j database
Two options:

- **Neo4j Desktop** (already installed): create a database and start it (Bolt on `7687`).
- **Docker**: `docker compose up -d` → Browser at http://localhost:7474

### 2. Environment
```bash
cp .env.example .env
```
Fill in `NEO4J_PASSWORD` (and the rest if needed). `.env` is in `.gitignore`.

### 3. Run the app
Windows (PowerShell):
```bash
.\mvnw.cmd spring-boot:run
```
Verify the database is connected: http://localhost:8080/actuator/health → `neo4j` status `UP`.

## Build / test
```bash
.\mvnw.cmd clean verify
```

## Structure (feature-based)
Packages under `com.skillatlas.<feature>`; layers `Controller → Service → Repository (Cypher only here) + dto`.
Detailed conventions: [CLAUDE.md](CLAUDE.md). Domain model and feature spec: intern documentation.
