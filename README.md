# InfectedMorph — faza 1 (0.1.0-SNAPSHOT)

Niezależny plugin Maven dla Purpur 26.2 i Java 25. Bez modów klienta i bez zależności od ChunkVote.
Ta wersja zawiera generator skina, komendy, diagnostykę i **eksperymentalną, domyślnie wyłączoną podmianę profilu Java**.
Nie jest jeszcze potwierdzonym rozwiązaniem wizualnym dla Java + Bedrock.

## Stan weryfikacji

Sprawdzono oficjalną dokumentację i źródła 14.10.2026. Nie uruchomiono serwera Purpur ani klientów gry.
Lokalna próba `mvn package` na Java 25.0.4.1 i Maven 3.9.11 zatrzymała się na blokadzie HTTP 403 repozytorium Purpur.
Następnie **pełne `mvn --batch-mode --no-transfer-progress package` w GitHub Actions zakończyło się BUILD SUCCESS**
na Temurin Java 25.0.4+1: skompilowano wszystkie klasy wraz z rzeczywistym API Purpur i spakowano JAR z relokowanym Gson.
8 testów JUnit przeszło (0 failures, 0 errors, 0 skipped).
[Udany build](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38594570604) i
[JAR do lokalnego testu (ZIP)](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38594570604/artifacts/11909096898).
Nie potwierdzono jeszcze uruchomienia pluginu na serwerze ani wyświetlania skinów przez klientów.
Szczegóły: [raport weryfikacji](docs/verification.md).

## Co zaimplementowano

| Funkcja | Zachowanie |
| --- | --- |
| `/infect stage1` | Sprawdza permissions, klienta i źródło skina; pobiera oryginał, generuje i zapisuje PNG poza wątkiem serwera. |
| Domyślna konfiguracja | `PREVIEW_READY`, faza `NORMAL`; komunikat jednoznacznie mówi, że wygląd nie został zmieniony. |
| Opcjonalne wyświetlanie Java | MineSkin V2 upload → podpisana tekstura → sprawdzenie modelu i zgodności pikseli → Paper `setPlayerProfile`. Implementacja wymaga testu na prawdziwych klientach. |
| `/infect normal` | Anuluje zadanie; przy aktywnym profilu odtwarza oryginalną właściwość `textures`, z jej podpisem. |
| `/infect status` | Aktualna faza, stan zadania, serwer, Java, Geyser/floodgate, wykryty klient i ograniczenia. Działa też w konsoli. |
| Bedrock jako przemieniany gracz | Komendy/status działają; przemiana zablokowana z komunikatem. Nie zakładamy, że jego profil Java jest oryginalnym skinem Bedrock. |
| Bedrock jako obserwator gracza Java | Widoczność podmiany **niezweryfikowana**, bez deklaracji wsparcia. |
| Slim/wide | Model odczytywany z metadanych profilu; różne szerokości UV ramion, zachowana mapa 64×64. |
| Rozmiar, ruch, statystyki | Plugin nie ustawia atrybutów, skali, prędkości, efektów ani ekwipunku. |

Generator kopiuje teksturę, dodaje nieliczne półprzezroczyste przebarwienia, zadrapania i rany na policzkach,
ramionach i nogach. Nie zmienia kanału alpha ani większości pikseli; zachowuje ubrania i włosy.
Rozmieszczenie zależy od UUID i oryginalnych pikseli, więc wynik jest powtarzalny i personalizowany.
To zachowawcza heurystyka UV, nie rozpoznawanie twarzy: oczy są przygaszane w standardowych pozycjach.
Dla nietypowej twarzy wyłącz `stage1.darken-standard-eyes`. Lokalny podgląd należy ocenić przed nagrywaniem.
Rany na przedramionach/nogach mogą być subtelnymi śladami na ubraniu; generator nie rozpoznaje semantycznie rękawów.
Legacy 64×32, HD, persona i niestandardowa geometria są jawnie odrzucane.

## Technologie i źródła

- [Purpur, gałąź 26.2](https://github.com/PurpurMC/Purpur/blob/ver/26.2/README.md): dziedziczy API Paper/Bukkit.
  W Maven przypięto `org.purpurmc.purpur:purpur-api:26.2.build.2621-stable`, którego publikację potwierdza
  [oficjalny indeks artefaktu](https://repo.purpurmc.org/snapshots/org/purpurmc/purpur/purpur-api/26.2.build.2621-stable/).
  Nie używamy nieaktualnego formatu `26.2-R0.1-SNAPSHOT` ani otwartego zakresu mogącego pobrać 26.3.
- [Paper 26.2 Player](https://jd.papermc.io/paper/26.2/org/bukkit/entity/Player.html): `getPlayerProfile()` zwraca kopię,
  `setPlayerProfile()` ponownie rejestruje gracza dla klientów, zachowując UUID.
  [PlayerProfile](https://jd.papermc.io/paper/26.2/com/destroystokyo/paper/profile/PlayerProfile.html) udostępnia właściwości i `clone()`.
  Oryginał to tekstura aktualnego profilu w chwili polecenia, również po zmianie przez inny plugin.
  Brak tekstury powoduje blokadę; nie pobieramy przypadkowego Steve/Alex ani nie zastępujemy tożsamości.
- [Geyser — wersje](https://geysermc.org/wiki/geyser/supported-versions/): w odczytanej dokumentacji Java 26.2,
  Bedrock 26.30–26.52. Lista jest zmienna; sprawdź ją przy instalacji. Wersja klienta/protokołu nie gwarantuje skinów.
- [Floodgate API](https://geysermc.org/wiki/floodgate/api/): `getInstance().isFloodgatePlayer(UUID)` rozpoznaje klienta, także powiązane konta.
  [GeyserApi](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/GeyserApi.java)
  dostarcza `api().connectionByUuid(UUID)`. Integracje są opcjonalne, używają refleksji po publicznych interfejsach
  z classloadera zainstalowanego pluginu. Awaria API daje `UNKNOWN` i blokadę, nie domniemanie Java.
  Wspierany układ detekcji to Geyser-Spigot/floodgate na tym samym serwerze. Układ z Geyserem wyłącznie na proxy
  wymaga lokalnego floodgate i poprawnego przekazywania danych; w tej wersji nie deklarujemy jego obsługi.
- [Geyser API](https://geysermc.org/wiki/geyser/api/) ma osobny
  [SessionSkinApplyEvent](https://github.com/GeyserMC/Geyser/blob/master/api/src/main/java/org/geysermc/geyser/api/event/bedrock/SessionSkinApplyEvent.java)
  dla tekstur i geometrii. Przyszły adapter musi obsłużyć surowy skin Bedrock, odświeżenie aktywnych sesji,
  obserwatorów, własny widok i ograniczenia persona/trusted skins. Sama metoda Paper tego nie potwierdza.
- [MineSkin — podpisy](https://docs.mineskin.org/docs/wiki/faq/),
  [V2 upload](https://docs.mineskin.org/docs/mineskin-api/generate-a-skin/),
  [migracja](https://docs.mineskin.org/docs/guides/migrating-to-v2/),
  [oficjalny OpenAPI](https://github.com/MineSkin/api.mineskin.org/blob/master/openapi.v2.json):
  używamy `POST /v2/generate`, multipart `file`, `variant=classic|slim`, `visibility=unlisted`;
  odpowiedź `skin.texture.data.value/signature`. Endpoint synchroniczny jest udokumentowany, ale usługa zaleca kolejkę;
  mały prototyp publikuje szeregowo z limitem czasu i cooldownem, bez automatycznych powtórzeń.
  Podpis przyjmujemy z zaufanej odpowiedzi HTTPS; plugin nie podpisuje tekstury sam i nie weryfikuje jej kryptograficznie.
  Sprawdza obecność/format, host, model i zgodność opublikowanych pikseli z własnym PNG.
- ModelEngine nie jest zależnością fazy 1. [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine)
  pokazuje osobną integrację dla modeli MEG4; nie potwierdza automatycznej zgodności ModelEngine/Purpur 26.2/Bedrock.
  Taką zgodność trzeba sprawdzić przy dodawaniu 3D. [Paczki Bedrock](https://geysermc.org/wiki/geyser/packs/)
  mają własną ścieżkę dystrybucji; paczka Java nie jest automatycznie paczką Bedrock.

Faza 1 nie wymaga paczki zasobów. Sam lokalny PNG lub dowolny adres HTTPS nie stanowi gotowego podpisanego skina Java.
Opcjonalna publikacja zastępuje całą podpisaną właściwość `textures`: podczas infekcji peleryna może zniknąć.
`normal` odtwarza ją z oryginalną właściwością. Nie modyfikujemy treści podpisanego payloadu, żeby dopisać pelerynę.

## Budowanie

Zainstaluj **JDK 25** i Maven 3.9.x. `java -version` i `mvn -version` muszą wskazywać JDK 25.

```sh
mvn package
```

Po udanym buildzie: `target/InfectedMorph-0.1.0-SNAPSHOT.jar` (Gson relokowany w JAR, API Purpur provided).
Możesz też pobrać ZIP z artefaktem powyższego udanego workflow i rozpakować JAR; artefakty Actions mają termin ważności.
Potrzebny jest dostęp do Maven Central i `repo.purpurmc.org`. W środowisku z proxy skonfiguruj Maven/proxy
i zaufany certyfikat CA; nie wyłączaj sprawdzania TLS. Błąd pobrania API nie jest udanym buildem.
Wersję API można świadomie nadpisać `mvn -Dpurpur.version=<sprawdzona-wersja-26.2> package`.

## Instalacja i lokalny test

1. Utwórz osobny katalog lokalnego serwera testowego. Pobierz build 26.2 z [Purpur](https://purpurmc.org/downloads),
   sprawdź wymagania i uruchom na Java 25: `java -Xms1G -Xmx2G -jar purpur.jar nogui`.
   Przeczytaj i zaakceptuj EULA w tym lokalnym katalogu. Nie kopiuj produkcyjnego świata.
2. Zatrzymaj serwer i umieść zbudowany JAR w `plugins/`. Dodaj właściwe dla tej wersji Geyser-Spigot,
   floodgate i opcjonalnie LuckPerms. Uruchom ponownie, bez `/reload`.
3. Sprawdź w konsoli włączenie InfectedMorph, Java 25 oraz tryb `PREVIEW_ONLY`.
   Konfiguracja powstaje w `plugins/InfectedMorph/config.yml`.
4. Dla gracza testowego nadaj w LuckPerms `lp user <nick> permission set infectedmorph.stage1 true`.
   Pozostałe permissions: `infectedmorph.normal` i `infectedmorph.status` (domyślnie true).
   Używamy standardowego `hasPermission`, więc LuckPerms nie jest zależnością kodu.
5. Połącz klient Java ze swoim standardowym skinem. `/infect status` → `NORMAL`.
   `/infect stage1` → generowanie → `PREVIEW_READY`, nadal `NORMAL`.
   Porównaj `plugins/InfectedMorph/previews/<UUID>/original.png` i `stage1.png`.
   Brak zmiany skina w grze w tym trybie jest oczekiwany.
6. Zbadaj dwa różne skiny, slim i wide, z włączoną i wyłączoną zewnętrzną warstwą.
   Sprawdź, czy rany są widoczne i nie niszczą charakteru skina. Konfiguracja jest odczytywana przy restarcie.
7. Test eksperymentalnego Java: zatrzymaj serwer, utwórz klucz [MineSkin](https://docs.mineskin.org/docs/guides/getting-started/),
   ustaw `INFECTEDMORPH_MINESKIN_API_KEY` w środowisku procesu serwera i `java-display.enabled: true`.
   To włącza wysyłanie wygenerowanego skina do zewnętrznej usługi; `unlisted` oznacza niepubliczny wykaz, nie prywatność.
   Nie zapisuj klucza w Git ani w komendach publikowanych w logach. Zapewnij dostęp HTTPS do `api.mineskin.org`
   i `textures.minecraft.net`. Plugin używa JVM HttpClient; przy proxy potrzebna jest konfiguracja sieci JVM.
8. Uruchom serwer. `/infect stage1` powinno zakończyć się `JAVA_PROFILE_APPLIED` dopiero po odpowiedzi usługi
   i podmianie profilu. Sprawdź F5, drugiego gracza Java, tablistę, zmianę świata i odrodzenie.
   Plugin nie może potwierdzić, że klient wyrenderował teksturę; trzeba to zobaczyć.
   `/infect normal` musi odtworzyć oryginalny skin, model i pelerynę. Nie nagrywaj serii przed tą weryfikacją.
9. Podłącz klient Bedrock przez lokalny Geyser/floodgate. `/infect status` powinno rozpoznać `BEDROCK`;
   `/infect stage1` powinno wyświetlić ograniczenie i pozostać `NORMAL`. Osobno sprawdź obserwację przemienionego
   gracza Java z Bedrock. Zapisz faktyczny wynik, wersje pluginów/klienta i platformę; nie zakładaj zgodności.
10. Sprawdź brak permission, brak tekstury, niedostępność sieci, niepoprawny klucz, cooldown/429,
    wielokrotne `stage1`, `normal` podczas zadania, wyjście/rejoin i restart.
    Żaden błąd generowania/publikacji nie może zgłaszać aktywnej przemiany.
    Jeżeli podmiana API i rollback zawiodą, `RESTORE_REQUIRED` uczciwie ostrzega o niepewnym stanie.

## Cykl życia i ograniczenia

- Wszystkie operacje Player/Bukkit są na głównym wątku; HTTP, generowanie i pliki na jednym pracowniku.
  Kolejka ma 8 miejsc. Pobranie skina ma limit 256 KiB, odpowiedź MineSkin 128 KiB; brak przekierowań.
  Źródło jest ograniczone do `textures.minecraft.net`, PNG IHDR sprawdzany przed dekodowaniem.
- `normal`, wyjście i wyłączenie unieważniają zadania. Wynik sprawdza tożsamość sesji, teksturę i permissions
  przed zastosowaniem. Wyjście/wyłączenie próbuje przywrócić własną podmianę; rejoin rozpoczyna `NORMAL`.
  Infekcja nie jest utrwalana między sesjami w tej pierwszej wersji.
- Plugin nie nadpisuje tekstury zmienionej później przez inną integrację. Status zgłasza `EXTERNAL_SKIN_CHANGE`.
  Współpraca z pluginami skinów/nicków i obciążenie serwera wymagają testów integracyjnych.
- Podglądy są nadpisywane per UUID, pozostają po wyjściu i `normal`; nie są publikowane przez WWW.
  Administrator może usunąć `previews/` przy zatrzymanym serwerze. Równocześnie przechowywane jest maksymalnie
  32 podpisanych wyników w pamięci; cache znika po restarcie.
- Brak faz 2–5, modeli 3D, animacji, dodatkowych kończyn, typów Tall/Twisted/Spine/Hollow/Claw/Brute/Crooked/Cracked/Stalker/Rib
  i Fuse Type. Żadnych pozornych komend wyboru typu. Fuse pozostaje późniejszą osobną mutacją.
- `InfectionStage`, `Stage1SkinGenerator`, `SkinPublisher` i `AppearanceBackend` rozdzielają stan, generowanie,
  publikację i prezentację. Kolejny krok: przetestowanie Java, adapter Geysera i dopiero później modele/fazy.

Nie wdrożono na produkcję, nie modyfikowano ChunkVote i nie scalono zmian z `main`.
