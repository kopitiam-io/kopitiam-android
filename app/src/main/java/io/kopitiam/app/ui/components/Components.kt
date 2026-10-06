package io.kopitiam.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.Tool
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import io.kopitiam.app.ui.theme.rememberReduceMotion

/** Press-response scale (fluid interface). Fires [onClick] on tap. */
@Composable
fun Modifier.pressable(scale: Float = 0.97f, onClick: () -> Unit): Modifier {
    val reduce = rememberReduceMotion()
    var pressed by remember { mutableStateOf(false) }
    val s by animateFloatAsState(
        targetValue = if (pressed && !reduce) scale else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "press",
    )
    return this
        .scale(s)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressed = true
                    val released = tryAwaitRelease()
                    pressed = false
                    if (released) onClick()
                },
            )
        }
}

/** 2-col grid tile — mirror iOS ToolCard. */
@Composable
fun ToolCard(tool: Tool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val kopi = LocalKopi.current
    Column(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 150.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(kopi.surface)
            .border(1.dp, kopi.line, RoundedCornerShape(Radius.lg))
            .pressable(onClick = onClick)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(tool.icon, null, tint = kopi.accent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.weight(1f))
            if (tool.comingSoon) {
                Text(
                    "Soon", color = kopi.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(kopi.accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(tool.title, color = kopi.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(
            tool.blurb, color = kopi.textSoft, fontSize = 14.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Filled brand primary button — mirror iOS primaryButton. */
@Composable
fun PrimaryButton(
    label: String, modifier: Modifier = Modifier, icon: ImageVector? = null,
    enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit,
) {
    val kopi = LocalKopi.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (enabled) kopi.brand else kopi.brand.copy(alpha = 0.5f))
            .then(if (enabled && !loading) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(color = kopi.onBrand, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(8.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = kopi.onBrand, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(label, color = kopi.onBrand, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Accent (secondary) button. */
@Composable
fun AccentButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val kopi = LocalKopi.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(kopi.accent)
            .pressable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = kopi.onBrand, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)) }
        Text(label, color = kopi.onBrand, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Standard tool screen scaffold with a back-navigating top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val kopi = LocalKopi.current
    Scaffold(
        containerColor = kopi.bg,
        topBar = {
            TopAppBar(
                title = { Text(title, color = kopi.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = kopi.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kopi.bg, titleContentColor = kopi.text,
                ),
            )
        },
    ) { padding -> content(padding) }
}

/** A scrolling column body used inside ToolScaffold. */
@Composable
fun ToolBody(padding: PaddingValues, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(padding)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        content = content,
    )
}
