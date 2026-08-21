# SkillAtlas — roadmap

Redoslijed izgradnje i status. **Ovo je fajl koji `/slice` čita** — kad se korak završi, ovdje se
ažurira status u istom PR-u.

Radna petlja: **jedan feature = jedna grana s `main` = jedan PR na `main`.** Nikad stacked —
vidi `CLAUDE.md` § *Git workflow*. Detalji feature-a su u [`spec.md`](spec.md).

**Zašto ovaj redoslijed:** finder, mentoring i dashboard su prazni dok ne postoje `KNOWS` veze.
Zato prvo ide unos znanja (E2.3), pa tek onda stvari koje ga čitaju.

| # | Korak | Spec | Status |
|---|---|---|---|
| [0] | Test infra + unique constraints | §04.4 | ✅ PR #6 |
| [1] | *(spojen u [0])* | — | ✅ |
| [2] | E2.3 · Moji skillovi — stvara `KNOWS` / `WANTS_TO_LEARN` | [E2.3](spec.md#e2--ljudi-skillovi-timovi-projekti-core) | ✅ PR #8 |
| [3] | E4.1 · Expert finder | [E4.1](spec.md#e4--expert-finder-i-pretraga-core--srce-aplikacije) | ✅ PR #10, dorada u #12 |
| [4] | E4.2 · Bogat profil osobe | [E4.2](spec.md#e4--expert-finder-i-pretraga-core--srce-aplikacije) | ✅ PR #17 |
| [5] | E5.1 · Graf endpoint + explorer | [E5.1](spec.md#e5--graf-vizualizacija-core) | ✅ PR #18 |
| [6] | E6.1/2/3 · Mentoring, learning path, dashboard | [E6](spec.md#e6--preporuke-i-dashboard-core-sječivo-po-tempu) | ✅ PR #19 |
| [6b] | E2.1 · Admin CRUD nad osobama — new person + delete | [E2.1](spec.md#e2--ljudi-skillovi-timovi-projekti-core) | ✅ `feat/people-admin-crud` |
| [6c] | E2.4 · Projekat: roster, USES editovanje, podgraf, paginacija liste | [E2.4](spec.md#e2--ljudi-skillovi-timovi-projekti-core) | ✅ |
| **[7]** | **E3 · Import iz VacaYAY-a** | [E3](spec.md#e3--import-iz-vacayay-a-core) | ⬅️ **sljedeći** |
| [8] | Dorade: change-password, logout, people search/filter, person↔team | §04.3 | ⬜ |
| [9] | Dopuna testova | §04.5 | ⬜ |
| [10] | Jedan Advanced (E7–E13) | [Advanced](spec.md#advanced-bira-se-jedan) | ⬜ |

Podrška uz gornje: graf domenski model i people/skills slice-ovi (#1, #2), projekti i timovi
(#3, #4), Angular frontend (#5), branching pravilo u `CLAUDE.md` (#7), CODEOWNERS (#9),
Nocturne form kontrole + `sa-select` (#11).

---

## [0] Test infra + constraints — gotovo

`SchemaInitializer` (unique constraints na `Person.email`, `Skill.name`, `Team.name`),
`AbstractNeo4jIT`, failsafe → `mvn verify`.

> **Odstupanje od prvobitnog plana:** Testcontainers je **izbačen** — docker-java ne može do
> Docker Desktopa preko Windows named pipe-a na ovoj mašini. Umjesto toga `AbstractNeo4jIT`
> čita `NEO4J_URI` / `NEO4J_USERNAME` / `NEO4J_PASSWORD` iz env-a, pa radi i s Neo4j Desktopom
> i s compose instancom i u CI-ju. **Ne vraćati Testcontainers** — to bi razdvojilo harness na
> dvije grane.

## [2] E2.3 · Moji skillovi — gotovo

`KNOWS` / `WANTS_TO_LEARN`, owner-only endpointi, IDOR guard, pravila iz `spec.md` §4.1.

## [3] E4.1 · Expert finder — gotovo

`GET /api/v1/experts` — AND preko `count(DISTINCT s) = $requiredCount`, rangiranje `sum(r.level)`,
soft-delete filter, obavezan Cypher-injection test. Dorada u PR #12: minimum level po skillu
(`skills=neo4j>=4,docker`), typeahead tag input, `GET /experts/coverage` (bus factor).

## [4] E4.2 · Bogat profil osobe — gotovo

`GET /api/v1/people/{id}` vraća `PersonProfileResponse` — **nadskup** `PersonResponse`-a (spisak
ljudi i finder linkovi rade nepromijenjeni): skillovi + leveli, želje, projekti + uloge + period,
timovi, mentorstva **u oba smjera**, kepovano susjedstvo.

Odluke (potvrđene i implementirane):
1. Prošireno postojeće `GET /people/{id}`, bez novog `/profile` — spec doslovno kaže da je to profil.
2. `COLLECT {}` subqueryji umjesto niza `OPTIONAL MATCH` (inače se redovi množe). Traži Neo4j ≥ 5.6; image je pinovan na 5.26.
3. Susjedstvo: jedan hop + jedini drugi hop koji se isplati crtati (`(p)-[:WORKED_ON]->(pr)-[:USES]->(s)`). Cap ide u bazi (`rels[0..$limit]`), `size(rels)` i dalje javlja pravi stepen → `truncated`.
4. `GraphNode`/`GraphEdge` su **ugniježđeni u `PersonProfileResponse`**, bez layout polja (x/y/r) — raspored je posao force-grapha na klijentu. [5] ih preuzima i može ih promovisati u `graph` paket kad dobiju drugog pozivaoca.
5. `MentorshipsRepository` — `MENTORS` write postoji samo za dev seed; admin flow koji ga zove je [6].

Uz to: DevSeeder sada seeda projekte (`USES`, `WORKED_ON`) i mentorstva — bez toga je pola profila
prazno na svježoj bazi.

Van opsega (ostaje): dugme „Predloži mentora" (traži [6]). „U grafu" je isporučeno u [5].

## [5] E5.1 · Graf endpoint + explorer — gotovo

`GET /api/v1/graph?types=&team=&limit=&rootId=&hops=` — kepovan podgraf za force-graph, čitljiv
svakom prijavljenom korisniku. `GraphNode`/`GraphEdge` promovisani iz `PersonProfileResponse` u
`com.skillatlas.graph.dto` (JSON profila nepromijenjen).

Odluke (potvrđene i implementirane):
1. **Ljudi su uvijek sjeme podgrafa**, i kad je `PERSON` isfiltriran — svaka veza osim `USES`
   polazi od osobe. Zato `team` filter ima smisla za svaku kombinaciju tipova.
2. Čvorovi se gejtuju **po svom tipu**, veze po **oba kraja**. Bez toga `types=team` vraća prazno
   platno, jer timovi u graf ulaze samo preko ljudi.
3. `rootId` + `hops` (1–2) su dodatak preko spec tabele — bez njih dugme „U grafu" sa profila
   (E4.2) nema gdje da skoči. `hops` nije `[*1..$hops]`: Neo4j ne prima parametar za granicu
   varijabilne dužine, a i ekspanzija bi pobjegla. Umjesto toga dva eksplicitna paterna.
4. DISTINCT **prije** kepa: veze se skupljaju po osobi iz sjemena, pa isti `USES` stigne jednom po
   osobi na projektu — inače duplikati troše budžet a `totalRelations` broji istu vezu više puta.
5. Front: `d3-force` (prva runtime zavisnost u `frontend/`), layout se rješava **sinhrono** — tick
   po frejmu bi vrtio change detection do iste slike. Zoom/pan preko `getScreenCTM()`.

Uz to: `/graph` više nije `WaitingForApiComponent`, a profil je dobio „Open in the graph".

## [6] E6 · Mentoring, learning path, dashboard — gotovo

Novi paketi `com.skillatlas.mentoring` i `com.skillatlas.dashboard`. `MENTORS` prestaje da bude
write koji radi samo seed: `GET /people/{id}/mentor-candidates`, `POST /mentorships`,
`DELETE /mentorships`, `GET /people/{id}/learning-path`, `GET /dashboard`.

Odluke (potvrđene i implementirane):
1. **Bez sintetičkog `score`-a.** Rangiranje je `level DESC, activeMentorships ASC, lastName ASC`, a
   red nosi `level` i `activeMentorships` — §4.3 traži da se u UI-ju vidi *zašto* je neko prvi.
2. Iz kandidata ispadaju: sam mentee, level < 3, soft-deleted, i onaj ko ga već mentoriše **za taj
   skill** (per-skill, pa mentor za Docker i dalje stoji kao kandidat za Neo4j). Opterećenost je
   `COUNT {}` subquery — bez drugog upita.
3. Pravilo „level ≥ 3" se **provjerava ponovo na POST-u**, ne samo pri listanju: lista je prijedlog
   koji klijent može zadržati, a write je mjesto gdje pravilo obavezuje. `MERGE` → dupli klik daje
   jednu relaciju.
4. `shortestPath` je **neusmjeren** i ograničen na 6 hopova: `KNOWS` ide osoba→skill a `USES`
   projekat→skill, pa usmjereni patern nikad ne bi stigao do skilla kroz projekat. Put reuse-uje
   `graph.dto.GraphNode/GraphEdge`. „Najbliži mentor" je drugi upit nad ljudima s puta — *koji* je
   najbliži odlučuje Java, gdje redoslijed puta i postoji.
5. Nema puta → `200 {found:false}`, ne 404. Oba kraja postoje, samo nisu povezani.
6. `DELETE /mentorships` nije u spec katalogu (§04) — dodat namjerno, inače je `MENTORS` nepovratan.
   Identifikuje se trojkom (mentor, mentee, skill); relacija nema svoj stabilan id.
7. Skill gap se broji **unutar tima** — ekspert van tima ne zatvara rupu tog tima.
8. **Skill gap se paginira** (`GET /dashboard/skill-gap?page=&size=`) — na seedovanoj bazi je već 37
   redova, a to je jedini widget koji raste s brojem ljudi. Overview nosi prvu stranicu (prvi paint
   ostaje jedan poziv), a okretanje stranice ne pokreće ponovo ostala tri widgeta. Najveće rupe
   (`knownBy = 0`) idu prve, pa je prva stranica ona koju vrijedi čitati.
9. **Kartica „čeka mentora"** (`GET /dashboard/mentor-requests`) — želje (`WANTS_TO_LEARN`) za koje
   ne postoji živa `MENTORS` veza, sa brojem kandidata po redu i dugmetom koje otvara isti modal.
   Admin više ne mora da obilazi profile da bi vidio ko čeka. Mentor koji je u međuvremenu obrisan
   ne računa se kao odgovor — želja se vraća u red. Red je FIFO (najstarija želja prva) i paginira
   se kao i skill gap.
10. Bus factor kartica na dashboardu više ne otvara modal za mentore (ranije `menteeId=""`, što je
   prolazilo samo dok je API bio mock): matching kreće od mentija, a skill nije menti. Red vodi na
   profil osobe od koje skill zavisi, a mentor flow kreće s profila.

Uz to: `MentorshipsRepository` preseljen iz `people` u `mentoring`, `/dashboard` više nije
`WaitingForApiComponent`, profil je dobio „mentor" (admin) i „path" akcije, a mentoring/dashboard
mockovi su **obrisani**, ne preoblikovani.

## [6b] E2.1 · Admin CRUD nad osobama — gotovo

`POST /people` i `DELETE /people/{id}` su postojali od početka, ali ih niko nije zvao: „New person"
je vodio na `/coming-soon`, a „Delete" je krio red u `Set`-u u memoriji komponente. Sada oba
dugmeta pišu u bazu.

Odluke (potvrđene i implementirane):
1. `PeopleApi` je dobio `create` i `remove`; modal je `sa-person-create` u `features/people/`, po
   uzoru na `sa-mentor-matching` — lista ostaje iza njega, bez nove rute i bez novog guarda.
2. **Bez optimističnog uklanjanja reda.** Poslije brisanja se lista ponovo čita; stranica ima 6
   redova, pa optimizam pomjera numeraciju i pager prijavljuje pogrešan raspon. Ako brisanje
   isprazni zadnju stranicu, `load()` se vrati korak nazad umjesto da pokaže „No results".
3. **Provjera emaila pri kreiranju sada ignoriše soft-delete** (`existsByEmail`, ne
   `existsByEmailAndDeletedFalse`). Unique constraint na `Person.email` ne zna za `isDeleted`, pa je
   email obrisane osobe ranije prolazio Java provjeru i padao na constraintu — 500 umjesto 409.
4. **Admin ne može obrisati sebe** (`SelfDeleteNotAllowedException` → 409). Inače ostaje s važećim
   tokenom za osobu koju svaki read filtrira, pa `GET /me` vraća 404 i klijent nema izlaz. Klijent
   uz to sakriva „Delete" na svom redu — poruka nije jedini način da se to sazna.
5. Greške se crtaju u accent paleti (`--surface-accent` / `--accent-text`), kao na sign-in ekranu —
   Nocturne nema crvenu i ovaj slice je nije uvodio.
6. Forma **nema tim ni sliku**: `PersonCreateRequest` ih ne prima, a dodjela tima je [8]. Spec E2.1
   pominje tim pri kreiranju — svjesno odstupanje, zatvara se u [8].

`PeopleAdminIT` (7 testova) pokriva 201 + pojavu u listi, 403 za membera na POST i DELETE, 400 za
kratku lozinku, 409 za email obrisane osobe, 409 za samobrisanje, i da soft delete **ostavlja čvor**
(`p.isDeleted = true`), a ne da ga briše.

## [6c] E2.4 · Ekran projekta — gotovo

`ProjectResponse` nije nosio ljude, pa je stranica projekta imala prazan spisak, mrtvo „+ add"
dugme i nacrtan (izmišljen) podgraf. Sada `GET /projects/{id}` vraća `ProjectDetailResponse`.

Odluke (potvrđene i implementirane):
1. **Dva DTO-a, ne jedan.** Lista nosi samo `memberCount`, detalj i `members` — `ProjectDetailResponse`
   je nadskup `ProjectResponse`-a, isto kao `PersonProfileResponse` nad `PersonResponse`-om.
2. **Soft-deleted osoba se na ovoj stranici vidi, i samo ovdje.** §4.6 traži oboje: „nestaje iz svih
   listi" i „istorija `WORKED_ON` ostaje (projekat i dalje zna ko je radio)". Druga rečenica nema
   gdje da se vidi osim ovdje, pa je roster jedini read koji ih nosi — označene (`left: true`), bez
   linka na profil (taj read ih filtrira) i **van `memberCount`-a**, da se kartica i zaglavlje ne
   razilaze.
3. **Head count za cijelu stranu jednim upitom** (`UNWIND $ids`), ne `count` po projektu — inače je
   to N+1 koji pravila zabranjuju.
4. **`PUT /projects/{id}` sada vraća detalj.** Ekran projekta piše kroz PUT (arhiviranje, USES), a
   odgovor bez rostera bi obrisao ljude koje ekran već prikazuje.
5. **Dodavanje tehnologije ide kroz postojeći PUT**, read-modify-write na klijentu — isti put kojim
   je `setActive` već išao. Bez novog `/{id}/skills/{skillId}` endpointa: spec katalog (§04) ga nema,
   a admin ekran je jedini pisac.
6. **Podgraf je prsten, ne zvijezda.** Ljudi se rasipaju niz lijevi luk, tehnologije niz desni, a
   svaka `KNOWS` veza je tetiva svedena kroz centar. Projekat **nije čvor** — čvorište u sredini je
   bilo baš ono što je sprečavalo da se osoba i tehnologija ikad dodirnu direktno; `WORKED_ON` i
   `USES` ionako već stoje ispisani u rosteru i u „Stack" panelu iznad, pa ih crtež ne duplira.
   Ime projekta stoji u sredini prstena, gdje mu je čvor bio.
   - Prošli su kroz `/design` kanvas kao četiri prijedloga na pravim podacima (Ribbons / Matrix /
     Ring / Cluster); izabran je Ring.
   - Oba luka se čitaju **odozgo nadolje**: ljudi u redoslijedu rostera (tabela iznad), tehnologije
     po **baricentru** (prosječna pozicija ljudi koji ih znaju), pa tetive ostaju kratke.
     Tehnologija koju niko s rostera ne zna nema baricentar i pada na dno luka.
   - Poluprečnik čvora raste sa brojem `KNOWS` veza; debljina tetive nosi level (1–3 / 4 / 5).
     Prozirnost je rezervisana za stanje (mirno / istaknuto / prigušeno), da hover ostane čitljiv.
   - Crta se prvih 8 po luku, ostatak se **prijavljuje ispod platna** umjesto da se tiho odsiječe.

7. **Kompozicija stranice preraspoređena** (prošla kroz `/design` kao četiri layouta; izabran „A ·
   Merged"). Rail od 360px je **ukinut** — držao je jednu karticu od ~200px u koloni visokoj preko
   900, a istih pet tehnologija se crtalo tri puta (Stack čipovi, Coverage redovi, labele prstena).
   - **Coverage se ulio u Stack**: jedan red po tehnologiji nosi tačku u njenoj boji, „bus factor 1"
     oznaku i dubinu (`N people ≥ 3`). Jedna lista umjesto tri.
   - Stranica je sada jedna kolona pune širine; Stack i podgraf dijele traku `1fr 1fr`, pa je
     **prsten iznad preloma** (na 1280×720 završava na 671px). Roster ide punom širinom ispod, a
     „Actions" je fiksiran na 140px umjesto rastegljivih `0.7fr`.
   - Zaglavlje: `description · period · N people` razbijen u četiri figure (People, Technologies,
     Bus factor 1, Started; + Ended kad projekat ima kraj). Bus factor je izvučen iz raila u
     zaglavlje.
   - Napomena o soft-delete-u se crta **samo kad neko na rosteru zaista jeste obrisan**, a ne kao
     stalni pasus dokumentacije.
   - `.mini` ima `max-width: 620px` = širini user-space-a, pa se crtež nikad ne skalira **iznad**
     1:1 (na 1920 bi labele od 12px postale 24px). Ispod toga se blago smanjuje s kolonom — na
     1280 je faktor 0,93, tj. 11,1px.

8. **Višestruki izbor tehnologija → finder.** Svaki red u „Stack & coverage" ima checkbox; kad je
   nešto štiklirano, dugme na dnu postaje `Find experts in all N` i vodi na
   `/finder?q=Angular>=3 + React>=3`, uz `Clear` pored njega. Bez izbora ostaje staro dugme za
   najslabiju tehnologiju.
   - **Prag je `>=3`, ne goli naziv**: red na kojem checkbox stoji broji upravo `N people ≥ 3`, pa
     rezultat odgovara broju koji je bio na ekranu u trenutku štikliranja. Finder na dolasku crta
     `≥ 3` čipove i „Parsed: KNOWS … AND KNOWS …", pa se ni prag ni AND ne kriju od korisnika.
   - Dugme za najslabiju tehnologiju je **namjerno ostavljeno na nivou 1** — to je drugo pitanje
     („ko uopšte može pomoći"), ne isti upit s drugim brojem.
   - Izbor se prazni pri učitavanju drugog projekta, a tehnologija uklonjena preko ✕ ispada iz
     izbora — inače bi `all N` brojalo nešto što više ne postoji.

9. **Roster se paginira po 8 redova**, isti pager kao katalog skillova i lista projekata.
   Paginacija je **na klijentu**, nad listom koju `GET /projects/{id}` ionako vraća cijelu:
   sve ostalo na ekranu traži **cio** roster — prvih osam za prsten, spisak za „Assign people"
   filter, i provjeru ima li obrisanih za napomenu — pa bi server-side paging značio da se te tri
   stvari dopremaju zaobilazno, za listu ograničenu veličinom tima na projektu. Kad roster preraste
   jedan odgovor, potez je dashboardov: prva strana inline, `GET /projects/{id}/members?page=` za
   ostatak.
   - Brisanje zadnjeg reda zadnje strane **vraća korak nazad** (isto kao [6b] odluka 2), a otvaranje
     drugog projekta vraća na prvu stranu.
   - Prsten ostaje na **prvih osam po rosteru**, ne prati stranu tabele — okretanje strane ne
     mijenja sliku.

`ProjectDetailIT` (5 testova) pokriva roster, obrisanu osobu van `memberCount`-a, `knows` ograničen
na stack projekta, `memberCount` bez rostera na listi, i PUT koji vraća roster.

## [7] E3 · VacaYAY import

`POST /api/v1/people/import-vacayay`, idempotentno preko `MERGE` po emailu. MCP ugao: generisati
DTO-ve iz live OpenAPI spec-a starog sistema.

## [8] Dorade

Change-password, logout, person↔team dodjela. Uz to: migrirati preostale native `<select>`-ove
(skills, projects, graph filteri) na `sa-select`.

People search/filter je urađen ranije, van reda: `GET /people?search=&team=&skill=` filtrira u
Cypheru (`PeopleSearchRepository`), a lista je sortirana `createdAt DESC` pa abecedno — nova osoba
sleti na vrh prve strane. People filteri su prešli na `sa-select`.

Skills katalog (E2.2) je dorađen isto van reda: `GET /skills` dobija `search`, `category` i
`sort=wanted` uz paging, a red nosi `knownBy` / `wantedBy` / `usedBy` iz jednog Cyphera
(`SkillsCatalogRepository`) — brojači isključuju soft-obrisane osobe. Ekran je dobio paginaciju,
`sa-select` umjesto native `<select>`-a i pravi edit preko `PUT /skills/{id}`; „most wanted"
(`sort=wanted`) i „thinnest coverage" (`sort=known`) idu zasebnim upitima jer je rang firmo-wide, a
ne presortirana strana. Ostaju projects/graph filteri.

## [9] Dopuna testova

Popuniti rupe koje su nastale usput; e2e happy-path ako ostane vremena.

## [10] Jedan Advanced

Bira se na kraju iz E7–E13. `E8 · Staffing predlog` je najzanimljiviji Cypher u projektu.
