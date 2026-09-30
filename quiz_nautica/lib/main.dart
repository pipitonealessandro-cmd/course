import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'logic/domanda.dart';
import 'logic/progressi.dart';
import 'screens/home_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final json = await rootBundle.loadString('assets/domande.json');
  final banca = BancaDati.fromJson(jsonDecode(json) as Map<String, dynamic>);
  final progressi = await Progressi.carica();
  runApp(QuizNauticaApp(banca: banca, progressi: progressi));
}

class QuizNauticaApp extends StatelessWidget {
  const QuizNauticaApp({super.key, required this.banca, required this.progressi});

  final BancaDati banca;
  final Progressi progressi;

  @override
  Widget build(BuildContext context) {
    const seme = Color(0xFF0B5FA5);
    return MaterialApp(
      title: 'Quiz Patente Nautica',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: seme, useMaterial3: true),
      darkTheme: ThemeData(colorSchemeSeed: seme, brightness: Brightness.dark, useMaterial3: true),
      home: HomeScreen(banca: banca, progressi: progressi),
    );
  }
}
