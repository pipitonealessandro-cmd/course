---
name: casa-piantina
description: Dati, misure e decisioni di progetto per la casa di Alex ricostruita da una piantina cartacea (villa monofamiliare in Italia, garage + zona notte + zona giorno). Usare questa skill ogni volta che si lavora sul modello 3D della casa, si disegna il prospetto, si discutono arredi, aperture, porte scorrevoli/telescopiche o le misure delle stanze, o quando l'utente fa riferimento a "la piantina", "il modello della casa", "il soggiorno/cucina", "la vetrata", "il portoncino" o simili. Contiene le coordinate esatte dei muri (in metri) e l'elenco di tutte le richieste e correzioni fatte dall'utente in una lunga sessione di modellazione 3D su claude.ai.
---

# Casa — piantina e modello 3D

Questa skill raccoglie tutto quello che è stato ricavato e deciso lavorando
su un modello 3D interattivo della casa di Alex, partendo da una foto di
piantina cartacea (planimetria catastale/progetto, non quotata per gli
spazi esterni). Il modello finale (HTML, three-JS-like fatto a mano in
canvas 2D) è incluso in `assets/casa-3d-v3.html` ed è la fonte di verità
più aggiornata: contiene TUTTE le coordinate dei muri, porte, finestre e
arredi già impostate nelle unità corrette (metri).

**Se devi continuare il lavoro sul modello 3D o sul prospetto, apri prima
`assets/casa-3d-v3.html`**: è un file singolo autosufficiente (HTML+JS,
niente librerie esterne) con un sistema di coordinate in metri, un motore
di rendering 3D scritto a mano (proiezione prospettica su canvas 2D,
niente WebGL — scelta fatta apposta perché il WebGL non funzionava sul
telefono dell'utente) e tutta la geometria della casa già corretta e
verificata contro le quote della piantina originale.

## Struttura della casa (assi)

Sistema di coordinate del modello: X = est-ovest, Z (mappato da "y" in
metri nel codice) = nord-sud, Y = altezza. Origine arbitraria a
CX=2.45, CY=9.4 (vedi il file per il dettaglio). Tutte le misure sono in
metri, altezza muri H=2.7 m.

Leggi `references/misure.md` per l'elenco completo di stanze, muri,
porte/finestre con le coordinate esatte, e `references/decisioni.md` per
la cronologia delle scelte di arredo e le correzioni fatte dall'utente
(utile per capire *perché* certe cose sono come sono, non solo *cosa*
sono).

## Cosa NON è disponibile in questo ambiente (attenzione se stai leggendo
questa skill da Claude Code)

Il lavoro fatto su claude.ai non aveva accesso a:
- Un motore di render fotorealistico (no Blender, no V-Ray/Lumion/Cycles).
- WebGL affidabile lato client (il telefono dell'utente non lo
  supportava bene, per questo il modello usa un renderer canvas 2D scritto
  a mano invece di three.js).

Se in Claude Code hai accesso a Blender, a un motore Three.js con WebGL
vero, o ad altri strumenti di rendering, **puoi e dovresti usarli**: era
un limite dell'ambiente precedente, non una scelta di design da rispettare.
La geometria in `assets/casa-3d-v3.html` (coordinate muri/aperture/arredi)
resta comunque la fonte di verità da cui ripartire.

## Cose ancora aperte / incerte

- **Lotto di terreno:** l'utente ha detto che il lotto totale è di circa
  300 m². Il fronte (piazzale con auto + giardino, davanti alla casa) è
  stato solo stimato a occhio dal disegno (non quotato sulla piantina):
  circa 7,8 × 11,3 m. Il retro della casa (~70 m² residui) non è
  disegnato affatto nella piantina fornita: l'utente ha confermato che
  esiste ma non ne ha dato la forma/profondità.
- **Misure non quotate sulla piantina**, dedotte per allineamento con le
  stanze vicine (segnate "≈" nel modello): larghezza di ripostiglio,
  studio e lavanderia.
- Il divano definitivo è un modello reale (Robin Inspire "Tosaro",
  240×165 cm) di cui l'utente ha fornito la scheda tecnica con le quote
  precise (vedi `references/decisioni.md`).

## File in questa skill

- `assets/casa-3d-v3.html` — il modello 3D completo e aggiornato.
- `assets/piantina-originale.jpg` — la foto della piantina cartacea da cui
  è stato ricostruito tutto.
- `references/misure.md` — tabella di tutte le stanze con le loro misure,
  e lista dei muri/porte/finestre con coordinate in metri.
- `references/decisioni.md` — cronologia delle richieste e correzioni
  fatte dall'utente durante la sessione (arredi, aperture, porte
  scorrevoli, colori, ecc.), utile per non contraddire scelte già fatte.
