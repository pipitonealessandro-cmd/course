# Quiz Patente Nautica

App Android (Flutter) per esercitarsi al questionario base della patente nautica **entro 12 miglia**.

## Funzioni

- **Simulazione esame**: 20 domande estratte a caso, 3 risposte ciascuna, massimo 3 errori, timer.
  Le domande senza risposta contano come errori.
- **Esercitazione per argomento**: correzione immediata con spiegazione.
- **Ripasso errori**: le domande sbagliate finiscono in una lista e ne escono quando le indovini.
- Statistiche (esami fatti e superati), tema chiaro e scuro, funziona offline.

## Stato attuale

`assets/domande.json` contiene **34 domande di esempio** scritte per il prototipo, **non** i quiz
ministeriali. Prima di pubblicare bisogna:

1. Procurarsi la banca dati ufficiale (circa 1.472 quiz base più 250 vero/falso per la vela,
   pubblicati dal Ministero delle Infrastrutture e dei Trasporti) e verificare le condizioni di riuso.
2. Portarla in un CSV e convertirla:

   ```bash
   python3 tool/importa_csv.py domande.csv assets/domande.json
   ```

   Il formato delle colonne è descritto in cima a `tool/importa_csv.py`. Le immagini (es. fanali)
   vanno in `assets/img/` e registrate in `pubspec.yaml`.
3. Verificare le regole d'esame vigenti (numero di domande, errori ammessi, durata) in
   `ConfigEsame.entro12Miglia` in `lib/logic/sessione.dart`.

## Struttura

```
lib/
  main.dart                 avvio: carica domande e progressi
  logic/domanda.dart        modello Domanda e BancaDati
  logic/sessione.dart       regole d'esame, estrazione e punteggio
  logic/progressi.dart      ripasso errori e storico, salvati sul telefono
  screens/                  home, quiz, risultato
assets/domande.json         banca dati delle domande
tool/importa_csv.py         conversione CSV -> JSON
test/                       test della logica e dell'interfaccia
```

## Sviluppo

Serve il [Flutter SDK](https://docs.flutter.dev/get-started/install) e, per l'APK, Android Studio
oppure l'Android SDK.

```bash
flutter pub get
flutter test                  # test
flutter run                   # su telefono o emulatore collegato
flutter build appbundle       # file .aab da caricare sul Play Store
```

## Prossimi passi verso il Play Store

- Banca dati ufficiale e immagini (vedi sopra).
- Sezione vela (vero/falso) e carteggio.
- Monetizzazione: annunci (`google_mobile_ads`) più acquisto una tantum "senza pubblicità / tutte le
  funzioni" (`in_app_purchase`).
- Icona, nome definitivo, ID applicazione (`it.quiznautica.quiz_nautica` in
  `android/app/build.gradle.kts`), firma di rilascio, privacy policy.
