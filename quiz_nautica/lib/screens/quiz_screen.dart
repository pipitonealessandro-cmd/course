import 'dart:async';

import 'package:flutter/material.dart';

import '../logic/domanda.dart';
import '../logic/progressi.dart';
import '../logic/sessione.dart';
import 'risultato_screen.dart';

/// Quiz in due modalità:
/// - esame ([esame] non nullo): timer, nessuna correzione fino alla consegna;
/// - esercitazione: correzione immediata dopo ogni risposta.
class QuizScreen extends StatefulWidget {
  const QuizScreen({
    super.key,
    required this.titolo,
    required this.domande,
    required this.progressi,
    this.esame,
  });

  final String titolo;
  final List<Domanda> domande;
  final Progressi progressi;
  final ConfigEsame? esame;

  @override
  State<QuizScreen> createState() => _QuizScreenState();
}

class _QuizScreenState extends State<QuizScreen> {
  late final Sessione _sessione = Sessione(widget.domande);
  int _indice = 0;
  Timer? _timer;
  late Duration _rimasto = widget.esame?.durata ?? Duration.zero;
  bool _consegnato = false;

  bool get _modoEsame => widget.esame != null;

  @override
  void initState() {
    super.initState();
    if (_modoEsame) {
      _timer = Timer.periodic(const Duration(seconds: 1), (_) {
        setState(() => _rimasto -= const Duration(seconds: 1));
        if (_rimasto <= Duration.zero) _consegna();
      });
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _rispondi(int scelta) async {
    if (!_modoEsame && _sessione.rispostaData(_indice) != null) return;
    setState(() => _sessione.rispondi(_indice, scelta));
    if (!_modoEsame) {
      final d = widget.domande[_indice];
      final giusta = _sessione.eCorretta(_indice);
      await widget.progressi.registra(
        giuste: giusta ? [d] : const [],
        sbagliate: giusta ? const [] : [d],
      );
    }
  }

  Future<void> _consegna() async {
    if (_consegnato) return;
    _consegnato = true;
    _timer?.cancel();
    final config = widget.esame;
    if (config != null) {
      await widget.progressi.registra(giuste: _sessione.giuste, sbagliate: _sessione.sbagliate);
      await widget.progressi.aggiungiEsame(
        EsitoEsame(
          data: DateTime.now(),
          errori: _sessione.errori,
          superato: _sessione.superato(config),
        ),
      );
    }
    if (!mounted) return;
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(
        builder: (_) => RisultatoScreen(sessione: _sessione, esame: config),
      ),
    );
  }

  Future<bool> _conferma(String titolo, String testo) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(titolo),
        content: Text(testo),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Annulla')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Conferma'),
          ),
        ],
      ),
    );
    return ok ?? false;
  }

  Future<void> _chiediConsegna() async {
    final mancanti = widget.domande.length - _sessione.risposteDate;
    final testo = mancanti == 0
        ? 'Vuoi consegnare il questionario?'
        : 'Hai ancora $mancanti domande senza risposta, che contano come errori. Consegnare?';
    if (await _conferma('Consegna', testo)) await _consegna();
  }

  @override
  Widget build(BuildContext context) {
    if (widget.domande.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.titolo)),
        body: const Center(child: Text('Nessuna domanda disponibile.')),
      );
    }

    final ultima = _indice == widget.domande.length - 1;
    return PopScope(
      canPop: !_modoEsame,
      onPopInvokedWithResult: (didPop, _) async {
        if (didPop) return;
        final esci = await _conferma('Abbandonare l\'esame?', 'La simulazione non verrà salvata.');
        if (esci && context.mounted) {
          _timer?.cancel();
          Navigator.of(context).pop();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(widget.titolo),
          actions: [
            if (_modoEsame)
              Padding(
                padding: const EdgeInsets.only(right: 16),
                child: Center(child: Text(_formatta(_rimasto))),
              ),
          ],
        ),
        body: SafeArea(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              LinearProgressIndicator(value: (_indice + 1) / widget.domande.length),
              if (_modoEsame) _navigatore(),
              Expanded(child: _domanda(widget.domande[_indice])),
              Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    OutlinedButton(
                      onPressed: _indice == 0 ? null : () => setState(() => _indice--),
                      child: const Text('Indietro'),
                    ),
                    const Spacer(),
                    if (ultima)
                      FilledButton(
                        onPressed: _modoEsame ? _chiediConsegna : _consegna,
                        child: Text(_modoEsame ? 'Consegna' : 'Fine'),
                      )
                    else
                      FilledButton(
                        onPressed: () => setState(() => _indice++),
                        child: const Text('Avanti'),
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// Numeri delle domande per saltare avanti e indietro durante l'esame.
  Widget _navigatore() {
    return SizedBox(
      height: 48,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        itemCount: widget.domande.length,
        separatorBuilder: (_, _) => const SizedBox(width: 6),
        itemBuilder: (context, i) {
          final schema = Theme.of(context).colorScheme;
          final risposta = _sessione.rispostaData(i) != null;
          return ChoiceChip(
            label: Text('${i + 1}'),
            selected: i == _indice,
            backgroundColor: risposta ? schema.secondaryContainer : null,
            showCheckmark: false,
            onSelected: (_) => setState(() => _indice = i),
          );
        },
      ),
    );
  }

  Widget _domanda(Domanda d) {
    final tema = Theme.of(context);
    final data = _sessione.rispostaData(_indice);
    final mostraCorrezione = !_modoEsame && data != null;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Domanda ${_indice + 1} di ${widget.domande.length} · ${d.argomento}',
          style: tema.textTheme.labelMedium,
        ),
        const SizedBox(height: 8),
        Text(d.testo, style: tema.textTheme.titleLarge),
        if (d.immagine != null) ...[const SizedBox(height: 12), Image.asset(d.immagine!)],
        const SizedBox(height: 16),
        for (var i = 0; i < d.risposte.length; i++)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: _Opzione(
              lettera: String.fromCharCode(65 + i),
              testo: d.risposte[i],
              selezionata: data == i,
              stato: !mostraCorrezione
                  ? null
                  : i == d.corretta
                  ? true
                  : (data == i ? false : null),
              onTap: () => _rispondi(i),
            ),
          ),
        if (mostraCorrezione && d.spiegazione != null)
          Card(
            color: tema.colorScheme.surfaceContainerHighest,
            child: Padding(padding: const EdgeInsets.all(12), child: Text(d.spiegazione!)),
          ),
      ],
    );
  }

  static String _formatta(Duration d) {
    final m = d.inMinutes.toString().padLeft(2, '0');
    final s = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }
}

class _Opzione extends StatelessWidget {
  const _Opzione({
    required this.lettera,
    required this.testo,
    required this.selezionata,
    required this.stato,
    required this.onTap,
  });

  final String lettera;
  final String testo;
  final bool selezionata;

  /// `true` = corretta, `false` = sbagliata, `null` = nessuna correzione.
  final bool? stato;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final schema = Theme.of(context).colorScheme;
    final colore = switch (stato) {
      true => Colors.green,
      false => schema.error,
      null => selezionata ? schema.primary : schema.outlineVariant,
    };
    return OutlinedButton(
      style: OutlinedButton.styleFrom(
        side: BorderSide(color: colore, width: selezionata || stato != null ? 2 : 1),
        backgroundColor: selezionata ? colore.withValues(alpha: 0.08) : null,
        alignment: Alignment.centerLeft,
        padding: const EdgeInsets.all(14),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      onPressed: onTap,
      child: Row(
        children: [
          CircleAvatar(radius: 14, child: Text(lettera)),
          const SizedBox(width: 12),
          Expanded(child: Text(testo)),
          if (stato == true) const Icon(Icons.check_circle, color: Colors.green),
          if (stato == false) Icon(Icons.cancel, color: schema.error),
        ],
      ),
    );
  }
}
