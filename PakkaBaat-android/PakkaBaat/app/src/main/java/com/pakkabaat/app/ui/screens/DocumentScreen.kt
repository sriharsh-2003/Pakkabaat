package com.pakkabaat.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pakkabaat.app.data.db.CertificateEntity
import com.pakkabaat.app.data.db.StructuredDocumentEntity
import com.pakkabaat.app.pdf.PdfExporter

@Composable
fun DocumentScreen(
    document: StructuredDocumentEntity?,
    certificate: CertificateEntity?
) {
    val context = LocalContext.current
    var proofExpanded by remember { mutableStateOf(false) }

    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Agreement record", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Text(document.conditions, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                val file = PdfExporter.export(context, document, certificate)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Download PDF") }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { proofExpanded = !proofExpanded }) {
            Text(if (proofExpanded) "Hide proof details" else "Proof details")
        }
        if (proofExpanded && certificate != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recording hash (SHA-256): ${certificate.audioSha256}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text("Transcript hash (SHA-256): ${certificate.transcriptSha256}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Text(certificate.certificateStatement, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
