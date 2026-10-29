package io.kopitiam.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.docs.DocumentStore
import io.kopitiam.app.docs.SavedDocument
import io.kopitiam.app.ocr.OcrEngine
import io.kopitiam.app.pdf.PdfEngine
import io.kopitiam.app.pdf.Share
import io.kopitiam.app.scan.CameraUi
import io.kopitiam.app.scan.CardCamera
import io.kopitiam.app.scan.DocumentScanner
import io.kopitiam.app.scan.IdCardPdf
import io.kopitiam.app.scan.IdPageLayout
import io.kopitiam.app.scan.PaperSize
import io.kopitiam.app.ui.components.PrimaryButton
import io.kopitiam.app.ui.components.ToolBody
import io.kopitiam.app.ui.components.ToolScaffold
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import kotlinx.coroutines.launch

enum class ScanMode(val key: String, val title: String, val heading: String, val blurb: String) {
    SCAN("scan", "Scan", "Scan a document",
        "Kopitiam finds the edges and straightens each page. Saved as a PDF on this phone."),
    ID_CARD("idCard", "ID Cards", "ID Cards",
        "Scan the front, then the back. Printed at the card's real size, on one page or two."),
    BATCH("batch", "Batch", "Batch scan",
        "Receipts, a contract, a stack of forms. Every page lands in one PDF, in order."),
    TEXT("text", "Extract Text", "Extract text",
        "Point at printed text and get it back as text you can copy or share.");

    companion object {
        fun from(key: String?): ScanMode = entries.firstOrNull { it.key == key } ?: SCAN

        /** Tool ids from the registry that open the hub at a given mode. */
        fun forTool(toolId: String): ScanMode? = when (toolId) {
            "document" -> SCAN
            "id-card" -> ID_CARD
            "batch" -> BATCH
            else -> null
        }
    }
}

private sealed interface HubStage {
    data object Intro : HubStage
    data object Camera : HubStage
    data class Layout(val images: List<Bitmap>) : HubStage
    data class TextResult(val text: String) : HubStage
}

private const val PREFS = "kopitiam"

/**
 * CamScanner-style launcher: one illustrated intro per mode, a Capture button,
 * and a mode carousel along the bottom. Always dark, like a camera.
 */
@Composable
fun ScanHubScreen(initial: ScanMode, onClose: () -> Unit, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(initial) }
    var stage by remember { mutableStateOf<HubStage>(HubStage.Intro) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<List<Bitmap>?>(null) }

    fun saveScan(images: List<Bitmap>) {
        try {
            val pdf = PdfEngine.pdfFromImages(context, images, "Scan.pdf")
            val prefix = if (mode == ScanMode.BATCH) "Batch" else "Scan"
            val doc = DocumentStore.save(context, pdf, DocumentStore.title(prefix))
            onOpenDocument(doc.id)
        } catch (e: Exception) {
            error = e.message
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val pages = DocumentScanner.pagesFrom(context, result.data)
        if (pages.isEmpty()) return@rememberLauncherForActivityResult
        if (mode == ScanMode.TEXT) {
            working = true
            scope.launch {
                val text = pages.mapNotNull { runCatching { OcrEngine.recognize(it) }.getOrNull() }
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                working = false
                if (text.isBlank()) error = "No text was found. Try again with the page flat and well lit."
                else stage = HubStage.TextResult(text)
            }
        } else if (pages.size <= 2 && pages.all(IdCardPdf::looksLikeCard)) {
            suggestion = pages
        } else {
            saveScan(pages)
        }
    }

    fun capture() {
        error = null
        if (mode == ScanMode.ID_CARD) {
            stage = HubStage.Camera
            return
        }
        val act = activity ?: run { error = "Scanning isn't available here."; return }
        DocumentScanner.requestIntent(
            context, act, pageLimit = null,
            onReady = { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) },
            onError = { error = "Scanning needs a camera and Google Play services on this device." },
        )
    }

    when (val s = stage) {
        HubStage.Camera -> CardCamera(
            onDone = { images -> stage = HubStage.Layout(images) },
            onClose = { stage = HubStage.Intro },
        )
        is HubStage.Layout -> {
            BackHandler { stage = HubStage.Intro }
            IdLayoutScreen(
                images = s.images,
                onBack = { stage = HubStage.Intro },
                onSaved = { doc -> onOpenDocument(doc.id) },
                onRetake = { stage = HubStage.Camera },
            )
        }
        is HubStage.TextResult -> {
            BackHandler { stage = HubStage.Intro }
            TextResultScreen(s.text, onBack = { stage = HubStage.Intro })
        }
        HubStage.Intro -> HubIntro(
            mode = mode,
            working = working,
            error = error,
            onMode = { mode = it; error = null },
            onCapture = ::capture,
            onClose = onClose,
        )
    }

    suggestion?.let { images ->
        AlertDialog(
            onDismissRequest = { suggestion = null },
            title = { Text("This looks like an ID card") },
            text = { Text("Print it at the card's real size, with the front and back on one page or two.") },
            confirmButton = {
                TextButton(onClick = { suggestion = null; stage = HubStage.Layout(images) }) {
                    Text("Use the ID card layout")
                }
            },
            dismissButton = {
                TextButton(onClick = { suggestion = null; saveScan(images) }) {
                    Text("Keep as a normal scan")
                }
            },
        )
    }
}

@Composable
private fun HubIntro(
    mode: ScanMode,
    working: Boolean,
    error: String?,
    onMode: (ScanMode) -> Unit,
    onCapture: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val modes = ScanMode.entries
    Column(
        Modifier.fillMaxSize().background(CameraUi.bg).safeDrawingPadding(),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.padding(8.dp)) {
            Icon(Icons.Filled.Close, "Close", tint = CameraUi.text)
        }
        ScanModeArt(
            mode,
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .pointerInput(mode) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, dx -> total += dx },
                        onDragEnd = {
                            val i = modes.indexOf(mode)
                            if (total < -120f && i + 1 < modes.size) onMode(modes[i + 1])
                            if (total > 120f && i > 0) onMode(modes[i - 1])
                        },
                    )
                },
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg).padding(top = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                mode.heading, color = CameraUi.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(mode.blurb, color = CameraUi.soft, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        error?.let {
            Text(
                it, color = Color(0xFFFF8A80), fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.xs),
            )
        }
        Text(
            if (working) "Working\u2026" else "Capture",
            color = CameraUi.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.xl)
                .clip(RoundedCornerShape(50))
                .background(CameraUi.button)
                .clickable(enabled = !working, onClick = onCapture)
                .padding(vertical = 15.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Space.lg, bottom = Space.md),
            horizontalArrangement = Arrangement.spacedBy(Space.lg, Alignment.CenterHorizontally),
        ) {
            modes.forEach { m ->
                val on = m == mode
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onMode(m) }
                        .semantics { selected = on }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.size(28.dp, 3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (on) CameraUi.jade else Color.Transparent),
                    )
                    Text(
                        m.title, color = if (on) CameraUi.jade else CameraUi.soft, fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Simple drawn illustration per mode (no photos), in brand colours on dark. */
@Composable
private fun ScanModeArt(mode: ScanMode, modifier: Modifier) {
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(Color(0xFF24332E), CameraUi.bg)))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        when (mode) {
            ScanMode.SCAN -> ArtPage(lines = 7, highlight = false)
            ScanMode.TEXT -> ArtPage(lines = 7, highlight = true)
            ScanMode.BATCH -> Box(contentAlignment = Alignment.Center) {
                ArtPage(0, false, Modifier.offset(22.dp, 10.dp).rotate(8f))
                ArtPage(0, false, Modifier.offset((-18).dp, 6.dp).rotate(-6f))
                ArtPage(7, false)
            }
            ScanMode.ID_CARD -> Box(contentAlignment = Alignment.Center) {
                ArtCard(Color(0xFFE8DCC8), Color(0xFFC8B08A), back = true, Modifier.offset(28.dp, 26.dp).rotate(6f))
                ArtCard(Color(0xFFCFE3DC), Color(0xFF8FBCAE), back = false, Modifier.offset((-20).dp, (-18).dp).rotate(-5f))
            }
        }
    }
}

@Composable
private fun ArtPage(lines: Int, highlight: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .size(150.dp, 200.dp)
            .shadow(12.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFF4F1E9))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(lines) { i ->
            val color = if (highlight && (i == 2 || i == 3)) CameraUi.jade.copy(alpha = 0.7f)
            else Color(0xFF1A1310).copy(alpha = 0.22f)
            Box(Modifier.size(if (i % 3 == 2) 70.dp else 110.dp, 7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

@Composable
private fun ArtCard(top: Color, bottom: Color, back: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(190.dp, (190f / IdCardPdf.RATIO).dp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(top, bottom))),
    ) {
        val ink = Color.Black.copy(alpha = 0.2f)
        if (back) {
            Box(Modifier.offset(16.dp, 80.dp).size(150.dp, 18.dp).background(ink))
        } else {
            Box(Modifier.offset(16.dp, 22.dp).size(44.dp, 58.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.6f)))
            Column(Modifier.offset(72.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(90.dp, 6.dp).background(ink))
                Box(Modifier.size(70.dp, 6.dp).background(ink))
                Box(Modifier.size(70.dp, 6.dp).background(ink))
            }
        }
    }
}

// MARK: - ID layout choice (after capture)

/** Pick how the card prints, looking at a preview of each layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdLayoutScreen(
    images: List<Bitmap>,
    onBack: () -> Unit,
    onSaved: (SavedDocument) -> Unit,
    onRetake: (() -> Unit)? = null,
) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var layout by remember { mutableStateOf(IdPageLayout.from(prefs.getString("idLayout", null))) }
    var paper by remember { mutableStateOf(PaperSize.from(prefs.getString("paperSize", null))) }
    var error by remember { mutableStateOf<String?>(null) }
    val cards = remember(images) { images.map(IdCardPdf::landscape) }

    ToolScaffold("ID Card", onBack) { padding ->
        ToolBody(padding) {
            Text(
                "How should it print?", color = kopi.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                IdPageLayout.entries.forEach { option ->
                    val selected = option == layout
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(if (selected) kopi.accent.copy(alpha = 0.12f) else kopi.surface)
                            .border(if (selected) 2.dp else 1.dp, if (selected) kopi.accent else kopi.line, RoundedCornerShape(Radius.lg))
                            .clickable {
                                layout = option
                                prefs.edit().putString("idLayout", option.key).apply()
                            }
                            .semantics(mergeDescendants = true) { this.selected = selected }
                            .padding(Space.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        IdPagePreview(cards, option, paper, Modifier.fillMaxWidth().height(170.dp))
                        Text(option.title, color = kopi.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(option.subtitle, color = kopi.textSoft, fontSize = 12.sp)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Paper", color = kopi.textSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PaperSize.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = p == paper,
                            onClick = {
                                paper = p
                                prefs.edit().putString("paperSize", p.key).apply()
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, PaperSize.entries.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = kopi.accent.copy(alpha = 0.16f),
                                activeContentColor = kopi.text,
                                activeBorderColor = kopi.line,
                                inactiveContainerColor = kopi.surface,
                                inactiveContentColor = kopi.textSoft,
                                inactiveBorderColor = kopi.line,
                            ),
                        ) { Text(p.title, fontSize = 13.sp) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Icon(Icons.Outlined.Straighten, null, tint = kopi.textSoft, modifier = Modifier.size(18.dp))
                Text("Printed at the card's real size, 85.6 \u00D7 54 mm.", color = kopi.textSoft, fontSize = 13.sp)
            }
            PrimaryButton("Save") {
                try {
                    val pdf = IdCardPdf.make(context, images, layout, paper)
                    val doc = DocumentStore.save(context, pdf, DocumentStore.title("ID Card"))
                    error = null
                    onSaved(doc)
                } catch (e: Exception) {
                    error = e.message
                }
            }
            if (onRetake != null) {
                Text(
                    "Scan again", color = kopi.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).clickable(onClick = onRetake).padding(10.dp),
                )
            }
            error?.let { Text(it, color = Color(0xFFD1332B), fontSize = 13.sp) }
        }
    }
}

/** Miniature of the printed page(s), drawn to the same proportions as the PDF. */
@Composable
private fun IdPagePreview(cards: List<Bitmap>, layout: IdPageLayout, paper: PaperSize, modifier: Modifier) {
    val pages = if (layout == IdPageLayout.ONE_PAGE) listOf(cards) else cards.map { listOf(it) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val gap = 6.dp
        val count = pages.size.coerceAtLeast(1)
        val aspect = paper.widthPt.toFloat() / paper.heightPt
        val maxW: Dp = (maxWidth - gap * (count - 1)) / count
        val pageW: Dp = minOf(maxW, maxHeight * aspect)
        val k = with(density) { pageW.toPx() } / paper.widthPt   // px per point
        fun pt(v: Float): Dp = with(density) { (v * k).toDp() }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            pages.forEach { pageCards ->
                Column(
                    Modifier
                        .width(pageW)
                        .aspectRatio(aspect)
                        .shadow(2.dp, RoundedCornerShape(3.dp))
                        .background(Color.White)
                        .padding(top = pt(IdCardPdf.TOP_MARGIN_PT)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(pt(IdCardPdf.GAP_PT)),
                ) {
                    pageCards.forEach { card ->
                        Image(
                            card.asImageBitmap(), null, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(pt(IdCardPdf.CARD_W_PT), pt(IdCardPdf.CARD_H_PT)),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Extracted text

@Composable
private fun TextResultScreen(text: String, onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    var copied by remember { mutableIntStateOf(0) }
    ToolScaffold("Extracted text", onBack) { padding ->
        ToolBody(padding) {
            SelectionContainer {
                Text(
                    text, color = kopi.text, fontSize = 15.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(kopi.surface)
                        .border(1.dp, kopi.line, RoundedCornerShape(Radius.lg))
                        .padding(Space.md),
                )
            }
            PrimaryButton(if (copied > 0) "Copied" else "Copy", icon = Icons.Outlined.ContentCopy) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Extracted text", text))
                copied++
            }
            Text(
                "Share as a text file", color = kopi.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md))
                    .clickable { Share.share(context, listOf(OcrEngine.writeTextFile(context, text))) }
                    .padding(10.dp),
            )
        }
    }
}
