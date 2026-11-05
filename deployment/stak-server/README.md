# STAK Server 1.2.3

Egy Docker-konténer a korábbi `stak-watch` és `stak-library-filter` helyett.
A Zurg és a Jellyfin továbbra is a meglévő szolgáltatásod marad.

## Mit végez?

- Sorozat Auto-add a meglévő szabályokkal, kizárólag frissen igazolt TorBox-cache alapján.
- Közvetlen, folyamaton belüli átadás a rendezőnek, külön filter URL/token nélkül.
- A STAK-ból jóváhagyott **filmek és sorozatok** rendezése.
- Egy sorozat → egy azonosított sorozatmappa → `Season 01`, `Season 02` stb.
- Külön epizódfájlokat tartalmazó csomagok epizódonként kerülnek a megfelelő évadba.
- Egy fájlban lévő több epizód egyetlen, `S01E01-E02` nevű hivatkozás marad.
- Filmek saját cím/év/provider-azonosítós mappába kerülnek. Több, bizonytalan főfilmet nem találgat össze.
- A kapcsolódó feliratok megmaradnak. A minták és trailerek nem lesznek fő epizódok.
- Rendezés után Jellyfin-scan, csak ellenőrzött Jellyfin-könyvtárútvonalak mellett.
- Eredeti videók mozgatása, átnevezése, másolása vagy megnyitása nélkül: a szerver hivatkozásokat készít.

Példa:

```text
movies/A Film (2026) [imdbid-tt1234567]/A Film (2026) [imdbid-tt1234567] - abcdef12 2160p.mkv
shows/A Series (2026) [imdbid-tt7654321]/Season 01/A Series S01E01 [stak-abcdef12 2160p].mkv
```

A szabványos struktúra a [Jellyfin dokumentációját](https://jellyfin.org/docs/general/server/media/shows/) követi.
A több minőségben meglévő ugyanazon epizód külön, azonosítható verzió marad. Nincs automatikus minőségcsere vagy törlés.

## Meglévő két szolgáltatás összevonása

Linuxon Python 3 és Docker Compose szükséges. A meglévő konténerek neve legyen `stak-watch`, `stak-library-filter`, `jellyfin`.
**Ne töröld a régi mappákat, konténereket vagy adatbázisokat.** A migráló ezekből dolgozik.

1. Csomagold ezt a könyvtárat `~/stak-server` alá. A kész privát `.env` és `torznab.json` fájlokat nem kell kézzel másolni.
2. Előkészítés:

```bash
cd ~/stak-server
sudo python3 migrate.py prepare
```

Ez a Dockerből kiolvassa a meglévő beállításokat, ellenőrzi az azonos TorBox-fiókot,
600-as jogosultságú beállításokat és ellenőrzött SQLite-mentéseket készít, majd felépíti az új képet.
**A régi szolgáltatásokat még nem állítja le, és a médiakönyvtárat nem rendezi át.**

3. Rendezési terv, videófájlok módosítása nélkül:

```bash
docker compose run --rm --no-deps -T stak-server python -m server.admin plan
```

A `sudo` a régi, privát adatfájlok olvasásához kell; az új beállítások a bejelentkezett felhasználód tulajdonában maradnak.

A `ready` sorok rendezhetők. A `needs_attention` soroknál hiányzik egy értelmezhető epizódszám vagy egyértelmű forrás; meglévő hivatkozásaik megmaradnak. Az új Android-frissítés
a korábbi STAK-hozzáadások eltárolt médiaazonosítóit is át tudja adni.
A terv a másolt adatbázisokat dúsíthatja az ismert watcher-azonosítókkal; nem ad hozzá torrentet és nem készít médiahivatkozásokat.

4. Átállítás:

```bash
sudo python3 migrate.py apply
docker compose exec -T stak-server python -m server.admin status
```

Az `apply` a mentés idejére leállítja a Jellyfint, teljes privát `/config`-mentést készít,
majd újraindítja. Utána leállítja a két régi szolgáltatást, azok legfrissebb adatbázisát ismét
ellenőrzötten átmásolja, és elindítja az egyetlen `stak-server` konténert.
Sikeres indulás után a régi konténerek automatikus újraindítását kikapcsolja, de nem törli őket.

A Jellyfin jelenlegi Docker-volume sorait nem kell átírni: a rendező ugyanazt a dedikált kimeneti mappát használja.
Csak a saját adatbázisában nyilvántartott hivatkozásokat cseréli le. A megszakított átrendezéshez helyreállítási napló tartozik.

## App

A mellékelt Android-frissítés: STAK 1.2.0 / code60, eredeti alkalmazásazonosítóval és aláírással.
Frissítésként telepítsd, ne töröld az alkalmazást.

- A korábbi 8055-ös watcher-cím tovább működik.
- A korábbi 8787-es filter-cím átmenetileg ugyanabban az egy konténerben elérhető, a régi tokennel.
- A közös párosításhoz a meglévő Auto-add beállításban mentsd a STAK Server címét és a meglévő watcher-tokenedet.
  Az app a rendező csatlakozását is ehhez állítja. Nem kell új tokeneket vagy szolgáltatási API-kulcsokat létrehozni.
- Új appban a szerveren létrejött jóváhagyások is megjelennek a Library access listában.
- Az app a jóváhagyott tartalmak médiaazonosítóit átadja; a csak metaadatot frissítő kérés nem hagy jóvá újra visszavont elemeket.

Az egyetlen új szervercím: `http://LAPTOP_IP:8055`. A könyvtár API-ja ezen belül `library/` alatt található.
A régi kompatibilitási port később kikapcsolható: `STAK_LEGACY_FILTER_PORT=` és `docker compose up -d --force-recreate`.

## Következő szerverfrissítés

Az új fájlokat ugyanebbe a könyvtárba bontsd ki, majd:

```bash
sh update.sh
```

Ez nem írja felül a saját `.env`, `torznab.json` és `data` fájljaidat. Mindkét adatbázisról WAL-t is tartalmazó mentést készít.
A Docker újraindításkor automatikusan indítja a szolgáltatást. Teljes laptopkikapcsolás vagy Zurg/TorBox-kimaradás alatt természetesen nem dolgozhat.

## Visszaállás

```bash
sudo python3 migrate.py rollback
```

A legfrissebb watcher-napló és jóváhagyások visszakerülnek a régi szolgáltatásokhoz, így nem egy elavult
mentésből indulnak újra a TorBox-hozzáadások. A régi filter visszaépíti a korábbi hivatkozási struktúráját.
Ez **nem** állítja vissza automatikusan a Jellyfin teljes konfigurációját.

## Nézési adatok és korlátok

A STAK beállításait és történeti megfigyeléseit nem töröljük. A szerver nem állít watched/unwatched állapotot.
**A Jellyfin a megváltozott útvonalakhoz új elem-ID-t rendelhet; a régi nézettség automatikus összekapcsolása nem garantálható.**
Ezért készül a teljes Jellyfin-konfiguráció mentése a `data/backups/*-jellyfin-config.tar.gz` fájlba.
Ha szükséges, a Jellyfin leállítása mellett ebből visszaállítható a régi konfiguráció; ez azóta keletkezett Jellyfin-változásokat is visszavonhat.
Ne töröld a mentést a sikeres ellenőrzés előtt.

Nem azonosítunk filmet/sorozatot pusztán hasonló név alapján. A rendezéshez nem kötelező IMDb/TMDB-azonosító: a jóváhagyott tartalmak tisztított címmel és explicit epizódszámokkal is automatikusan rendeződnek. Az Identify opcionális pontosítás. A hibás fájlnévből nem készül találgatott epizód.

A `.env`, `torznab.json`, `migration.json`, `data/` és a Jellyfin-mentés privát; ezeket soha ne töltsd fel GitHubra.

## 1.2.1 — Jellyfin-hitelesítés javítása

Az útvonalak ellenőrzése és az automatikus scan most a támogatott Authorization: MediaBrowser Token fejlécet használja, a watcherhez hasonlóan. A régi X-Emby-Token fejlécet a Jellyfin 12 alapból letiltja. A meglévő Jellyfin-kulcs, szabályok és jóváhagyások megmaradnak. Már összevont 1.2.0 szervernél csak a csomagot bontsd ki a meglévő mappába, majd sh update.sh; az átállító nem szükséges újra. Android-frissítés ehhez a javításhoz nem kell.

Hivatalos változás: https://jellyfin.org/posts/jellyfin-release-12.0/#http-api

## 1.2.2 — automatikus rendezés, kézi azonosítás nélkül

A hiányzó IMDb/TMDB-kód többé nem akadályozza a jóváhagyott filmek és sorozatok rendezését. A meglévő pontos azonosítók megmaradnak; új azonosítót nem találunk ki.

- A tiszta cím leválik a weboldal-előtagokról, release- és technikai jelölésekről.
- Egy sorozat külön torrentekben érkező részei közös sorozat- és évadmappába kerülnek.
- Az évszám megjelenik, ha megállapítható; a 2160p felbontás nem számít évnek.
- A meglévő és az új jóváhagyásokat is kezeli, ismeretlen kategóriánál a szokásos sorozatjelöléseket felismeri.
- Az aláhúzásos vagy tagolt epizódszámok, valamint az explicit Season 01 mappában lévő Episode 02 fájlok is működnek.
- Az eredeti film/sorozat fájlja marad a helyén; csak a saját hivatkozások rendeződnek át.

Már futó STAK Server esetén: csomag kibontása a meglévő könyvtárba, majd `sh update.sh`. Az app és a beállítások maradnak; ismételt migráció nem szükséges.


## 1.2.3: téves többepizódos felismerés javítása

Az epizódszám után álló, például e5dbdc39 kezdetű torrentazonosítót a Jellyfin E05-ként értelmezhette. Az új név:

`The Leftovers S03E01 [stak-e5dbdc39 1080p].mkv`

A valós többepizódos jelölés megmarad: `S03E01-E02 [stak-e5dbdc39 1080p].mkv`.
A meglévő jóváhagyások a következő sikeres rendezési ciklusban automatikusan átállnak; nem kell eltávolítani/újra hozzáadni a torrenteket. Csak a nyilvántartott epizód- és feliratlinkek neve változik; filmek neve, eredeti videók, mappák, tokenek, szabályok nem változnak. Bizonytalan forrás vagy ütközés esetén a korábbi linkek megmaradnak. A második ciklus már nem nevez át és nem kér új scan-t.

A frissítés előtt legyen aktuális Jellyfin /config mentés, mert az új epizódfájlnevek miatt új Jellyfin-elem-azonosítók keletkezhetnek. A STAK nem ír watched állapotot. Az update.sh továbbra is ellenőrzött SQLite-mentést készít.

Frissítés után először várd meg a könyvtárvizsgálat végét. Ha a hibás összefűzött címek maradnak, csak az érintett sorozaton kérj teljes metaadatfrissítést. Több teljes scan indítása nem gyorsítja a folyamatot. Ehhez a javításhoz új Android APK nem szükséges.
