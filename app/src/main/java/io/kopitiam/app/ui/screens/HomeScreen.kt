package io.kopitiam.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.ToolCategory
import io.kopitiam.app.ToolRegistry
import io.kopitiam.app.ads.AdSlot
import io.kopitiam.app.ui.components.ToolCard
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.NyonyaTileBand
import io.kopitiam.app.ui.theme.Space

@Composable
fun HomeScreen(onOpenTool: (String) -> Unit) {
    val kopi = LocalKopi.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(top = Space.lg, bottom = Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            NyonyaTileBand(modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs))
            Text(
                "KOPITIAM", color = kopi.accent, fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp,
            )
            Text(
                "Every PDF tool,\nfree.", color = kopi.text,
                fontSize = 44.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp,
            )
            Text(
                "A real editor and a full toolkit — right on your phone.",
                color = kopi.textSoft, fontSize = 18.sp,
            )
        }

        // Category sections
        ToolCategory.entries.forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(category.title, color = kopi.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                val tools = ToolRegistry.tools(category)
                tools.chunked(2).forEach { rowTools ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        rowTools.forEach { tool ->
                            ToolCard(tool, modifier = Modifier.weight(1f)) { onOpenTool(tool.id) }
                        }
                        if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // One house-ad slot
        AdSlot(onOpenTool = onOpenTool)
        Spacer(Modifier.height(Space.md))
    }
}
