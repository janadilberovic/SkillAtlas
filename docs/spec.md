# SkillAtlas — Dokumentacija za praktikante

> **Generisano iz `SkillAtlas_Dokumentacija_za_praktikante.pdf`.** PDF ostaje autoritativan za
> slike, dijagrame i tačan izgled tabela. Ovaj fajl postoji da bi spec bio pretraživ (`Grep`),
> diff-abilan u PR-ovima i čitljiv alatima koji ne rasterizuju PDF.
>
> Regeneracija:
> ```bash
> pdftotext -layout -enc UTF-8 SkillAtlas_Dokumentacija_za_praktikante.pdf docs/spec.txt
> ```
> pa ručno prelomiti u markdown. Ako se PDF promijeni, regeneriši i ažuriraj ovaj fajl u istom PR-u.

Full-Stack · AI-Accelerated Internship 2026 — Interni „ko-zna-šta" graf · Skill & Knowledge Mapping Platform.
Referentni stack u dokumentaciji: Next.js/TypeScript + Neo4j. **Ovaj repo koristi Java 21 + Spring Boot 3 + Spring Data Neo4j** — dokumentacija je pisana stack-agnostično gdje god može.

## Šta je SkillAtlas?

Web aplikacija koja odgovara na pitanja koja svaka firma stalno postavlja: ko kod nas zna Neo4j? Ko može biti mentor za React? Koji tim ima rupu u DevOps znanju?

Ljudi, skillovi, projekti i timovi su prirodno graf — zato je baza Neo4j, a ne relaciona. Upiti tipa „nađi put od osobe do skilla preko projekata i kolega" su u grafu jedan izraz; u SQL-u bi bili rekurzivna muka.

Dvije role:
- **Admin** — pun pristup: upravlja ljudima, skillovima i projektima, potvrđuje mentorstva, vidi dashboard, pokreće import.
- **Member** — vodi svoje skillove i želje, pretražuje eksperte, istražuje graf, gleda svoj learning path.

Poseban začin: sopstveni VacaYAY API je „stari sistem" iz kog SkillAtlas uvozi zaposlene. Kod koji je već napisan postaje eksterna integracija.

---

# 01 · Proizvod i način rada

## 1. Vizija proizvoda

SkillAtlas postoji da znanje u firmi prestane da bude usmeno predanje. Zaposleni treba za pola minuta da nađe ko mu može pomoći oko tehnologije koju ne zna — i da vidi put kako sam da je nauči. Team lead treba da vidi gdje su rupe: koje tehnologije tim koristi na projektima, a niko ih (ili samo jedna osoba) ne zna.

Konkretno: admin vodi ljude, skillove i projekte; svaki zaposleni sam prijavljuje šta zna (level 1–5) i šta želi da nauči; pretraga rangira eksperte; graf vizualizacija pokazuje cijelu sliku; mentor matching spaja ljude; dashboard upozorava na gap i bus factor.

## 2. Role i dozvole

| Mogućnost | Member | Admin |
|---|:---:|:---:|
| Login, svoj profil, svoja lozinka | ✓ | ✓ |
| Svoji skillovi (dodaj/izmijeni/ukloni) + „želim da naučim" | ✓ | ✓ |
| Pregled grafa, expert finder, tuđi profili (read-only) | ✓ | ✓ |
| Learning path za sebe | ✓ | ✓ |
| CRUD ljudi, skillova, projekata | — | ✓ |
| Dodjela ljudi na projekte | — | ✓ |
| Mentor matching + potvrda mentorstva | — | ✓ |
| Skill-gap dashboard | — | ✓ |
| Import iz VacaYAY-a | — | ✓ |

---

# 02 · Domenski model

Model je namjerno mali — dubina dolazi iz načina na koji se gradi (graf upiti, algoritmi, integracije), ne iz ogromne šeme.

## 1. Čvorovi (core)

- **Person** — centralni zapis.
  - Identitet: email (unique), password hash, rola (Admin/Member), is-active.
  - Profil: ime, prezime, pozicija, opciono slika.
  - Soft delete: `isDeleted` + `deletedAt` (ljude nikad hard-delete).
- **Skill** — naziv (React, Neo4j, Docker…), kategorija (jezik / framework / alat / baza), opciono boja za graf.
- **Project** — naziv, opis, period (od–do), aktivan/arhiviran.
- **Team** — naziv (Backend, Frontend, DevOps…).

## 2. Relacije (core)

| Relacija | Od → Do | Properties |
|---|---|---|
| `KNOWS` | Person → Skill | `level` (1–5), `since` (opciono) |
| `WANTS_TO_LEARN` | Person → Skill | `createdAt` |
| `WORKED_ON` | Person → Project | `role`, `from`, `to` (opciono) |
| `USES` | Project → Skill | — |
| `MEMBER_OF` | Person → Team | — |
| `MENTORS` | Person → Person | `skill` (ili relacija ka Skill čvoru), `since` |

> **Napomena za modelovanje:** `level` pripada relaciji (`KNOWS {level: 4}`), ne čvoru — ista osoba zna različite skillove na različitim nivoima. Ako agent u planu predloži level na `Person` ili `Skill` čvoru — to je greška koju treba uhvatiti.

## 3. Opcioni elementi (samo uz odgovarajući feature)

- **Endorsement (E7):** relacija `ENDORSES` Person → Person sa `skill` i `createdAt`; `verified` flag na `KNOWS` poslije N potvrda.
- **Notification (E7):** čvor ili spoljni zapis — tip, naslov, pročitano/ne.

Držati šemu poštenom: ne dodavati čvorove za feature-e na koje se nije obavezalo. Prazan `Department` čvor je šum — i zbunjuje agenta.

## 4. Poslovna pravila

### 4.1 Skill level
- Level je cio broj **1–5**. Van opsega → validaciona greška.
- Ne može se dodati `WANTS_TO_LEARN` za skill koji se već zna na levelu 5.
- Ne može se imati i `KNOWS` i `WANTS_TO_LEARN` istovremeno na levelu ≥ željenog — kad se nauči, želja se briše (ili konvertuje).

### 4.2 Expert finder rangiranje
- Rezultat: osobe koje znaju **sve** tražene skillove (AND), rangirane po zbiru/minimumu levela (odlučiti i dokumentovati).
- Soft-deleted osobe se nikad ne pojavljuju.
- Opciono: sekcija „djelimično poklapanje" za one koji znaju podskup.

### 4.3 Mentor matching
- Kandidat: `KNOWS` željeni skill sa levelom **≥ 3**.
- Osoba ne može biti mentor samoj sebi.
- Rangiranje: viši level bolje; manje aktivnih mentorstava bolje (opterećenost).
- Kriterijumi rangiranja su prikazani u UI-ju — korisnik vidi zašto je neko prvi.
- `MENTORS` relacija nastaje tek kad admin potvrdi.

### 4.4 Learning path
- `shortestPath` od `Person` do željenog `Skill` čvora kroz dozvoljene relacije (`KNOWS`, `WORKED_ON`, `USES`, `MEMBER_OF`).
- Prikazati i „najbližeg mentora na putu" ako postoji.

### 4.5 Import iz VacaYAY-a
- Poziva VacaYAY API, mapira zaposlene na `Person` čvorove.
- **Idempotentan** — dedup po emailu (`MERGE`, ne `CREATE`); ponovni klik ne pravi duplikate čvorova ni relacija.
- Uvezeni dolaze bez skillova → ulaze u listu „čeka mapiranje skillova".

### 4.6 Soft delete
- Obrisana osoba nestaje iz svih listi, pretrage, grafa i preporuka.
- Istorija `WORKED_ON` ostaje (projekat i dalje zna ko je radio).
- **Svaki read upit mora imati soft-delete filter** — ovo je najčešće mjesto gdje agent zaboravi.

---

# 03 · Specifikacija feature-a

Prioriteti: **Core** (radi se), **Advanced** (bira se jedan na kraju; ostali samo ako se leti). Acceptance criteria su u laganom Given/When/Then stilu.

## E1 · Identity & Access (core)

**E1.1 — Login i role.** Kao korisnik, želim da se prijavim da bih pristupio funkcijama svoje role.
- Validni kredencijali → sesija/token koji nosi rolu.
- Nevalidni → generička greška (bez odavanja da li korisnik postoji).
- Neautentifikovan zahtjev na zaštićen endpoint → **401**; pogrešna rola → **403**.

**E1.2 — Nalozi kroz admin panel.** Kao admin, kreiram naloge da bi pristup imali samo pravi zaposleni.
- Nema javne registracije; kreiranje postavlja početnu lozinku i rolu.

**E1.3 — Profil i lozinka.** Kao bilo koji korisnik, mijenjam svoj profil i lozinku.
- Svoj profil da, svoju rolu ne. Promjena lozinke traži trenutnu; lozinke se hash-uju (nikad plain, nikad u logovima).

## E2 · Ljudi, skillovi, timovi, projekti (core)

**E2.1 — CRUD nad osobama (admin).**
- Create: ime, prezime, email, lozinka, pozicija, tim, opciono slika. Edit: bilo šta od navedenog. Delete: soft delete.
- Spisak: pretraga po imenu/prezimenu, filter po timu, paginacija (kolone: Osoba, Tim, Pozicija, Top skillovi, Akcije).

**E2.2 — Katalog skillova (admin).** CRUD nad skillovima: naziv (unique), kategorija, boja. Merge duplikata je van obima.

**E2.3 — Moji skillovi (member).**
- Dodavanje sebi skilla iz kataloga sa levelom 1–5; promjena levela; uklanjanje.
- Dodavanje/uklanjanje „želim da naučim".
- Validacije iz `02·§4.1`; sve i server-side.
- **Tuđi skillovi se ne mogu mijenjati ni direktnim API pozivom** (ownership — test obavezan).

**E2.4 — Projekti (admin).** CRUD nad projektima; projekat `USES` tehnologije; dodjela ljudi sa ulogom i periodom (`WORKED_ON`).

## E3 · Import iz VacaYAY-a (core)

**E3.1 — „Import from VacaYAY".** Kao admin, želim dugme koje uvozi zaposlene iz starog sistema.
- Klik poziva VacaYAY API, mapira payload na `Person` čvorove, ubacuje, prikazuje broj uvezenih.
- **Idempotentno** — ponovni klik ne pravi duplikate (dedup po emailu, `MERGE`).
- Uvezeni bez skillova ulaze u „čeka mapiranje skillova" listu na dashboardu.
- MCP ugao: zakačiti agenta na OpenAPI/Swagger spec VacaYAY API-ja preko MCP-a i tražiti da iz live spec-a generiše DTO-ve i integraciju. Eksplicitno tražena vještina.

## E4 · Expert finder i pretraga (core — srce aplikacije)

**E4.1 — Expert finder.** Kao bilo koji korisnik, tražim ko zna određene skillove.
- Biranje jednog ili više skillova (tagovi, **AND**); opcioni filter po timu.
- Rezultat rangiran po levelu; prikazani leveli po skillu.
- Nema rezultata → **prazno stanje sa porukom, ne error**. Soft-deleted nikad u rezultatu.
- Opciono: „djelimično poklapanje" sekcija.

**E4.2 — Profil osobe.**
- Skillovi sa levelima, želje, projekti sa ulogama, mentorstva (**koga / ko njega**), mini-graf okoline (1–2 hopa).
- Sa profila: „U grafu" (skok na explorer sa fokusom) i — za admina — „Predloži mentora".

## E5 · Graf vizualizacija (core)

**E5.1 — Graf explorer.** Kao bilo koji korisnik, želim interaktivnu mapu znanja firme.
- Force-graph: čvorovi obojeni po tipu, zoom/pan.
- Klik na čvor → highlight veza + bočni panel sa detaljima i linkom na profil.
- Filteri: tip čvora (ljudi/skillovi/projekti/timovi), tim.
- **Upit za graf ima `LIMIT` / dubinu** — cio graf od 10.000 čvorova se ne šalje browseru odjednom.
- Preporuka: `react-force-graph` ili `d3-force`; dizajn i layout istražiti kroz artifacts (skice, ne kod za repo), pa implementirati kroz standardnu petlju.

## E6 · Preporuke i dashboard (core, sječivo po tempu)

**E6.1 — Mentor matching (admin).** Za osobu + željeni skill → rangirani kandidati po pravilima iz `02·§4.3`, sa vidljivim kriterijumima. Admin potvrđuje → nastaje `MENTORS`; vidi se u grafu i na profilima.

**E6.2 — Learning path.** Za sebe (member) ili bilo koga (admin): put od osobe do željenog skilla, vizuelno iscrtan (varijanta graf prikaza — samo put), + „najbliži mentor na putu". Nema puta → jasna poruka.

**E6.3 — Skill-gap dashboard (admin).**
- Metrike: aktivnih osoba, skillova, projekata, mentorstava.
- Widget „skill gap po timu": tehnologije koje tim koristi na projektima a 0 ili 1 osoba ih zna.
- Widget „bus factor": skillovi koje zna samo 1 osoba.
- Widget „čeka mapiranje skillova" (poslije importa).

> Ako vrijeme stisne: matching je obavezan; learning path smije bez rangiranja mentora; dashboard smije sa 2 widgeta.

## Advanced (bira se jedan)

- **E7 · Endorsement flow** — member klikom potvrđuje kolegin skill („+1"); poslije N potvrda `KNOWS` dobija `verified`; osoba dobija obavještenje. Ne može se endorse-ovati samog sebe; jedan endorsement po (osoba, skill, potvrdilac).
- **E8 · Staffing predlog (admin)** — ulaz: skup skillova („React + Neo4j + Docker"). Izlaz: predlog kombinacije ljudi koja pokriva sve, sa alternativama. Najzanimljiviji Cypher u projektu — plan mode obavezan.
- **E9 · Skill izvještaj (admin)** — PDF skill-matrica za osobu ili tim (analogno rješenju o odmoru iz VacaYAY-a).
- **E10 · Timeline znanja** — level kroz vrijeme, temporalne relacije.
- **E11 · CSV import/export skillova**
- **E12 · Rewrite jednog slice-a nazad u .NET** — obrnut smjer od VacaYAY-a.
- **E13 · Polish grafa** — clustering po timu, pretraga u grafu, dark mode.

---

# 04 · Arhitektura i API

## 1. Predložena struktura

Dokumentacija predlaže Next.js strukturu (`/src/app`, `/src/features/<feature>/{service,repo,dto}.ts`, `/src/lib`, `/tests/{unit,integration,e2e}`, `/docs`, `CLAUDE.md`, `docker-compose.yml`). **Ovaj repo je Spring Boot ekvivalent** — vidi `CLAUDE.md`: `com.skillatlas.<feature>` sa `*Controller` / `*Service` / `*Repository` / `dto/`.

> **Cypher živi samo u repo modulima — nikad u komponentama ni route handlerima.** Ovo pravilo ide u `CLAUDE.md`.

## 2. Frontend

- Komponente + hooks; jednostavan state (query cache) prije teškog global state-a.
- Rute zaštićene po roli — ali API svejedno odbija; klijentu se ne vjeruje.
- Expert finder i leave-request-stil forme: validacija na FE za UX, na serveru za istinu.
- Graf: `react-force-graph` (canvas) — `data-testid` disciplina za e2e, jer canvas nema selektore.
- API pozivi u malom client sloju, ne razbacani po komponentama; 401 (re-auth) vs 403 (forbidden) različito.

## 3. API katalog (reprezentativan)

REST, JSON, pod `/api/v1`. Puna površina se dizajnira sama — ovo drži oblik i imenovanje.

### Auth
| Metoda | Putanja | Ko | Svrha |
|---|---|---|---|
| POST | `/auth/login` | anon | Prijava, token |
| POST | `/auth/change-password` | self | Promjena lozinke |
| GET | `/me` | self | Trenutni korisnik |

### Ljudi (admin, osim gdje piše)
| Metoda | Putanja | Ko | Svrha |
|---|---|---|---|
| GET | `/people?search=&team=&skill=&page=` | svi | Spisak / pretraga (paginirano) |
| POST | `/people` | admin | Kreiranje |
| GET | `/people/{id}` | svi | **Profil (skillovi, projekti, mentorstva)** |
| PUT | `/people/{id}` | admin | Izmjena |
| DELETE | `/people/{id}` | admin | Soft delete |
| POST | `/people/import-vacayay` | admin | Import (E3) |

### Skillovi
| Metoda | Putanja | Ko | Svrha |
|---|---|---|---|
| GET·POST·PUT·DELETE | `/skills[/{id}]` | admin (GET svi) | Katalog |
| PUT | `/people/{id}/skills/{skillId}` | owner | Dodaj/izmijeni svoj skill (level) |
| DELETE | `/people/{id}/skills/{skillId}` | owner | Ukloni svoj skill |
| PUT·DELETE | `/people/{id}/wishes/{skillId}` | owner | Želim da naučim |

### Pretraga, graf, preporuke
| Metoda | Putanja | Ko | Svrha |
|---|---|---|---|
| GET | `/experts?skills=neo4j,docker&team=` | svi | Expert finder (AND, rangirano) |
| GET | `/graph?types=person,skill&team=&limit=` | svi | Podgraf za vizualizaciju |
| GET | `/people/{id}/learning-path?skill=` | owner/admin | Learning path |
| GET | `/people/{id}/mentor-candidates?skill=` | admin | Rangirani kandidati |
| POST | `/mentorships` | admin | Potvrda mentorstva |
| GET | `/dashboard` | admin | Metrike + gap + bus factor |

### Projekti
| Metoda | Putanja | Ko | Svrha |
|---|---|---|---|
| GET·POST·PUT·DELETE | `/projects[/{id}]` | admin (GET svi) | CRUD |
| POST·DELETE | `/projects/{id}/members/{personId}` | admin | Dodjela na projekat |

> `scope`/ownership se forsira **na serveru iz tokena** — nikad iz query parametra kome se vjeruje.

## 4. Non-functional requirements

- **Security** — v. §5, nije opciono.
- **Validacija** — server-side na svakom write-u.
- **Paginacija** — svaka lista koja raste; graf endpoint sa limitom.
- **Performanse** — bez poziva bazi u petlji (N+1); indeksi/constraints na email i naziv skilla (`CREATE CONSTRAINT ... IS UNIQUE`).
- **Config** — env varijable; secrets van repo-a.
- **Idempotentnost** — import bezbjedan za ponovno pokretanje.
- **Dokumentacija** — README pokreće projekat za ~5 min (docker-compose za Neo4j + seed + dev).

## 5. Security zahtjevi (pročitati pažljivo)

SkillAtlas drži podatke o ljudima i njihovom znanju. Fokus:

- **Cypher injection** — direktna analogija SQL injectionu. Parametri (`$search`) uvijek; string-concat nikad.
  **Test:** input `React'}) DETACH DELETE (n) //` kroz pretragu ne smije ništa da obriše — napisati test koji pada prije fiksa, prolazi poslije.
- **IDOR** — member mijenja `{id}` u `/people/{id}/skills/...` da dira tuđe skillove. Rola nije dovoljna; ownership provjera obavezna (`id == currentUserId` iz tokena). Test koji dokazuje blokadu.
- **Mass assignment** — bind na DTO; `role` i `verified` se ne postavljaju kroz profil endpoint samo zato što polje postoji.
- **XSS** — imena skillova i komentari su vektor; framework escape-uje po defaultu — ne razbijati ga (`dangerouslySetInnerHTML` i ekvivalenti).
- **Hashing lozinki** — jak algoritam (bcrypt/argon2), nikad plain, nikad u logovima.
- **Secrets** — Neo4j kredencijali kroz env; nikad u repo, nikad u agentov kontekst kao plain vrijednost.
- **AI-assisted audit** — pustiti agenta da traži gore navedeno nad PR-om, ali znati šta se lovi: agent nalazi kandidate, čovjek sudi (uključujući odbacivanje pogrešnih nalaza sa obrazloženjem).
