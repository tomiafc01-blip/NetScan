package com.netscan.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.netscan.app.data.Diag
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // refresh dispara recomposición tras borrar / generar prueba.
    var refresh by remember { mutableIntStateOf(0) }
    val header = remember(refresh) { Diag.header() }
    val entries = remember(refresh) { Diag.entries().asReversed() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Diagnóstico", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                "La app guarda aquí los errores (y los cierres inesperados). Comparte el reporte para que sea fácil repararlos. Todo queda en tu teléfono hasta que lo compartas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { shareReport(ctx) },
                    label = { Text("Compartir reporte") },
                    leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.width(18.dp)) })
                AssistChip(onClick = { copyReport(ctx) },
                    label = { Text("Copiar") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.width(18.dp)) })
                AssistChip(onClick = { Diag.clear(); refresh++ },
                    label = { Text("Borrar") },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.width(18.dp)) })
                AssistChip(
                    onClick = {
                        Diag.log("PRUEBA", "Error de prueba generado por el usuario",
                            RuntimeException("Prueba de diagnóstico: todo funciona"))
                        refresh++
                        Toast.makeText(ctx, "Error de prueba registrado", Toast.LENGTH_SHORT).show()
                    },
                    label = { Text("Generar prueba") },
                    leadingIcon = { Icon(Icons.Filled.Science, null, Modifier.width(18.dp)) })
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(12.dp)
                ) {
                    Text(header, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (entries.isEmpty()) {
                        Text("Sin errores registrados. 🎉", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        entries.forEach { e ->
                            Text(e, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun shareReport(ctx: Context) {
    try {
        val file = File(ctx.cacheDir, "netscan_diagnostico.txt")
        file.writeText(Diag.report())
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Reporte de diagnóstico · NetScan")
            putExtra(Intent.EXTRA_TEXT, "Te comparto el reporte de diagnóstico de NetScan.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Compartir reporte").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No se pudo compartir: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun copyReport(ctx: Context) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("NetScan diagnóstico", Diag.report()))
    Toast.makeText(ctx, "Reporte copiado", Toast.LENGTH_SHORT).show()
}
