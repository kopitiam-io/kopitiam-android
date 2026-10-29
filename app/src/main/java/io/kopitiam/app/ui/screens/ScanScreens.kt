package io.kopitiam.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.docs.DocumentStore
import io.kopitiam.app.pdf.PdfEngine
import io.kopitiam.app.pdf.Share
import io.kopitiam.app.scan.DocumentScanner
import io.kopitiam.app.scan.IdCardPdf
import io.kopitiam.app.scan.IdPageLayout
import io.kopitiam.app.scan.PaperSize
import io.kopitiam.app.ui.components.AccentButton
import io.kopitiam.app.ui.components.PrimaryButton
import io.kopitiam.app.ui.components.ToolBody
import io.kopitiam.app.ui.components.ToolScaffold
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import java.io.File

private enum class ScanLayout { ONE_PER_IMAGE, STACKED }

@Composable
fun ScanDocumentScreen(onBack: () -> Unit) =
    ScanTool(onBack, "Scan Document",
        "Scan a page straight into a PDF. Capture as many pages as you like — each becomes a page in one document.",
        ScanLayout.ONE_PER_IMAGE, "Scan.pdf", pageLimit = null)

@Composable
fun ScanIdCardScreen(onBack: () -> Unit) =
    ScanTool(onBack, "Scan ID Card",
        "Scan the FRONT of the ID first, then the BACK. Both sides are placed stacked on a single PDF page — ideal for a passport, driver's licence, or membership card.",
        ScanLayout.STACKED, "ID-Card.pdf", pageLimit = 2)

@Composable
fun ScanBatchScreen(onBack: () -> Unit) =
    ScanTool(onBack, "Batch Scan",
        "Scan many pages in one session — receipts, a contract, a stack of forms. Every captured page lands in a single PDF, in order.",
        ScanLayout.ONE_PER_IMAGE, "Batch-Scan.pdf", pageLimit = null)

@Composable
private fun ScanTool(onBack: () -> Unit, title: String, intro: String, layout: ScanLayout, outputName: String, pageLimit: Int?) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val activity = context as? Activity
    var output by remember { mutableStateOf<File?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var unsupported by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pages: List<Bitmap> = DocumentScanner.pagesFrom(context, result.data)
            if (pages.isNotEmpty()) {
                try {
                    val made = when (layout) {
                        ScanLayout.ONE_PER_IMAGE -> PdfEngine.pdfFromImages(context, pages, outputName)
                        // Real card size, not stretched to the page width.
                        ScanLayout.STACKED -> IdCardPdf.make(
                            context, pages, IdPageLayout.ONE_PAGE, PaperSize.regionDefault(), outputName,
                        )
                    }
                    // Keep a copy on the phone so it shows in Recents and Files.
                    val prefix = if (layout == ScanLayout.STACKED) "ID Card" else "Scan"
                    output = runCatching { DocumentStore.save(context, made, DocumentStore.title(prefix)).file }
                        .getOrDefault(made)
                    pageCount = if (layout == ScanLayout.STACKED) 1 else pages.size
                    error = null
                } catch (e: Exception) { error = e.message }
            }
        }
    }

    fun startScan() {
        val act = activity ?: run { unsupported = true; return }
        DocumentScanner.requestIntent(
            context, act, pageLimit,
            onReady = { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
            onError = { unsupported = true; error = null },
        )
    }

    ToolScaffold(title, onBack) { padding ->
        ToolBody(padding) {
            Text(intro, color = kopi.textSoft, fontSize = 15.sp)
            if (unsupported) {
                UnsupportedState()
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(kopi.surface).border(1.dp, kopi.line, RoundedCornerShape(Radius.lg)).padding(Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(Icons.Filled.CameraAlt, null, tint = kopi.brand, modifier = Modifier.size(44.dp))
                    Text("Point the camera at your document — Kopitiam finds the edges and straightens each page automatically.",
                        color = kopi.textSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
                PrimaryButton(if (output == null) "Start scanning" else "Scan again", icon = Icons.Filled.CameraAlt) { startScan() }
                output?.let { file ->
                    Text("Captured $pageCount page${if (pageCount == 1) "" else "s"} → one PDF.", color = kopi.accent, fontSize = 15.sp)
                    AccentButton("Save & Share", icon = Icons.Filled.Description) { Share.share(context, listOf(file)) }
                }
            }
            error?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFD1332B), fontSize = 13.sp) }
        }
    }
}

/** Themed no-camera state — the Android mirror of iOS ScannerUnsupportedState. */
@Composable
private fun UnsupportedState() {
    val kopi = LocalKopi.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(kopi.surface).border(1.dp, kopi.line, RoundedCornerShape(Radius.lg)).padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Icon(Icons.Filled.NoPhotography, null, tint = kopi.brand, modifier = Modifier.size(52.dp))
        Text("Scanning needs a camera", color = kopi.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "Open Kopitiam on a phone or tablet with a camera to scan documents into PDFs. This device (or the emulator) has no camera available, so scanning is disabled here.",
            color = kopi.textSoft, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
    }
}
