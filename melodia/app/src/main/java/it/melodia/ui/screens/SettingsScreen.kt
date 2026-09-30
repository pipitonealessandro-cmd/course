package it.melodia.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.melodia.BuildConfig
import it.melodia.MelodiaApp
import it.melodia.data.AudioQuality
import it.melodia.playback.AudioCache
import it.melodia.ui.LocalActions
import it.melodia.ui.Routes
import it.melodia.ui.components.Thumb
import it.melodia.ui.theme.SectionTitle

@SuppressLint("BatteryLife")
@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val app = MelodiaApp.instance
    val actions = LocalActions.current
    val context = LocalContext.current
    val account by app.prefs.account.collectAsState()
    val quality by app.prefs.quality.collectAsState()
    val autoRadio by app.prefs.autoRadio.collectAsState()
    var cacheSize by remember { mutableLongStateOf(runCatching { AudioCache.sizeBytes(context) }.getOrDefault(0L)) }
    var showCookieDialog by remember { mutableStateOf(false) }

    val pm = context.getSystemService(PowerManager::class.java)
    val batteryOk = pm?.isIgnoringBatteryOptimizations(context.packageName) == true

    Column(Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState())) {
        TopBar("Impostazioni") { actions.nav.popBackStack() }

        Header("Account")
        if (account != null) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(account?.photo, Modifier.size(48.dp), CircleShape)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(account!!.name, style = MaterialTheme.typography.titleMedium)
                    account?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                OutlinedButton(onClick = { app.logout() }) { Text("Esci") }
            }
        } else {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Non hai effettuato l'accesso. Puoi cercare e ascoltare comunque, ma senza le tue playlist.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(12.dp))
                Button(onClick = { actions.nav.navigate(Routes.LOGIN) }) { Text("Accedi con YouTube") }
                TextButton(onClick = { showCookieDialog = true }) { Text("Accesso avanzato (incolla cookie)") }
            }
        }
        HorizontalDivider()

        Header("Riproduzione")
        Text("Qualità audio", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyLarge)
        AudioQuality.entries.forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { app.prefs.setQuality(q) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = q == quality, onClick = { app.prefs.setQuality(q) })
                Text(q.label)
            }
        }
        Row(
            Modifier.fillMaxWidth().clickable { app.prefs.setAutoRadio(!autoRadio) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Riproduzione automatica", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Quando la coda finisce, continua con brani simili dello stesso artista o genere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = autoRadio, onCheckedChange = { app.prefs.setAutoRadio(it) })
        }
        Column(Modifier.padding(16.dp)) {
            Text("Riproduzione a schermo spento", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (batteryOk) "Ottimizzazione batteria disattivata: la musica non verrà interrotta."
                else "Alcuni telefoni (Xiaomi, Samsung, Huawei…) fermano le app in background. Disattiva l'ottimizzazione della batteria per Melodia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!batteryOk) {
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                        )
                    }.onFailure {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }) { Text("Disattiva ottimizzazione batteria") }
            }
        }
        HorizontalDivider()

        Header("Memoria")
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cache audio", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "%.0f MB usati (max 1 GB). I brani già ascoltati non consumano dati.".format(cacheSize / 1024.0 / 1024.0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = {
                AudioCache.clear(context)
                cacheSize = AudioCache.sizeBytes(context)
            }) { Text("Svuota") }
        }
        HorizontalDivider()

        Header("Informazioni")
        Text(
            "Melodia ${BuildConfig.VERSION_NAME}\nApp personale, non distribuita su store. La musica proviene da YouTube Music.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showCookieDialog) CookieDialog { showCookieDialog = false }
}

@Composable
private fun Header(text: String) {
    Text(text, style = SectionTitle, modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp))
}

@Composable
private fun CookieDialog(onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Incolla cookie") },
        text = {
            Column {
                Text(
                    "Se l'accesso normale non funziona: da un browser su PC apri music.youtube.com con l'account, " +
                        "copia l'intestazione \"Cookie\" di una richiesta (Strumenti per sviluppatori → Rete) e incollala qui.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Cookie") }, maxLines = 5)
            }
        },
        confirmButton = {
            TextButton(enabled = text.contains("SAPISID"), onClick = {
                MelodiaApp.instance.login(text.trim().removePrefix("Cookie:").trim(), null, null)
                onDismiss()
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
