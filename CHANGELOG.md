# Changelog

## 0.5.0 — 29 ottobre 2026
- **Disinstalla** dal menu "tieni premuto OK" su un'app (conferma di sistema; non compare per le app di Fire OS).
- **Forza arresto · Svuota cache**: dallo stesso menu apre la pagina dell'app nelle impostazioni Fire, dove ci sono entrambi (un launcher non può farli da sé).
- **Impostazioni Fire TV** dall'ingranaggio si aprono di nuovo: il rimando del tasto Home le scambiava per la Home Amazon.
- Scegliendo **Sposta** il focus resta sull'app da spostare invece di saltare sulla prima riga; lo stesso dopo Nascondi, Annulla e Disinstalla.

## 0.4.0 — 28 ottobre 2026
- **Sfondo dinamico**: il bagliore dietro la home prende il colore della tessera a fuoco (locandina, stemma, logo del canale o banner dell'app) e sfuma da un colore all'altro.
- **Hero animato**: al cambio di tessera i dettagli entrano con una dissolvenza e un leggero scorrimento.
- **Ordine righe**: dalle impostazioni si sceglie l'ordine di Continua a guardare, Calcio, OpenTV e App (una partita live porta comunque Calcio in cima).
- **Città meteo**: dalle impostazioni si cerca la città (tastiera Fire); il nome compare sotto la temperatura.
- **Riga OpenTV**: tessera "Tutti i canali ›" in fondo che apre OpenTV.
- **Nuova icona e banner**; il logo (tessere + scritta bicolore) sostituisce il titolo in alto a sinistra.
- Spostando un'app la tessera non finisce più tagliata sul bordo sinistro.

## 0.3.0 — 28 ottobre 2026
- **Riga Calcio (da OpenTV)**: partite di Serie A live (risultato e minuto), di oggi, finite (▶ highlights) e prossime. Con una partita in diretta la riga sale in cima e prende il focus. OK su una live/prossima = "Guarda in automatico" in OpenTV, su una finita = highlights; "Tutte ›" apre la scheda Calcio.
- **Riga OpenTV**: canali visti di recente e preferiti con il programma in onda e la barra di avanzamento. OK apre il canale.
- **Hero** per partite (stemmi, risultato, canali) e canali (programma in onda e orari).
- Richiede OpenTV 0.30.0 o successiva (stessa firma): senza, le righe restano nascoste.

## 0.2.0 — 28 ottobre 2026
- **Continua a guardare (Nuvio)**: nuova riga con i titoli in corso dell'account Nuvio (film, episodio corrente o prossimo episodio), barra di avanzamento e minuti mancanti. OK apre la scheda del titolo in Nuvio.
- **Accesso Nuvio con QR**: dalle impostazioni o dalla tessera "Collega", si inquadra il QR col telefono (oppure codice su nuvio.tv/link). Scelta del profilo Nuvio.
- **Testi in italiano**: titoli, episodi e trame da TMDB in italiano; dove mancano restano quelli inglesi di Cinemeta.
- **Hero dinamico**: con un titolo Nuvio a fuoco mostra logo, episodio, trama e lo sfondo del titolo sfumato a destra.
- **Impostazioni**: l'ingranaggio apre il menu del launcher (account Nuvio, profilo, impostazioni Fire TV).
- I menu non lasciano più scappare il focus sulle righe sotto.

## 0.1.0 — 28 ottobre 2026
- **Scheletro**: launcher Android TV (Compose for TV) che si registra come Home.
- **Header**: titolo, meteo attuale (Open-Meteo), orologio con data, ingranaggio per le impostazioni di sistema.
- **Riga App**: tutte le app installate (banner TV se presente, altrimenti icona + nome), avvio con OK.
- **Tieni premuto OK** su un'app: sposta (◀ ▶, OK per confermare) o nascondi; le app nascoste si ripristinano dalla tessera in fondo alla riga.
