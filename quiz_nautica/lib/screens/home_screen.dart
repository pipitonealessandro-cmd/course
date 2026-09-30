import 'package:flutter/material.dart';

import '../logic/domanda.dart';
import '../logic/progressi.dart';
import '../logic/sessione.dart';
import 'quiz_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key, required this.banca, required this.progressi});

  final BancaDati banca;
  final Progressi progressi;

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const _config = ConfigEsame.entro12Miglia;

  Future<void> _apri(Widget schermata) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => schermata));
    // Aggiorna statistiche e contatore del ripasso al ritorno.
    setState(() {});
  }

  void _simulazione() => _apri(
    QuizScreen(
      titolo: 'Simulazione esame',
      domande: generaEsame(widget.banca.domande, _config),
      progressi: widget.progressi,
      esame: _config,
    ),
  );

  void _ripasso() {
    final domande = widget.banca.perId(widget.progressi.daRipassare)..shuffle();
    _apri(QuizScreen(titolo: 'Ripasso errori', domande: domande, progressi: widget.progressi));
  }

  Future<void> _sceltaArgomento() async {
    final argomento = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            for (final a in widget.banca.argomenti)
              ListTile(
                title: Text(a),
                trailing: Text('${widget.banca.perArgomento(a).length}'),
                onTap: () => Navigator.pop(context, a),
              ),
          ],
        ),
      ),
    );
    if (argomento == null) return;
    _apri(
      QuizScreen(
        titolo: argomento,
        domande: widget.banca.perArgomento(argomento)..shuffle(),
        progressi: widget.progressi,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final esami = widget.progressi.esami;
    final superati = esami.where((e) => e.superato).length;
    final daRipassare = widget.progressi.daRipassare.length;

    return Scaffold(
      appBar: AppBar(title: const Text('Quiz Patente Nautica')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (widget.banca.esempio)
            Card(
              color: tema.colorScheme.tertiaryContainer,
              child: const Padding(
                padding: EdgeInsets.all(12),
                child: Text(
                  'Versione di prova: le domande sono esempi, non i quiz ministeriali ufficiali.',
                ),
              ),
            ),
          const SizedBox(height: 8),
          Row(
            children: [
              _Statistica(etichetta: 'Esami fatti', valore: '${esami.length}'),
              _Statistica(etichetta: 'Superati', valore: '$superati'),
              _Statistica(etichetta: 'Da ripassare', valore: '$daRipassare'),
            ],
          ),
          const SizedBox(height: 24),
          _Azione(
            icona: Icons.timer_outlined,
            titolo: 'Simulazione esame',
            sottotitolo:
                '${_config.numeroDomande} domande, massimo ${_config.erroriMassimi} errori, '
                '${_config.durata.inMinutes} minuti',
            onTap: _simulazione,
          ),
          _Azione(
            icona: Icons.menu_book_outlined,
            titolo: 'Esercitati per argomento',
            sottotitolo: 'Correzione immediata con spiegazione',
            onTap: _sceltaArgomento,
          ),
          _Azione(
            icona: Icons.replay,
            titolo: 'Ripassa gli errori',
            sottotitolo: daRipassare == 0
                ? 'Nessuna domanda da ripassare'
                : '$daRipassare domande sbagliate da rifare',
            onTap: daRipassare == 0 ? null : _ripasso,
          ),
        ],
      ),
    );
  }
}

class _Statistica extends StatelessWidget {
  const _Statistica({required this.etichetta, required this.valore});

  final String etichetta;
  final String valore;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    return Expanded(
      child: Column(
        children: [
          Text(valore, style: tema.textTheme.headlineMedium),
          Text(etichetta, style: tema.textTheme.bodySmall),
        ],
      ),
    );
  }
}

class _Azione extends StatelessWidget {
  const _Azione({
    required this.icona,
    required this.titolo,
    required this.sottotitolo,
    required this.onTap,
  });

  final IconData icona;
  final String titolo;
  final String sottotitolo;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        enabled: onTap != null,
        leading: Icon(icona, size: 32),
        title: Text(titolo),
        subtitle: Text(sottotitolo),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
