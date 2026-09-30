# Melodia 🎵

App Android personale, stile Spotify, che riproduce la musica di **YouTube Music**:

- **senza pubblicità**
- **solo audio** (consuma pochi dati)
- **anche a schermo spento**, con i comandi nella schermata di blocco, nelle notifiche e sulle cuffie Bluetooth
- con il **tuo account YouTube**: le tue playlist, i brani che ti piacciono, album, artisti e cronologia

> Non è pubblicata su nessuno store. È pensata per essere installata a mano su pochi telefoni (il tuo e quello di tua moglie).

---

## 1. Scaricare l'app

Ogni volta che il codice cambia, GitHub compila automaticamente l'app.

1. Dal telefono apri la pagina **Releases** del repository:
   `https://github.com/pipitonealessandro-cmd/course/releases`
   (se il repository è privato, prima accedi a GitHub dal browser del telefono).
2. Apri l'ultima versione ("Melodia 1.0.x") e tocca **Melodia.apk** per scaricarlo.

## 2. Installarla

1. Apri il file scaricato (dalla notifica o dall'app *File* → *Download*).
2. Android chiederà di **consentire l'installazione da questa fonte**: attiva l'interruttore per il browser (Chrome) e torna indietro.
3. Tocca **Installa**. Se compare Play Protect, scegli *Installa comunque*: l'app non è sul Play Store, quindi Google non la conosce.

Per gli **aggiornamenti** basta scaricare e installare il nuovo `Melodia.apk` sopra la versione vecchia: i dati e l'accesso restano.

## 3. Primo avvio

1. Consenti le **notifiche**: servono per i comandi di riproduzione a schermo spento.
2. Tocca **Accedi** e fai login con l'account Google/YouTube.
   - Consiglio: usa un **account Google creato apposta** per l'app, non quello principale (vedi *Avvertenze*).
   - Se Google blocca il login ("browser non sicuro"), usa *Impostazioni → Accesso avanzato (incolla cookie)*.
3. Vai in **Impostazioni → Disattiva ottimizzazione batteria**. Su Xiaomi, Samsung, Huawei, Oppo ecc. è importante: senza questa impostazione il telefono può fermare la musica dopo qualche minuto a schermo spento.

## Android Auto 🚗

Melodia compare tra le app musicali di Android Auto, con Home, Brani che ti piacciono, Playlist, Ascoltati di recente, ricerca e comandi dal volante o vocali ("Ok Google, metti Vasco Rossi su Melodia").

Android Auto però nasconde le app non installate dal Play Store. Una volta sola, sul telefono:

1. Apri **Impostazioni → Dispositivi connessi → Android Auto** (oppure cerca "Android Auto" nelle impostazioni).
2. Scorri in fondo e tocca **10 volte** la voce **Versione**, poi conferma: si attivano le impostazioni sviluppatore.
3. Dal menu **⋮** in alto apri **Impostazioni sviluppatore** e attiva **Origini sconosciute**.
4. Scollega e ricollega il telefono all'auto.

## Cosa si può fare

| Schermata | Funzioni |
|---|---|
| **Home** | Consigli personalizzati di YouTube Music, mix, nuove uscite |
| **Cerca** | Suggerimenti mentre scrivi; filtri Brani / Video / Album / Artisti / Playlist |
| **La tua libreria** | Brani che ti piacciono, playlist, album, artisti, cronologia |
| **Player** | Casuale, ripeti, coda modificabile, "Mi piace", timer di spegnimento |
| **Menu ⋮ di un brano** | Riproduci dopo, aggiungi alla coda, radio del brano, aggiungi a playlist, vai ad artista/album, condividi |

Altre cose utili:

- **Riproduzione automatica**: quando la coda sta per finire (un singolo brano, un album, una playlist) la musica continua da sola con brani simili, dello stesso artista o di artisti e generi affini. Si disattiva nelle impostazioni.
- **Cache**: i brani ascoltati di recente vengono salvati (fino a 1 GB) e riascoltarli non consuma dati.
- **Qualità audio**: *Alta* oppure *Risparmio dati*.

## Avvertenze

- L'app usa le interfacce **non ufficiali** di YouTube (come NewPipe, ViMusic, InnerTune). Questo **viola i Termini di Servizio di YouTube**: in teoria Google può limitare o sospendere l'account usato. Per questo conviene un account dedicato.
- YouTube cambia spesso i suoi sistemi. Se un giorno i brani smettono di partire:
  1. aspetta qualche giorno che esca un aggiornamento di [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor);
  2. su GitHub vai in **Actions → Melodia APK → Run workflow** per ricompilare l'app con la versione più recente (lo fa comunque da sola ogni lunedì);
  3. installa il nuovo `Melodia.apk` dalla pagina Releases.
- La password non viene mai salvata: dopo il login l'app conserva solo i cookie di sessione, sul telefono. *Esci* nelle impostazioni li cancella.

---

## Per sviluppatori

```
melodia/
├── innertube/   client YouTube Music (Kotlin puro, testabile senza Android)
└── app/         app Android: Jetpack Compose + Media3 (ExoPlayer, MediaSession)
```

- **Riproduzione**: `PlaybackService` è un `MediaSessionService` in foreground: ExoPlayer con wake lock di rete, cache su disco e un `ResolvingDataSource` che trasforma l'id del video in un URL audio diretto solo quando serve (gli URL di YouTube scadono dopo qualche ora).
- **Flussi audio**: `StreamResolver` usa NewPipeExtractor; se non funziona, prova il client InnerTube `ANDROID_VR`.
- **Account**: login con WebView su accounts.google.com; le richieste firmate usano l'header `SAPISIDHASH` calcolato dal cookie.

Comandi:

```bash
./gradlew -p innertube test                     # test del parser (offline)
./gradlew -p innertube test -PliveTests=true    # test contro YouTube Music reale
./gradlew :app:assembleRelease                  # APK (serve l'Android SDK)
```

La chiave di firma in `keystore/` è inclusa apposta, così ogni build di GitHub Actions aggiorna l'app già installata. È un'app per uso personale: non riutilizzare questa chiave per altro.
