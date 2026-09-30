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

## Sessione Claude Code (render fotorealistici e visore)

- L'utente ha **confermato tutte le assunzioni** su misure non quotate: larghezze stimate di
  ripostiglio/studio/lavanderia, pilastro 25x45, finestre (davanzale 0,90-1,10, sommità 2,00-2,40),
  portone garage 2,80 x 2,40, porte interne 0,90 x 2,10, altezza 2,70, **tetto piano** con cornice
  sottile, dislivello giardino ~12 cm, muri di confine 1,20 m su strada e 1,80 m sugli altri lati,
  cancello carrabile da 3 m davanti al garage, cancelletto pedonale a 7,2 m dal muro sinistro.
- **Cucina, angolo pranzo (proposta 1 scelta dall'utente):** divanetto a panca 140 x 70 cm contro il
  muro ovest della piantina (tra l'angolo della vetrata telescopica e la porta finestra, 1,50 m di
  muro libero), tavolo 140 x 85 ruotato con il lato lungo davanti al divanetto, 2 sedie (una sul lato
  verso i mobili, una a capotavola), TV 32" su braccio orientabile sul muretto accanto alla vetrata.
  Scartata la proposta 2 (TV 43" con divanetto di fronte) perché toglieva il tavolo da 4.
- Render e visore: `casa-render/` nel repository (script Blender/Cycles `build_scene.py`, pagina
  `viewer/index.html` con modello 3D, tour 360 e galleria).
- **Porta finestra della cucina spostata** verso l'angolo in basso (est): 60 cm dall'angolo interno,
  a filo dei mobili del lavello (y 16,70-17,90). Muro libero verso la vetrata: 2,15 m. Angolo pranzo
  aggiornato: divanetto a panca 200 cm, tavolo 160 x 85, 2 sedie lato mobili = 5 posti; TV 32" invariata.
  Scartato lo spostamento verso la vetrata (sovrapponeva passaggi e toglieva il posto alla TV).

## Revisione con 12 modifiche (Claude Code)

1. Facciate **tortora**, infissi esterni **bianchi** (portoncino in noce con telaio bianco; cancelli e portone garage antracite).
2. Cucina: **frigo in colonna nell'angolo verso la vetrata** + **colonna forno/microonde** accanto; basi, alzatina e pensili accorciati di conseguenza.
3. Soggiorno: **credenza alta 180 x 40 x 200 cm contro il muro NORD** (destro in piantina), tra lo schienale del divano e il muretto della vetrata (y 12,59-14,39), ante verso il soggiorno. (Prima versione, trasversale dietro il divano, corretta dall'utente.)
4. Camera: **armadio sul muro della porta** (2,70 m, lascia libera l'anta); **letto 160 x 200 spostato di 45 cm verso la finestra**; sul muro di fronte al letto **armadio con vano TV** centrale (TV 43").
5. Tra bagno grande e lavanderia: la porta diventa una **finestrella alta a vasistas** (h 1,80-2,25); la lavanderia si raggiunge solo dal corridoio.
6. Bagno grande: l'apertura verso il retro è una **porta finestra** (luce naturale); **mobile sospeso 90 cm** con lavabo e specchio sotto il vasistas.
7. Cameretta → **cameretta doppia**: due letti 90 x 200 in fila sul muro destro con **armadio a ponte** sopra, **due scrivanie** sul muro della porta.
8. Ripostiglio in alto → **studio**: scrivania sotto la finestra, sedia, armadietto 60 x 90.
9. Bagno piccolo: **doccia in fondo sotto la finestra** (piatto ~100 x 166, vetro fisso), wc e bidet, **mobiletto 60 cm**.
10. Studio → **cameretta singola**: letto 90 x 200, scrivania sotto la finestra, armadio 1,60 m.
11. **Cancelli dalla piantina**: carrabile ~2,90 m (x -0,47..2,42), pilastro ~0,50 m, pedonale ~1,00 m (x 2,95..3,95; centro a ~7,2 m dal muro sinistro). Passo carrabile **in diagonale** dal cancello al garage (linea tratteggiata), aiuola triangolare a sinistra (palma), **fioriera** in muratura lungo il muro su strada davanti alla cucina, albero nel giardino come in piantina.
12. Visore: **sagoma di persona alta 1,75 m** trascinabile tra gli ambienti.
- Finestra del soggiorno: provate vetrate a terra (2,50 e 2,10), **poi riportata alla finestra originale** 1,00 × 1,40. Per più luce, **due feritoie verticali** 25 cm a destra del portoncino con davanzale a 1,20 (sotto c'è la scarpiera dell'ingresso). Pianta spostata accanto alla parete TV.
- Variante **scartata dall'utente**: finestra da 75 cm unita al portoncino al posto delle feritoie (davanzali a 0,85, in regola 1/8). L'utente preferisce le **due feritoie** (la prima 0,90–2,30, la seconda 1,20–2,30 sopra la scarpiera). Vetri soggiorno 2,03 m² = 1/9,9: se servirà la conformità, proporre davanzale finestra a 0,60 e/o portoncino con fascia vetrata.
- **Decisione finale ingresso:** tolte le feritoie, messa una **finestra 60 × 140** (misura standard) accanto al portoncino, allineata alla finestra del soggiorno; scarpiera abbassata a 85 cm.
- **Giardino posteriore** (disegno dell'utente sulla vista dall'alto): pavimentato dietro il garage (x -3,80..0, y -2,10..5,25; 1 m di prato con arbusti lungo il muro di fondo), passaggio largo 1 m lungo il retro della zona notte (y -1..0, x 0..8,10, dal portoncino posteriore) e sul fianco destro, **tutto pavimentato fino al muro di confine** (x 7,10..8,70) fino alla porta finestra del bagno grande; il resto a prato. Tolto l'albero dietro il garage.
- **Agrumi e orto solo nel giardino posteriore:** limone, arancio e mandarino (alberelli nani, chioma ~1,6 m) nella striscia di prato lungo il muro di fondo, a x 1,3 / 4,1 / 6,9 (passo ~2,8 m); orto in **due cassoni rialzati 1 × 3 m** (h 47 cm) sulla pavimentazione dietro il garage: uno di lattughe, uno di pomodori con tutori.
- **Giardino anteriore, altre parti pavimentate** (disegno dell'utente): la parte alta dell'aiuola triangolare a sinistra (resta a prato solo la punta verso la strada, con l'albero) e la striscia a destra del passo carrabile, dall'albero fino al cancello pedonale; l'albero resta in un'aiuola di terra 1,2 × 1,2 m. A prato restano la punta del triangolo verso la strada e la fascia davanti alla facciata (x 0,3..2,9, y 12,35..14,1).
- **W.C. garage** (1,70 × 1,20): **porta 70 cm sul lato destro** (muro verso il garage, x −1,80, y 5,90–6,60) come da piantina, nessuna porta sul lato basso; **doccia 70 × 120** a sinistra (sotto parte della finestrella), wc sul muro di fondo, lavamani 40 cm sul muro basso. Porta da far aprire verso il garage o scorrevole (verso l'interno urterebbe il wc).
- **Bagno piccolo:** doccia ridotta da 98 × 166 a **80 × 120** nell'angolo sotto la finestra (vetro fisso + ingresso 60 cm, vetro di testa); il **mobiletto 60 cm** passa nell'angolo accanto alla doccia, così la parte verso la porta resta libera (~1,8 × 1,1 m).
- **Correzione bagno piccolo:** il mobiletto nell'angolo accanto alla doccia era inutilizzabile (davanti c'era la doccia). Mobile lavabo 60 cm **riportato sul muro in basso davanti al wc** (x 1,40–2,00), nell'angolo dietro il vetro di testa della doccia uno **scaldasalviette**.
- **Cameretta doppia:** **letto a castello** 90 × 200 nell'angolo in basso a destra, **armadio tortora a 4 ante** 2,20 m sul muro destro sopra il castello (x 6,30–6,85, y 0,25–2,45), **due scrivanie** sul muro della porta come prima (x 4,15–4,75). Provato anche un armadio da 3 m al posto delle scrivanie: l'utente preferisce le scrivanie.
- **Camera:** armadio sul muro della porta ridotto a **3 ante (150 cm, x 1,35–2,85)**, così non tocca il comodino; **letto riportato al centro** della parete (y 8,10–9,70), vano TV centrato sul letto (y 8,90). Passaggio tra ante e letto 60 cm.
- **Tutti gli armadi color tortora** ("Laccato tortora"): camera, armadio TV, cameretta doppia, cameretta singola, studio.
- **Cucina, angolo pranzo (rivisto):** tolto il divanetto; al suo posto sul muro ovest una **credenza da cucina 180 × 50** (base h 0,92 con piano in noce + alzata a vetrina 1,35–2,20, stesso laccato greige della cucina). **Tavolo 160 × 85 al centro** della cucina (x 5,90–6,75, y 15,40–17,00) con **4 sedie**, 2 per lato; ~90 cm liberi verso i mobili del lavello e ~1,10 m verso le basi del lato est. Punto del tour 360 cucina spostato a (7,5; 17,3).
