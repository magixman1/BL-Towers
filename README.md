# Light Towers BLE v3 (Android APK)

Natywna aplikacja Android (Kotlin + Jetpack Compose) do sterowania świetlnymi barami Bluetooth LE (**ELK-BLEDOM / Elkotrol**).

## Nowe funkcje w v3
- **Zapisywanie urządzeń i auto-łączenie:** automatyczne zapamiętywanie połączonych barów, nadawanie własnych nazw (np. *Bar Lewy*, *Bar Prawy*) oraz opcja automatycznego łączenia po uruchomieniu aplikacji.
- **Systemowe uruchamianie Bluetooth i uprawnień:** okno żądania włączenia Bluetooth (`ACTION_REQUEST_ENABLE`) oraz uprawnień `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` i lokalizacji.
- **Rozbudowana personalizacja kolorów:** płynne spektrum HSV + nasycenie, temperatura barwowa bieli w Kelwinach (`2000K–9000K`), suwaki RGB z wpisem HEX oraz zapisywanie własnych ulubionych kolorów.
- **Zwiększony poziom jasności (1% – 150% Turbo Boost):** rozszerzony zakres sterowania jasnością wraz z przyciskami szybkiego wyboru (`25%`, `50%`, `100%`, `125% MAX`, `150% MAX`).
- **Zamiana kanałów RGB:** 6 kombinacji (`RGB`, `RBG`, `GRB`, `GBR`, `BRG`, `BGR`) zapisywanych osobno dla każdego baru.
