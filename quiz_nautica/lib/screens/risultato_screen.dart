import 'package:flutter/material.dart';

import '../logic/sessione.dart';

class RisultatoScreen extends StatelessWidget {
  const RisultatoScreen({super.key, required this.sessione, this.esame});

  final Sessione sessione;
  final ConfigEsame? esame;

  @override
  Widget build(BuildContext context) {
    final tema = Theme.of(context);
    final config = esame;
    final superato = config != null && sessione.superato(config);

    final String titolo;
    final Color colore;
    if (config == null) {
      titolo = '${sessione.corrette} risposte giuste su ${sessione.domande.length}';
      colore = tema.colorScheme.primary;
    } else {
      titolo = superato ? 'Esame superato!' : 'Esame non superato';
      colore = superato ? Colors.green : tema.colorScheme.error;
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Risultato')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Icon(
            config == null ? Icons.flag : (superato ? Icons.emoji_events : Icons.sailing),
            size: 64,
            color: colore,
          ),
          const SizedBox(height: 8),
          Text(titolo, textAlign: TextAlign.center, style: tema.textTheme.headlineSmall),
          if (config != null)
            Text(
              '${sessione.errori} errori (massimo ${config.erroriMassimi})',
              textAlign: TextAlign.center,
            ),
          const SizedBox(height: 24),
          for (var i = 0; i < sessione.domande.length; i++) _riga(context, i),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Torna alla home'),
          ),
        ],
      ),
    );
  }

  Widget _riga(BuildContext context, int i) {
    final d = sessione.domande[i];
    final data = sessione.rispostaData(i);
    final giusta = sessione.eCorretta(i);
    final errore = Theme.of(context).colorScheme.error;
    return Card(
      child: ListTile(
        leading: Icon(
          giusta ? Icons.check_circle : Icons.cancel,
          color: giusta ? Colors.green : errore,
        ),
        title: Text(d.testo),
        subtitle: giusta
            ? null
            : Text(
                '${data == null ? 'Nessuna risposta' : 'Tua risposta: ${d.risposte[data]}'}\n'
                'Corretta: ${d.risposte[d.corretta]}',
              ),
      ),
    );
  }
}
