# Deploy

Jedan Linux server, jedna komanda. Angular je unutar Spring Boot jara, pa su frontend i API isti
proces na istom portu — nema odvojenog frontend hosta i nema CORS-a.

```text
browser
  └─→ http://<host>:8080/          → index.html      ┐ isti proces (app kontejner)
      http://<host>:8080/api/v1/   → REST API        ┘
                                        ├─→ bolt://neo4j:7687   (interna mreža, bez published porta)
                                        └─→ azurite:10000        (SAS link ide direktno u browser)
```

## Preduslovi

- Docker Engine 24+ i Docker Compose v2 (`docker compose version`)
- Otvoreni portovi: **8080** (aplikacija) i **10000** (Azurite — vidi „Profilne slike")

## Prvi deploy

```bash
git clone <repo> skillatlas && cd skillatlas
cp .env.prod.example .env.prod
```

Popuni `.env.prod`. Tri vrijednosti generišeš, ne izmišljaš:

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 32   # AZURITE_KEY
openssl rand -base64 24   # NEO4J_PASSWORD
```

`AZURE_STORAGE_CONNECTION_STRING` sastavi tako da `AccountKey` bude **isti** kao `AZURITE_KEY`, a
`<host>` javno ime servera:

```
DefaultEndpointsProtocol=http;AccountName=skillatlas;AccountKey=<AZURITE_KEY>;BlobEndpoint=http://<host>:10000/skillatlas;
```

Pa:

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

`--env-file` nije opcion: bez njega `${NEO4J_PASSWORD}` i `${AZURITE_KEY}` u compose fajlu nemaju
odakle da se popune i `up` odmah pukne.

Prvi build traje nekoliko minuta (npm + maven). Kasniji su brži zbog keša slojeva.

## Varijanta bez servera: Render + Neo4j Aura Free

Ako nemaš server, cijela aplikacija staje na dva besplatna servisa, bez kartice. Compose se tu ne
koristi — Render vodi jedan kontejner — ali `Dockerfile` je isti.

```text
browser ──→ Render (1 web servis)  ──neo4j+s://──→  Neo4j Aura Free
            Angular + API, isti origin
```

**1. Baza.** `console.neo4j.io` → AuraDB Free. Lozinka se prikazuje **samo jednom** pri kreiranju —
skini fajl sa kredencijalima. URI je oblika `neo4j+s://<id>.databases.neo4j.io`.

**2. Aplikacija.** Render → New → **Blueprint**, poveži ovaj repo. Render pročita `render.yaml` i
zatraži četiri vrijednosti koje nisu u repou: `JWT_SECRET`, `NEO4J_URI`, `NEO4J_USERNAME`,
`NEO4J_PASSWORD`.

**3. Prvi admin.** Na praznoj bazi dodaj još i `SKILLATLAS_ADMIN_BOOTSTRAP_EMAIL` i
`SKILLATLAS_ADMIN_BOOTSTRAP_PASSWORD`. Ako je admin već napravljen (npr. lokalnim pokretanjem protiv
Aure), `AdminBootstrap` ga preskače i ove dvije ne trebaju.

### Šta na ovoj varijanti ne radi

**Profilne slike.** Render free nema trajni disk, pa Azurite nema gdje živjeti.
`AZURE_STORAGE_CONNECTION_STRING` ostaje na dev defaultu koji pokažuje u prazno, pa **upload avatara
vraća 500**. Sve ostalo radi — `avatarUrl` je `null` i liste se prikazuju normalno.

**Stalna dostupnost.** Render free uspavljuje instancu nakon 15 minuta neaktivnosti; prvi zahtjev
poslije toga čeka 30–60 s. Aura Free pauzira bazu nakon ~72 sata mirovanja i budi se ručno iz
konzole. Demo koji niko ne otvori mjesec dana može biti i obrisan — provjeri politiku u Aura konzoli.

**Memorija je tijesna.** 512 MB ukupno, pa `render.yaml` postavlja `-Xmx320m`. Ako se servis ruši
bez jasne greške, prvo posumnjaj na OOM.

## Env varijable

| Varijabla | Obavezna | Napomena |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | da | `prod` — bez njega vrijede dev defaulti iz `application.yml` |
| `NEO4J_PASSWORD` | da | Ista vrijednost ide i Neo4j kontejneru i aplikaciji |
| `NEO4J_USERNAME`, `NEO4J_DATABASE` | ne | `neo4j` / `neo4j` |
| `JWT_SECRET` | da | Min 32 znaka. **Prod profil se bez njega ne pokreće** — namjerno |
| `JWT_EXP_MINUTES` | ne | `120` |
| `SKILLATLAS_ADMIN_BOOTSTRAP_EMAIL` | prvi put | Bez njega nema kako ući u praznu bazu |
| `SKILLATLAS_ADMIN_BOOTSTRAP_PASSWORD` | prvi put | Min 8 znakova; nikad se ne loguje |
| `AZURITE_KEY` | da | base64; mora se poklopiti sa `AccountKey` u connection stringu |
| `AZURE_STORAGE_CONNECTION_STRING` | da | `BlobEndpoint` mora biti javno ime servera |
| `SECURITY_CORS_ALLOWED_ORIGINS` | ne | Prazno je ispravno kad frontend dolazi iz istog jara |
| `VACAYAY_*` | ne | Prazno = dugme Import vraća „servis nedostupan"; ostalo radi |

`NEO4J_URI` ne postavljaj — `docker-compose.prod.yml` ga fiksira na `bolt://neo4j:7687`.

## Prvi admin

`DevSeeder` je u prod profilu ugašen, pa baza starta prazna. `AdminBootstrap` napravi jedan admin
nalog iz `SKILLATLAS_ADMIN_BOOTSTRAP_*`, i to **samo dok nijedan admin ne postoji** — kasniji restarti
ga preskoče i to zapišu u log. Poslije prvog logina te dvije varijable možeš izbaciti iz `.env.prod`.

## Provjera da radi

```bash
docker compose -f docker-compose.prod.yml logs app | grep -i -e seed -e admin
curl -s localhost:8080/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/people   # očekuje se 401
```

Da provjeriš da secret zaista *mora* biti postavljen, izbaci `JWT_SECRET` iz `.env.prod` i pokreni
ponovo. Pazi šta gledaš: zbog `restart: unless-stopped` `docker ps` i dalje pokazuje **Up**, jer
compose stalno diže novi pokušaj. Pravi signali su da health nikad ne napusti `starting` i da
`RestartCount` raste:

```bash
docker inspect skillatlas-prod-app-1 --format 'restarts={{.RestartCount}}'
docker compose -f docker-compose.prod.yml logs app | grep -i placeholder
```

Očekivano: `Could not resolve placeholder 'JWT_SECRET'` i `curl` koji ne dobija odgovor. Vrati secret.

U logu ne smije biti nijedne `DevSeeder` linije. Zatim u browseru: login, otvori profil, pa
**Ctrl+Shift+R** na toj dubokoj ruti (mora se učitati ekran, ne Whitelabel Error Page).

Za slike otvori **DevTools → Network** i pogledaj URL avatara. Host mora biti ime servera, ne
`127.0.0.1`. Na tvojoj mašini će se slika prikazati i kad je URL pogrešan — jer je tvoj `127.0.0.1`
slučajno pravi Azurite — pa je URL jedini pouzdan test. `403` skoro uvijek znači da se `AccountName`
i putanja u `BlobEndpoint` ne poklapaju.

## Profilne slike

Backend ne servira slike. Vraća **SAS link koji otvara browser korisnika**, direktno prema Azuriteu
([AzureAvatarStorage.java](../src/main/java/com/skillatlas/storage/AzureAvatarStorage.java)). Otud tri
posljedice:

1. Port `10000` mora biti dostupan izvana — za razliku od Neo4ja, kojem pristupa samo aplikacija.
2. `BlobEndpoint` mora biti javno ime servera. `UseDevelopmentStorage=true` daje `127.0.0.1`, što u
   tuđem browseru znači njegov računar.
3. Nalog je `skillatlas` sa vlastitim ključem, ne podrazumijevani `devstoreaccount1` — njegov ključ je
   javno objavljen, pa bi svako mogao sam potpisati link za bilo koju sliku.

Prihvaćen rizik na čistom HTTP-u: presretnut SAS link otvara tu sliku dok ne istekne
(`AZURE_AVATAR_SAS_MINUTES=15`, potpis je read-only).

## Upgrade

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

`SchemaInitializer` je idempotentan, podaci ostaju u volumenima.

## Backup — dva volumena, ne jedan

```bash
docker compose -f docker-compose.prod.yml exec neo4j \
  neo4j-admin database dump neo4j --to-path=/data/backup
docker run --rm -v skillatlas_azurite-data:/data -v "$PWD":/out alpine \
  tar czf /out/azurite-$(date +%F).tar.gz -C /data .
```

`neo4j-data` drži graf, `azurite-data` drži **sve profilne slike**. Backup samo baze daje obnovljen
graf u kojem svaki avatar puca na 404.

## HTTPS (Caddy)

JWT živi u `localStorage` i preko čistog HTTP-a putuje otvoren, pa čim aplikaciju koriste pravi
ljudi, stavi je iza TLS-a. Caddy sam uzima Let's Encrypt sertifikat.

`Caddyfile`:

```
skillatlas.firma.com {
    handle_path /blob/* {
        reverse_proxy azurite:10000
    }
    reverse_proxy app:8080
}
```

Servis u composeu:

```yaml
  caddy:
    image: caddy:2
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy-data:/data
    restart: unless-stopped
```

Uz to obavezno idu dvije izmjene, inače se slike **tiho** prestanu prikazivati: skini `ports` sa
`azurite` i `app` servisa, i prepiši connection string na

```
BlobEndpoint=https://skillatlas.firma.com/blob/skillatlas;
```

Razlog: stranica je `https`, a slika bi ostala `http://...:10000` — Chrome to blokira kao mixed
content, prijavi u konzoli, a sama aplikacija ne prijavi ništa. SAS potpis pokriva putanju bez hosta
pa preživljava proxy, ali provjeri Network tabom čim uključiš TLS.
