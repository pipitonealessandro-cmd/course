# Casa — render fotorealistici

Render in Blender/Cycles (path tracing) della casa ricostruita nella skill
`.claude/skills/casa-piantina`. Tutta la geometria (muri, aperture, arredi)
viene dalle coordinate di `assets/casa-3d-v3.html`, in metri.

## Immagini (`renders/`)

| File | Vista |
|---|---|
| `ext_ingresso.jpg` | Esterno dal giardino: garage, facciata d'ingresso con portoncino e porta finestra della cucina |
| `ext_aerea.jpg` | Vista aerea 3/4 del lotto |
| `int_soggiorno.jpg` | Soggiorno dall'ingresso: divano Tosaro, parete attrezzata, porta scorrevole in vetro |
| `int_divano.jpg` | Soggiorno dalla parete TV verso il divano e la vetrata telescopica della cucina |
| `int_cucina.jpg` | Cucina: tavolo, basi e pensili sul muro est, lavello sotto il finestrone 1,50 × 0,90 |
| `int_cucina2.jpg` | Cucina verso il soggiorno, attraverso la vetrata telescopica (1,80 m) |

## Scelte di stile (modifica pure)

- Intonaco chiaro, infissi in alluminio antracite, zoccolo in pietra scura, legno caldo (noce/rovere) negli arredi.
- Tetto piano con cornice sottile: la piantina non indica la copertura, serve solo a chiudere il volume.
- Pavimenti: parquet in rovere nella zona giorno e notte, gres 120×60 in cucina, gres grigio nei bagni.
- Giardino, recinzione, cancello, alberi e siepi sono **ipotesi**: il fronte è stimato (≈ 7,8 × 11,3 m) e il retro non è disegnato.
- Tolte rispetto al modello: l'auto nel piazzale. Divisorio a listelli, pouf e tavolino restano esclusi, come deciso in precedenza.

## Rigenerare

```bash
pip install bpy==5.0.1 numpy          # Blender come modulo Python (Python 3.11)
python build_scene.py --cams ext_ingresso,int_cucina --res 1920 --samples 160 --out renders
python build_scene.py --save casa.blend   # apri la scena in Blender per modificarla
```

Le camere sono nel dizionario `CAMERAS` di `build_scene.py`: posizione e target in
coordinate di pianta (x est, y sud, z quota), lente in mm, esposizione.
