#!/usr/bin/env python3
"""Converte una banca dati di domande da CSV al formato di assets/domande.json.

Colonne attese (con intestazione, separatore ';' oppure ','):
    id;argomento;testo;risposta_a;risposta_b;risposta_c;corretta;spiegazione;immagine

- `corretta` è la lettera della risposta giusta: A, B o C (oppure V/F per i quiz vero/falso).
- `spiegazione` e `immagine` sono facoltative.
- Per i quiz vero/falso lasciare vuota `risposta_c` e mettere "Vero"/"Falso" in A e B.

Uso:
    python3 tool/importa_csv.py domande.csv assets/domande.json
"""

import csv
import json
import sys


def converti(percorso_csv):
    with open(percorso_csv, newline="", encoding="utf-8-sig") as f:
        campione = f.read(4096)
        f.seek(0)
        dialetto = csv.Sniffer().sniff(campione, delimiters=";,")
        righe = list(csv.DictReader(f, dialect=dialetto))

    domande, ids = [], set()
    for n, r in enumerate(righe, start=2):
        risposte = [r[k].strip() for k in ("risposta_a", "risposta_b", "risposta_c") if r.get(k, "").strip()]
        lettera = r["corretta"].strip().upper()
        lettera = {"V": "A", "F": "B"}.get(lettera, lettera)
        indice = ord(lettera) - ord("A") if len(lettera) == 1 else -1
        if not 0 <= indice < len(risposte):
            sys.exit(f"Riga {n}: risposta corretta '{r['corretta']}' non valida")
        if r["id"] in ids:
            sys.exit(f"Riga {n}: ID duplicato {r['id']}")
        ids.add(r["id"])

        domanda = {
            "id": r["id"].strip(),
            "argomento": r["argomento"].strip(),
            "testo": r["testo"].strip(),
            "risposte": risposte,
            "corretta": indice,
        }
        for campo in ("spiegazione", "immagine"):
            if r.get(campo, "").strip():
                domanda[campo] = r[campo].strip()
        domande.append(domanda)
    return {"esempio": False, "domande": domande}


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    dati = converti(sys.argv[1])
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump(dati, f, ensure_ascii=False, indent=2)
    print(f"Scritte {len(dati['domande'])} domande in {sys.argv[2]}")
