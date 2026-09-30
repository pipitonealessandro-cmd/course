import 'package:shared_preferences/shared_preferences.dart';

import 'domanda.dart';

/// Esito di una simulazione d'esame salvata nello storico.
class EsitoEsame {
  const EsitoEsame({required this.data, required this.errori, required this.superato});

  final DateTime data;
  final int errori;
  final bool superato;

  String codifica() => '${data.toIso8601String()}|$errori|${superato ? 1 : 0}';

  static EsitoEsame? decodifica(String s) {
    final parti = s.split('|');
    if (parti.length != 3) return null;
    final data = DateTime.tryParse(parti[0]);
    final errori = int.tryParse(parti[1]);
    if (data == null || errori == null) return null;
    return EsitoEsame(data: data, errori: errori, superato: parti[2] == '1');
  }
}

/// Salva sul dispositivo le domande da ripassare e lo storico degli esami.
class Progressi {
  Progressi(this._prefs);

  static const _chiaveDaRipassare = 'da_ripassare';
  static const _chiaveEsami = 'esami';

  final SharedPreferences _prefs;

  static Future<Progressi> carica() async => Progressi(await SharedPreferences.getInstance());

  Set<String> get daRipassare => (_prefs.getStringList(_chiaveDaRipassare) ?? const []).toSet();

  List<EsitoEsame> get esami => (_prefs.getStringList(_chiaveEsami) ?? const [])
      .map(EsitoEsame.decodifica)
      .whereType<EsitoEsame>()
      .toList();

  /// Le sbagliate entrano nel ripasso, le giuste ne escono.
  Future<void> registra({
    Iterable<Domanda> giuste = const [],
    Iterable<Domanda> sbagliate = const [],
  }) async {
    final set = daRipassare
      ..removeAll(giuste.map((d) => d.id))
      ..addAll(sbagliate.map((d) => d.id));
    await _prefs.setStringList(_chiaveDaRipassare, set.toList());
  }

  Future<void> aggiungiEsame(EsitoEsame esito) async {
    final lista = _prefs.getStringList(_chiaveEsami) ?? [];
    await _prefs.setStringList(_chiaveEsami, [...lista, esito.codifica()]);
  }

  Future<void> azzera() async {
    await _prefs.remove(_chiaveDaRipassare);
    await _prefs.remove(_chiaveEsami);
  }
}
