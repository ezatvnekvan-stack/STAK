# Opcionális könyvtárszűrő beállítása

A szűrő az appban külön engedélyezett teljes torrentekhez készít hivatkozásokat. Filmet nem tölt le, az eredeti TorBox-tartalmat nem törli. Az app önmagában, a Jellyfin könyvtárainak módosítása nélkül nem szűri a meglévő médiát.

1. A `.env.example` másolatából készíts privát `.env` fájlt. Minden mintabeli útvonalat és kulcsot a saját telepítésed alapján tölts ki. A kitöltött fájlt ne töltsd fel GitHubra.
2. A `ZURG_SOURCE_DIR` egy meglévő és ellenőrzött forráskönyvtárra mutasson. A kimenet és az állapot külön, általad választott könyvtárat kapjon. Üres könyvtár létrehozásával ne helyettesíts hiányzó mountot.
3. Ellenőrizd a `docker compose config --quiet` eredményét, majd indítsd a szűrőt a `docker compose up -d --build` paranccsal. A részletes működés az angol README-ben szerepel.
4. A STAK Library → Zurg → Library access részében add meg a saját elérhető címet és a külön hozzáférési tokent.
5. A Jellyfin kizárólag a szűrt Movies/Shows kimenetet vizsgálja. A `jellyfin-volumes.txt` változókat használó sablon; a saját útvonalakat külön kell beállítani és ellenőrizni. A Jellyfinben a hivatkozások céljának is elérhetőnek kell lennie.

A szolgáltatást meglévő, megbízható helyi/VPN-kapcsolaton vagy megfelelően védett HTTPS-en érd el. Az automatikus scan külön bekapcsolható lehetőség, alapból ki van kapcsolva. Sikeres kapcsolódás, mount, közzététel és Jellyfin-indexelés külön állapot.
