import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:quiz_nautica/logic/domanda.dart';
import 'package:quiz_nautica/logic/progressi.dart';
import 'package:quiz_nautica/main.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  testWidgets('esercitazione: rispondo e vedo la correzione', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final progressi = await Progressi.carica();
    final banca = BancaDati.fromJson(
      jsonDecode(File('assets/domande.json').readAsStringSync()) as Map<String, dynamic>,
    );

    await tester.pumpWidget(QuizNauticaApp(banca: banca, progressi: progressi));
    expect(find.text('Simulazione esame'), findsOneWidget);

    await tester.tap(find.text('Esercitati per argomento'));
    await tester.pumpAndSettle();
    await tester.tap(find.text(banca.argomenti.first));
    await tester.pumpAndSettle();

    // Tocco la prima risposta: compare un'icona di correzione.
    await tester.tap(find.text('A').first);
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.check_circle), findsOneWidget);
  });

  testWidgets('simulazione esame: consegna e risultato', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final progressi = await Progressi.carica();
    final banca = BancaDati.fromJson(
      jsonDecode(File('assets/domande.json').readAsStringSync()) as Map<String, dynamic>,
    );

    await tester.pumpWidget(QuizNauticaApp(banca: banca, progressi: progressi));
    await tester.tap(find.text('Simulazione esame'));
    await tester.pumpAndSettle();
    expect(find.text('30:00'), findsOneWidget);

    // Vado all'ultima domanda e consegno senza rispondere.
    for (var i = 0; i < 19; i++) {
      await tester.tap(find.text('Avanti'));
      await tester.pump();
    }
    await tester.tap(find.text('Consegna'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Conferma'));
    await tester.pumpAndSettle();

    expect(find.text('Esame non superato'), findsOneWidget);
    expect(progressi.esami.length, 1);
    expect(progressi.daRipassare.length, 20);
  });
}
