/// Una domanda a risposta multipla del questionario.
class Domanda {
  const Domanda({
    required this.id,
    required this.argomento,
    required this.testo,
    required this.risposte,
    required this.corretta,
    this.spiegazione,
    this.immagine,
  });

  final String id;
  final String argomento;
  final String testo;
  final List<String> risposte;

  /// Indice (da 0) della risposta corretta in [risposte].
  final int corretta;
  final String? spiegazione;

  /// Percorso di un asset immagine opzionale (es. `assets/img/fanali_1.png`).
  final String? immagine;

  factory Domanda.fromJson(Map<String, dynamic> json) {
    final risposte = List<String>.from(json['risposte'] as List);
    final corretta = json['corretta'] as int;
    if (corretta < 0 || corretta >= risposte.length) {
      throw FormatException('Domanda ${json['id']}: indice "corretta" fuori range');
    }
    return Domanda(
      id: json['id'] as String,
      argomento: json['argomento'] as String,
      testo: json['testo'] as String,
      risposte: risposte,
      corretta: corretta,
      spiegazione: json['spiegazione'] as String?,
      immagine: json['immagine'] as String?,
    );
  }
}

/// La banca dati caricata da `assets/domande.json`.
class BancaDati {
  const BancaDati({required this.domande, required this.esempio});

  final List<Domanda> domande;

  /// `true` finché si usano le domande di esempio e non quelle ministeriali.
  final bool esempio;

  factory BancaDati.fromJson(Map<String, dynamic> json) {
    final domande = (json['domande'] as List)
        .map((d) => Domanda.fromJson(d as Map<String, dynamic>))
        .toList();
    final ids = <String>{};
    for (final d in domande) {
      if (!ids.add(d.id)) throw FormatException('ID duplicato: ${d.id}');
    }
    return BancaDati(domande: domande, esempio: json['esempio'] as bool? ?? false);
  }

  List<String> get argomenti {
    final visti = <String>{};
    return [
      for (final d in domande)
        if (visti.add(d.argomento)) d.argomento,
    ];
  }

  List<Domanda> perArgomento(String argomento) =>
      domande.where((d) => d.argomento == argomento).toList();

  List<Domanda> perId(Iterable<String> ids) {
    final set = ids.toSet();
    return domande.where((d) => set.contains(d.id)).toList();
  }
}
