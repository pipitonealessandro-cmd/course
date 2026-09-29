# Cronologia delle decisioni prese (sessione claude.ai)

In ordine cronologico. Utile per capire il "perché" delle scelte nel
modello e per non riproporre opzioni già scartate.

## Correzioni alla lettura della piantina originale

- Cucina e soggiorno erano scambiati nella prima lettura: la parte NORD
  (verso la zona notte) è il **soggiorno**, la parte SUD è la **cucina**.
- La stanza sopra il bagno grande non è un vano separato: è la **doccia**
  del bagno grande, senza muro divisorio (solo vetro), 1,30×1,00 m.
- Il "ripostiglio a fianco alla doccia" è aperto sul corridoio (niente
  muro/porta sul lato fronte), con un **pilastro** nell'angolo verso il
  corridoio (~25×45 cm, stimato, non quotato).
- Il muro tra cameretta e doccia/ripostiglio è uno spessore muro normale
  (non un cavedio), la cameretta è stata allungata di conseguenza per
  toccare quel muro.

## Arredi soggiorno

- Parete attrezzata con TV: sul muro della lavanderia (2,70 m), con
  mobile basso, due colonne laterali, mensola.
- Divano: inizialmente ipotizzato generico, poi sostituito con un
  **divano ad L reale**, modello **Robin Inspire "Tosaro" 240×165 cm**
  (scheda tecnica fornita dall'utente via foto, con tutte le quote:
  seduta 55 cm, altezza seduta 50 cm, bracciolo 20×h60, schienale h87,
  poggiatesta h103, chaise longue larga 71 cm, profondità totale 95 cm).
  Sostituisce una prima ipotesi di divano generico 2,30×2,03 m — SCARTATA.
- Divano posizionato **contro il muro destro** (lato lavanderia/bagno
  grande), chaise a destra verso la TV, spostato progressivamente più
  vicino alla parete attrezzata (versione finale: bordo posteriore del
  divano a 12,50 m, cioè a circa 0,95 m dal mobile TV lato chaise).
- Pouf/tavolino: provati e poi TOLTI (l'utente ha chiesto "senza
  tavolino" per lasciare spazio).
- Un **divisorio a listelli in legno** vicino all'ingresso fu proposto e
  costruito, poi **rimosso su richiesta esplicita** dell'utente
  ("Togli il divisorio del soggiorno") — non riproporlo senza che lo
  richieda di nuovo.
- **Mobile d'ingresso ad angolo** con specchio (non un semplice
  scarpiera): lato corto 0,80 m sul muro dell'ingresso, lato lungo 1,20 m
  verso la cucina, profondo 0,30 m, alto 0,90 m, specchio 0,80×1,05 m.
- **Porta blindata**: anta 1,00 m, cerniere a sinistra (guardando da
  fuori), apertura verso l'interno — disegnata nel modello con l'arco di
  apertura tracciato a terra.

## Aperture con infissi scorrevoli (elementi custom nel modello)

1. **Porta scorrevole vetro** nel corridoio zona notte → soggiorno, a
   fianco della parete attrezzata: apertura 1,10 m, anta in vetro con
   telaio scuro, binario a soffitto, mostrata a metà apertura.
2. **Vetrata telescopica a 3 ante** tra cucina e soggiorno: apertura
   allargata a **1,80 m** (dato esplicito dell'utente, "l'apertura è di
   180 cm"), 3 ante da ~0,60 m che si accatastano **tutte sul lato
   destro** (verso l'interno/cucina profonda, NON verso il corridoio),
   su un unico binario telescopico — non ante che si aprono ciascuna per
   conto proprio (bifold), ma un vero sistema telescopico a pacchetto.

## Facciata / prospetto esterno

- Fu costruita una versione "moderna" della facciata nel modello 3D
  (tetto piano, pensilina sopra il portoncino, boiserie in legno intorno
  alla porta, infissi scuri) — poi **rimossa su richiesta esplicita**
  ("Niente togli il pulsante facciata e il tetto"). Il modello attuale
  NON ha tetto né facciata "vestita".
- Fu creata anche un'illustrazione SVG piatta in stile architettonico del
  prospetto d'ingresso (tipo Design/artboard) — **non piaciuta
  all'utente** ("Non mi piace"), che chiedeva qualcosa di più vicino a un
  render 3D vero/fotorealistico, non ottenibile nell'ambiente claude.ai
  (niente Blender/motori di rendering, niente WebGL affidabile sul suo
  telefono). Da qui la richiesta di passare il lavoro a Claude Code.
- Se in Claude Code è disponibile un motore di rendering vero (Blender,
  Three.js con WebGL, ecc.), è del tutto ragionevole ripartire da questa
  geometria e produrre un render molto più realistico — l'utente lo
  apprezzerebbe.

## Preferenze di stile generali emerse

- Stile moderno, intonaco chiaro, infissi/telai scuri, legno caldo come
  accento (boiserie, mobili). Non ci sono indicazioni contrarie a questo
  stile: è probabilmente un buon punto di partenza per un render vero.
- L'utente controlla con attenzione le misure e chiede spesso di
  verificarle o correggerle con precisione (vedi ad es. la correzione
  della finestra cucina, l'apertura cucina/soggiorno a 1,80 m, lo
  spessore del muro sopra doccia/ripostiglio). Continuare a essere
  precisi con le quote è importante per lui.
