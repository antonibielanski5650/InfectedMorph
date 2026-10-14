# Weryfikacja — 14.10.2026

## Rzeczywiście wykonane

- Repozytorium: `antonibielanski5650/InfectedMorph`, punkt startowy `fb19f2cdeea29ffa83caae0dd219d37d5b8b2954`.
  Zmiany na osobnej gałęzi `codex/stage1-foundation`.
- Oficjalna dokumentacja Purpur/Paper 26.2, Geyser, floodgate i MineSkin oraz publiczne sygnatury API:
  odnośniki i zakres potwierdzenia w README.
- Pobrano Maven 3.9.11 i Oracle JDK 25.0.4.1 do katalogu roboczego; nie zmieniano systemowej Java ani innych projektów.
- `mvn package` spróbowano; początkowy problem CA po pobraniu JDK naprawiono przez użycie zaufanego systemowego
  truststore `/etc/ssl/certs/java/cacerts`. Ponowna próba zatrzymała się przed kompilacją na HTTP 403 z proxy dla
  `https://repo.purpurmc.org/snapshots/.../purpur-api/26.2.build.2621-stable/...pom`.
- Osobny `javac --release 25` kompiluje rzeczywiste klasy: `SkinModel`, `Stage1SkinGenerator`, `SkinDownloader`,
  `BoundedHttp`, `SignedTexture`, `SkinPublisher`, `MineSkinPublisher`, z Gson 2.13.2 i JUnit Console 1.13.4.
  Nie użyto atrap API serwera i nie nazywamy tej kontroli pełną kompilacją pluginu.
- 8 testów JUnit: zachowanie oryginału/alpha i większości pikseli; deterministyczna personalizacja;
  wyłączanie heurystyki oczu; slim UV; odrzucanie legacy/HD; HTTPS; blokada obcych adresów;
  rzeczywisty lokalny HTTP z limitem odpowiedzi i bez przekierowań. Wszystkie przeszły na Java 25.
- `git diff --check`: bez błędów whitespace.

## Niepotwierdzone

- Pełne `mvn package`, zgodność kompilacyjna całej warstwy Bukkit/Paper i działanie JAR-a na Purpur.
- Połączenie do MineSkin i Mojang z uruchomionego pluginu, poprawność podpisu w kliencie i wygląd po podmianie.
- Własny widok Java, pozostali obserwatorzy, peleryny, restart, quit/rejoin, kolizje z innymi pluginami skinów.
- Wykrywanie aktywnych klientów przez rzeczywistą instalację Geyser/floodgate i renderowanie Bedrock.
- ModelEngine, modele 3D, resource packi i fazy 2–5 — poza zakresem implementacji.

Tryb domyślny nie zgłasza przemiany jako udanej. Instrukcja lokalnego testu i tabela zachowania są w README.
Pełny build musi zostać wykonany w środowisku z dostępem do oficjalnego repozytorium Purpur przed instalacją.
