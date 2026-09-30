import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:quiz_nautica/logic/domanda.dart';
import 'package:quiz_nautica/logic/progressi.dart';
import 'package:quiz_nautica/logic/sessione.dart';
import 'package:shared_preferences/shared_preferences.dart';

Domanda _d(String id, {int corretta = 0}) => Domanda(
  id: id,
  argomento: 'Test',
  testo: 'Domanda $id',
  risposte: const ['A', 'B', 'C'],
  corretta: corretta,
);

void main() {
  group('Banca dati', () {
    test('assets/domande.json è valida', () {
      final json = jsonDecode(File('assets/domande.json').readAsStringSync());
      final banca = BancaDati.fromJson(json as Map<String, dynamic>);
      expect(banca.domande, isNotEmpty);
      expect(banca.argomenti, isNotEmpty);
      for (final d in banca.domande) {
        expect(d.risposte.length, 3, reason: d.id);
      }
    });

    test('rifiuta indice della risposta corretta fuori range', () {
      expect(
        () => Domanda.fromJson({
          'id': 'x',
          'argomento': 'a',
          'testo': 't',
          'risposte': ['a', 'b'],
          'corretta': 2,
        }),
        throwsFormatException,
      );
    });

    test('rifiuta ID duplicati', () {
      final d = {
        'id': 'x',
        'argomento': 'a',
        'testo': 't',
        'risposte': ['a'],
        'corretta': 0,
      };
      expect(
        () => BancaDati.fromJson({
          'domande': [d, d],
        }),
        throwsFormatException,
      );
    });
  });

  group('Esame', () {
    final tutte = [for (var i = 0; i < 50; i++) _d('$i')];

    test('estrae 20 domande senza ripetizioni', () {
      final esame = generaEsame(tutte, ConfigEsame.entro12Miglia, Random(1));
      expect(esame.length, 20);
      expect(esame.map((d) => d.id).toSet().length, 20);
    });

    test('con poche domande le usa tutte', () {
      expect(generaEsame(tutte.take(5).toList(), ConfigEsame.entro12Miglia).length, 5);
    });

    test('3 errori superato, 4 errori bocciato; le non risposte contano come errori', () {
      final domande = tutte.take(20).toList();
      final s = Sessione(domande);
      for (var i = 0; i < 17; i++) {
        s.rispondi(i, 0);
      }
      s.rispondi(17, 1); // sbagliata
      // 18 e 19 senza risposta
      expect(s.errori, 3);
      expect(s.superato(ConfigEsame.entro12Miglia), isTrue);

      s.rispondi(0, 2);
      expect(s.errori, 4);
      expect(s.superato(ConfigEsame.entro12Miglia), isFalse);
      expect(s.sbagliate.length, 4);
      expect(s.giuste.length, 16);
    });

    test('rifiuta una scelta inesistente', () {
      expect(() => Sessione([_d('a')]).rispondi(0, 3), throwsRangeError);
    });
  });

  group('Progressi', () {
    test('le sbagliate entrano nel ripasso e ne escono quando giuste', () async {
      SharedPreferences.setMockInitialValues({});
      final p = await Progressi.carica();
      await p.registra(sbagliate: [_d('a'), _d('b')]);
      expect(p.daRipassare, {'a', 'b'});
      await p.registra(giuste: [_d('a')]);
      expect(p.daRipassare, {'b'});
    });

    test('salva lo storico degli esami', () async {
      SharedPreferences.setMockInitialValues({});
      final p = await Progressi.carica();
      await p.aggiungiEsame(EsitoEsame(data: DateTime(2026, 9, 30), errori: 2, superato: true));
      expect(p.esami.single.errori, 2);
      expect(p.esami.single.superato, isTrue);
    });
  });
}
