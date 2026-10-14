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
- Pełne `mvn --batch-mode --no-transfer-progress package` w GitHub Actions:
  [run 38594570604](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38594570604),
  kod `13421afefc60be8682cccc55320514d397643f20`, Temurin Java 25.0.4+1, **BUILD SUCCESS**.
  Kompilacja wszystkich 12 klas źródłowych z prawdziwym API Purpur 26.2; 3 klasy testowe,
  8 testów (0 failures, 0 errors, 0 skipped), pakowanie i relokacja Gson.
  [Artefakt JAR w ZIP](https://github.com/antonibielanski5650/InfectedMorph/actions/runs/38594570604/artifacts/11909096898),
  SHA-256 ZIP: `6fd87df5df21710c247c3e81cf1e1ea7884d744a85d109d438f9eb65c20e2dae`.
  Pełna kompilacja jest potwierdzona pomimo lokalnej blokady sieci. Workflow nie wdraża pluginu.

## Niepotwierdzone

- Uruchomienie JAR-a na serwerze Purpur (zgodność kompilacyjna z API jest potwierdzona).
- Połączenie do MineSkin i Mojang z uruchomionego pluginu, poprawność podpisu w kliencie i wygląd po podmianie.
- Własny widok Java, pozostali obserwatorzy, peleryny, restart, quit/rejoin, kolizje z innymi pluginami skinów.
- Wykrywanie aktywnych klientów przez rzeczywistą instalację Geyser/floodgate i renderowanie Bedrock.
- ModelEngine, modele 3D, resource packi i fazy 2–5 — poza zakresem implementacji.

Tryb domyślny nie zgłasza przemiany jako udanej. Instrukcja lokalnego testu i tabela zachowania są w README.
Pełny build można powtórzyć w środowisku z dostępem do oficjalnego repozytorium Purpur.
Zbudowany JAR jest przeznaczony do lokalnych testów, nie został wdrożony na produkcję.
