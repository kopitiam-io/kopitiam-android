package io.kopitiam.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.Tool
import io.kopitiam.app.ToolCategory
import io.kopitiam.app.ToolRegistry
import io.kopitiam.app.ads.AdSlot
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.NyonyaTileBand
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space

// MARK: - Home tab

private data class QuickItem(val id: String, val label: String)

private val quickItems = listOf(
    QuickItem("document", "Scan"),
    QuickItem("id-card", "ID Cards"),
    QuickItem("ocr", "Extract Text"),
    QuickItem("edit", "Edit PDF"),
    QuickItem("merge", "Merge"),
    QuickItem("compress", "Compress"),
    QuickItem("convert", "Convert"),
)

@Composable
fun HomeScreen(onOpenTool: (String) -> Unit, onShowAllTools: () -> Unit) {
    val kopi = LocalKopi.current
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(kopi.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(top = Space.lg, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            NyonyaTileBand(modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs))
            Text(
                "KOPITIAM", color = kopi.accent, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = kopi.text)) { append("Documents. ") }
                    withStyle(SpanStyle(color = kopi.accent, fontStyle = FontStyle.Italic)) { append("Done here.") }
                },
                fontFamily = FontFamily.Serif,
                fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp,
                modifier = Modifier.semantics { heading() },
            )
        }

        ToolSearchField(query) { query = it }

        if (query.isBlank()) {
            val entries = quickItems.mapNotNull { item ->
                ToolRegistry.tool(item.id)?.let { tool -> toolEntry(tool, item.label, onOpenTool) }
            } + GridEntry(Icons.Outlined.GridView, "All tools", isPdf = true, onClick = onShowAllTools)
            RoundGrid(entries)
            AdSlot(onOpenTool = onOpenTool)
        } else {
            ToolSearchResults(query, onOpenTool)
        }
        Spacer(Modifier.height(Space.md))
    }
}

// MARK: - Tools tab

@Composable
fun ToolsScreen(onOpenTool: (String) -> Unit) {
    val kopi = LocalKopi.current
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(kopi.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(top = Space.lg, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        ScreenTitle("Tools")
        ToolSearchField(query) { query = it }
        if (query.isBlank()) {
            ToolCategory.entries.forEach { category ->
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    Text(
                        category.title, color = kopi.text, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() },
                    )
                    RoundGrid(ToolRegistry.tools(category).map { tool -> toolEntry(tool, tool.title, onOpenTool) })
                }
            }
        } else {
            ToolSearchResults(query, onOpenTool)
        }
    }
}

// MARK: - Files tab

/**
 * Kopitiam keeps no copies of documents yet, so Files is a starting point that
 * opens something from the phone rather than a library.
 */
@Composable
fun FilesScreen(onOpenTool: (String) -> Unit) {
    val kopi = LocalKopi.current
    Column(
        Modifier
            .fillMaxSize()
            .background(kopi.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(top = Space.lg, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        ScreenTitle("Files")
        Column(
            Modifier.fillMaxWidth().padding(top = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(kopi.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Folder, null, tint = kopi.accent, modifier = Modifier.size(40.dp))
            }
            Text(
                "Your files stay on your phone", color = kopi.text, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                "Kopitiam doesn't keep copies of your documents. Open one from your phone, or scan something new.",
                color = kopi.textSoft, fontSize = 14.sp, textAlign = TextAlign.Center,
            )
        }
        FlatCard {
            FlatRow(Icons.Outlined.Description, kopi.brand, "Open a PDF", "Edit, sign or highlight it") { onOpenTool("edit") }
            RowDivider()
            FlatRow(Icons.Outlined.PhotoLibrary, kopi.accent, "Turn photos into a PDF", "Pick images from your gallery") { onOpenTool("convert") }
            RowDivider()
            FlatRow(Icons.Outlined.PhotoCamera, kopi.accent, "Scan a document", "Use the camera") { onOpenTool("document") }
        }
    }
}

// MARK: - Settings tab

/** Stored appearance override. "system" follows the phone. */
enum class AppearanceChoice(val key: String, val title: String) {
    SYSTEM("system", "Match phone"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun from(key: String?): AppearanceChoice = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(appearance: AppearanceChoice, onAppearance: (AppearanceChoice) -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0"
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(kopi.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(top = Space.lg, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        ScreenTitle("Settings")

        SectionLabel("Appearance")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            AppearanceChoice.entries.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = choice == appearance,
                    onClick = { onAppearance(choice) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppearanceChoice.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = kopi.accent.copy(alpha = 0.16f),
                        activeContentColor = kopi.text,
                        activeBorderColor = kopi.line,
                        inactiveContainerColor = kopi.surface,
                        inactiveContentColor = kopi.textSoft,
                        inactiveBorderColor = kopi.line,
                    ),
                ) { Text(choice.title, fontSize = 13.sp) }
            }
        }

        SectionLabel("Privacy")
        FlatCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Icon(Icons.Outlined.Lock, null, tint = kopi.accent)
                Text(
                    "Your documents are processed on this phone and never uploaded.",
                    color = kopi.text, fontSize = 15.sp,
                )
            }
        }

        SectionLabel("About")
        FlatCard {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Version", color = kopi.text, fontSize = 15.sp)
                Text(version, color = kopi.textSoft, fontSize = 15.sp)
            }
            RowDivider(start = Space.md)
            Text(
                "Kopitiam is free, always.", color = kopi.textSoft, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = Space.md, vertical = 14.dp),
            )
        }
    }
}

// MARK: - Shared shell components

@Composable
private fun ScreenTitle(text: String) {
    val kopi = LocalKopi.current
    Text(
        text, color = kopi.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun SectionLabel(text: String) {
    val kopi = LocalKopi.current
    Text(
        text.uppercase(), color = kopi.textSoft, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.semantics { heading() },
    )
}

/** One round button in a grid. PDF tools tint kopi brown, everything else jade. */
private data class GridEntry(
    val icon: ImageVector,
    val label: String,
    val isPdf: Boolean,
    val dimmed: Boolean = false,
    val onClick: () -> Unit,
)

private fun toolEntry(tool: Tool, label: String, onOpenTool: (String) -> Unit) = GridEntry(
    icon = tool.icon,
    label = label,
    isPdf = tool.category == ToolCategory.PDF,
    dimmed = tool.comingSoon,
    onClick = { onOpenTool(tool.id) },
)

/** Four-column grid of round icon buttons. */
@Composable
private fun RoundGrid(entries: List<GridEntry>) {
    val kopi = LocalKopi.current
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                row.forEach { e ->
                    RoundIconButton(
                        e.icon, e.label, if (e.isPdf) kopi.brand else kopi.accent,
                        Modifier.weight(1f).alpha(if (e.dimmed) 0.6f else 1f), e.onClick,
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Round tinted icon with a short label underneath (Home + Tools grids). */
@Composable
fun RoundIconButton(
    icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val kopi = LocalKopi.current
    Column(
        modifier.clip(RoundedCornerShape(Radius.md)).clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Text(
            label, color = kopi.text, fontSize = 12.sp, textAlign = TextAlign.Center,
            maxLines = 2, lineHeight = 15.sp,
        )
    }
}

@Composable
private fun FlatCard(content: @Composable () -> Unit) {
    val kopi = LocalKopi.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(kopi.surface)
            .border(1.dp, kopi.line, RoundedCornerShape(Radius.lg)),
    ) { content() }
}

@Composable
private fun RowDivider(start: androidx.compose.ui.unit.Dp = 64.dp) {
    HorizontalDivider(Modifier.padding(start = start), thickness = 1.dp, color = LocalKopi.current.line)
}

/** Flat list row: round icon, title, subtitle, chevron. */
@Composable
private fun FlatRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    val kopi = LocalKopi.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Space.md, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = kopi.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = kopi.textSoft, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = kopi.textSoft)
    }
}

@Composable
private fun ToolSearchField(value: String, onChange: (String) -> Unit) {
    val kopi = LocalKopi.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(kopi.surface)
            .border(1.dp, kopi.line, RoundedCornerShape(Radius.md))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Icon(Icons.Outlined.Search, null, tint = kopi.textSoft, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Search tools, like \u201CMerge\u201D", color = kopi.textSoft, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = kopi.text, fontSize = 15.sp),
                cursorBrush = SolidColor(kopi.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel, "Clear search", tint = kopi.textSoft,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun ToolSearchResults(query: String, onOpenTool: (String) -> Unit) {
    val kopi = LocalKopi.current
    val q = query.trim().lowercase()
    val matches = ToolRegistry.all.filter {
        it.title.lowercase().contains(q) || it.blurb.lowercase().contains(q)
    }
    if (matches.isEmpty()) {
        Text(
            "No tools match \u201C$query\u201D.", color = kopi.textSoft, fontSize = 14.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = Space.lg),
        )
    } else {
        FlatCard {
            matches.forEachIndexed { index, tool ->
                if (index > 0) RowDivider()
                val tint = if (tool.category == ToolCategory.PDF) kopi.brand else kopi.accent
                FlatRow(tool.icon, tint, tool.title, tool.blurb) { onOpenTool(tool.id) }
            }
        }
    }
}
