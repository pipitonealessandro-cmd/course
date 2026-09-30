import 'dart:math';

import 'domanda.dart';

/// Regole della prova d'esame.
class ConfigEsame {
  const ConfigEsame({
    required this.nome,
    required this.numeroDomande,
    required this.erroriMassimi,
    required this.durata,
  });

  final String nome;
  final int numeroDomande;
  final int erroriMassimi;
  final Duration durata;

  /// Questionario base entro 12 miglia: 20 domande a 3 risposte, al massimo
  /// 3 errori. La durata è indicativa: verificarla con le regole vigenti.
  static const entro12Miglia = ConfigEsame(
    nome: 'Entro 12 miglia',
    numeroDomande: 20,
    erroriMassimi: 3,
    durata: Duration(minutes: 30),
  );
}

/// Estrae a caso le domande di una simulazione d'esame.
List<Domanda> generaEsame(List<Domanda> tutte, ConfigEsame config, [Random? rnd]) {
  final copia = List<Domanda>.of(tutte)..shuffle(rnd ?? Random());
  return copia.take(config.numeroDomande).toList();
}

/// Stato di un quiz in corso: quali risposte ha dato l'utente.
class Sessione {
  Sessione(this.domande) : _risposte = List<int?>.filled(domande.length, null);

  final List<Domanda> domande;
  final List<int?> _risposte;

  int? rispostaData(int indice) => _risposte[indice];

  void rispondi(int indice, int scelta) {
    if (scelta < 0 || scelta >= domande[indice].risposte.length) {
      throw RangeError.index(scelta, domande[indice].risposte, 'scelta');
    }
    _risposte[indice] = scelta;
  }

  bool eCorretta(int indice) => _risposte[indice] == domande[indice].corretta;

  int get risposteDate => _risposte.where((r) => r != null).length;

  int get corrette => [
    for (var i = 0; i < domande.length; i++)
      if (eCorretta(i)) i,
  ].length;

  /// All'esame una domanda senza risposta vale come errore.
  int get errori => domande.length - corrette;

  bool superato(ConfigEsame config) => errori <= config.erroriMassimi;

  Iterable<Domanda> get sbagliate sync* {
    for (var i = 0; i < domande.length; i++) {
      if (!eCorretta(i)) yield domande[i];
    }
  }

  Iterable<Domanda> get giuste sync* {
    for (var i = 0; i < domande.length; i++) {
      if (eCorretta(i)) yield domande[i];
    }
  }
}
