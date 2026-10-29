package io.kopitiam.app.ui.screens

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.docs.DocumentStore
import io.kopitiam.app.docs.SavedDocument
import io.kopitiam.app.pdf.Share
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** First-page thumbnail, rendered off the main thread. */
@Composable
fun PdfThumbnail(file: File, modifier: Modifier = Modifier.size(44.dp, 56.dp)) {
    val kopi = LocalKopi.current
    val bmp by produceState<Bitmap?>(null, file, file.lastModified()) {
        value = withContext(Dispatchers.IO) { DocumentStore.renderPage(file, 0, 160) }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White)
            .border(1.dp, kopi.line, RoundedCornerShape(6.dp))
            .clearAndSetSemantics { },
    ) {
        bmp?.let {
            Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** One saved document in a list: thumbnail, name, date and page count. */
@Composable
fun DocumentRow(doc: SavedDocument, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val date = remember(doc.modified) {
        DateUtils.formatDateTime(
            context, doc.modified,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME,
        )
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        PdfThumbnail(doc.file)
        Column(Modifier.weight(1f)) {
            Text(
                doc.name, color = kopi.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$date \u00B7 ${doc.pageCount} page${if (doc.pageCount == 1) "" else "s"}",
                color = kopi.textSoft, fontSize = 13.sp,
            )
        }
        if (trailing != null) trailing()
        else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = kopi.textSoft)
    }
}

/** A saved document: view it, share it, rename or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(documentId: String, onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val docs by DocumentStore.documents.collectAsState()
    var id by remember { mutableStateOf(documentId) }
    val doc = docs.firstOrNull { it.id == id }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var renameFailed by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = kopi.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        doc?.name ?: "Document", color = kopi.text, fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = kopi.text) }
                },
                actions = {
                    if (doc != null) {
                        IconButton(onClick = { Share.share(context, listOf(doc.file)) }) {
                            Icon(Icons.Outlined.Share, "Share", tint = kopi.text)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Outlined.MoreVert, "More", tint = kopi.text)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                    onClick = { menu = false; newName = doc.name; renaming = true },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kopi.bg, titleContentColor = kopi.text),
            )
        },
    ) { padding ->
        if (doc == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This document is no longer on this phone.", color = kopi.textSoft, fontSize = 15.sp)
            }
        } else {
            PageList(doc, Modifier.fillMaxSize().padding(padding))
        }
    }

    if (confirmDelete && doc != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this document?") },
            text = { Text("It will be removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    DocumentStore.delete(context, doc)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (renaming && doc != null) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    val renamed = DocumentStore.rename(context, doc, newName)
                    if (renamed != null) id = renamed.id else renameFailed = true
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (renameFailed) {
        AlertDialog(
            onDismissRequest = { renameFailed = false },
            title = { Text("Couldn't rename this document.") },
            confirmButton = { TextButton(onClick = { renameFailed = false }) { Text("OK") } },
        )
    }
}

/** Every page, rendered to the screen width. */
@Composable
private fun PageList(doc: SavedDocument, modifier: Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { (maxWidth - Space.md * 2).toPx() }.toInt()
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
        ) {
            items(doc.pageCount) { index ->
                val page by produceState<Bitmap?>(null, doc.file, index, widthPx) {
                    value = withContext(Dispatchers.IO) { DocumentStore.renderPage(doc.file, index, widthPx) }
                }
                val bmp = page
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), "Page ${index + 1}",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().background(Color.White),
                    )
                } else {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(Color.White))
                }
            }
        }
    }
}
