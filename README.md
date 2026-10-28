# My Launcher

Launcher per Android TV / Fire TV, leggero e senza pubblicità, scritto in Kotlin con Jetpack Compose for TV.

**Stato:** in sviluppo (0.1.0 = scheletro: header, meteo, orologio, riga app con riordino e app nascoste).

## Roadmap
- Riga *Continua a guardare* da Nuvio
- Righe Calcio e canali da OpenTV
- Hero dinamico e sfondo con i colori del contenuto a fuoco

## Requisiti
Android 7.1 (API 25) o superiore. Pensato per Fire TV Stick 4K (Fire OS 6).

## Build
La build gira su GitHub Actions (`.github/workflows/build.yml`); i tag `v*` pubblicano l'APK nelle Release.

```
./gradlew assembleDebug
```

## Licenza
[GPL-3.0](LICENSE)
