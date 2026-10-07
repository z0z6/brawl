# Arena Brawl

Darmowa, oryginalna gra w stylu Brawl Stars (offline, tryb Showdown: Ty + 6 botów).
Bez żadnych assetów ani znaków towarowych Supercell – tylko kształty rysowane kodem.

## Jak zbudować APK na GitHubie
1. Utwórz nowe repozytorium na GitHubie i wrzuć do niego całą zawartość tego folderu (razem z `.github/`).
2. Wejdź w zakładkę **Actions** → workflow **Build Android APK** (odpala się sam po pushu, albo kliknij *Run workflow*).
3. Po zakończeniu otwórz przebieg i pobierz artefakt **ArenaBrawl-debug-apk** (zip z `app-debug.apk`).
4. Skopiuj APK na telefon, zezwól na instalację z nieznanych źródeł i zainstaluj.

## Sterowanie
- lewa połowa ekranu – joystick ruchu
- prawa połowa – przeciągnij i puść = strzał w tym kierunku; szybkie stuknięcie = auto-celowanie
- żółty przycisk SUPER – pierścień pocisków (ładuje się zadawanym obrażeniem)
- krzaki ukrywają postacie, kostki mocy zwiększają HP i obrażenia, fioletowa strefa się zawęża
