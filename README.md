# Light Towers BLE (Android APK)

Natywna aplikacja Android (Kotlin + Jetpack Compose) do sterowania dwoma świetlnymi barami Bluetooth LE opartymi o protokół **ELK-BLEDOM / Elkotrol** (np. `BE:16:46:...`), z wbudowaną zamianą kolejności kanałów RGB (`RGB`, `RBG`, `GRB`, `GBR`, `BRG`, `BGR`).

## Jak skompilować APK na GitHubie

1. Utwórz nowe repozytorium na GitHubie (np. `LightTowersBLE`).
2. Rozpakuj archiwum i wgraj wszystkie pliki (w tym ukryty folder `.github/workflows/build-apk.yml`) do głównego katalogu repozytorium.
3. Wejdź w zakładkę **Actions** w repozytorium na GitHubie.
4. Workflow **Build Android APK** uruchomi się automatycznie po `push` (możesz go też uruchomić ręcznie przyciskiem **Run workflow**).
5. Po zakończeniu kompilacji (ok. 2–3 minuty) pobierz plik **`LightTowersBLE-debug-apk`** z sekcji **Artifacts** na dole strony buildu.
