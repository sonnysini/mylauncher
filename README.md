# My Launcher

Launcher per Android TV / Fire TV, leggero e senza pubblicità, scritto in Kotlin con Jetpack Compose for TV.

**Stato:** in sviluppo — header con meteo e orologio, riga *Continua a guardare* da Nuvio, riga app con riordino e app nascoste.

## Roadmap
- Righe Calcio e canali da OpenTV
- Hero dinamico e sfondo con i colori del contenuto a fuoco

## Requisiti
Android 7.1 (API 25) o superiore. Pensato per Fire TV Stick 4K (Fire OS 6).

## Build
La build gira su GitHub Actions (`.github/workflows/build.yml`); i tag `v*` pubblicano l'APK nelle Release.

```
./gradlew assembleDebug
```

## Nuvio
La riga *Continua a guardare* legge l'avanzamento dall'account [Nuvio](https://github.com/NuvioMedia/NuvioTV) (GPL-3.0) con lo stesso accesso a QR dell'app; i metadati arrivano da Cinemeta. URL e chiave pubblica del backend non sono nel repository: la build li legge dalle variabili `NUVIO_SUPABASE_URL` e `NUVIO_SUPABASE_ANON_KEY` (senza, la riga non compare).

## Licenza
[GPL-3.0](LICENSE)
