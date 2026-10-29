package io.kopitiam.app

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.kopitiam.app.ui.screens.AppearanceChoice
import io.kopitiam.app.ui.screens.ComingSoonScreen
import io.kopitiam.app.ui.screens.CompressScreen
import io.kopitiam.app.ui.screens.ConvertScreen
import io.kopitiam.app.ui.screens.EditorScreen
import io.kopitiam.app.ui.screens.ExtractScreen
import io.kopitiam.app.ui.screens.FilesScreen
import io.kopitiam.app.ui.screens.HomeScreen
import io.kopitiam.app.ui.screens.MergeScreen
import io.kopitiam.app.ui.screens.OcrScreen
import io.kopitiam.app.ui.screens.RedactScreen
import io.kopitiam.app.ui.screens.ScanBatchScreen
import io.kopitiam.app.ui.screens.ScanDocumentScreen
import io.kopitiam.app.ui.screens.ScanFilterScreen
import io.kopitiam.app.ui.screens.ScanIdCardScreen
import io.kopitiam.app.ui.screens.SettingsScreen
import io.kopitiam.app.ui.screens.SplitScreen
import io.kopitiam.app.ui.screens.ToolsScreen
import io.kopitiam.app.ui.theme.KopitiamTheme
import io.kopitiam.app.ui.theme.LocalKopi

private const val PREFS = "kopitiam"
private const val KEY_APPEARANCE = "appearance"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // AndroidX core-splashscreen: brand-brown splash with the kopi mark,
        // handed off to the app UI once ready. Must run before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Deep-link launch extras for deterministic verification screenshots:
        //   adb ... am start ... --es open <toolId>
        //   adb ... am start ... --es tab files|tools|settings
        val openTool = intent?.getStringExtra("open")
        val openTab = intent?.getStringExtra("tab")
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        setContent {
            var appearance by remember {
                mutableStateOf(AppearanceChoice.from(prefs.getString(KEY_APPEARANCE, null)))
            }
            val dark = when (appearance) {
                AppearanceChoice.LIGHT -> false
                AppearanceChoice.DARK -> true
                AppearanceChoice.SYSTEM -> isSystemInDarkTheme()
            }
            // Keep status/nav bar icons readable when the in-app override
            // differs from the phone's setting.
            DisposableEffect(dark) {
                val transparent = AndroidColor.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent)
                else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            KopitiamTheme(darkTheme = dark) {
                KopitiamApp(
                    startTool = openTool,
                    startTab = openTab,
                    appearance = appearance,
                    onAppearance = {
                        appearance = it
                        prefs.edit().putString(KEY_APPEARANCE, it.key).apply()
                    },
                )
            }
        }
    }
}

/** The five slots in the flat bottom bar. Scan is an action, not a page. */
private enum class ShellTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    FILES("files", "Files", Icons.Outlined.Folder, Icons.Filled.Folder),
    SCAN("scan", "Scan", Icons.Outlined.PhotoCamera, Icons.Filled.PhotoCamera),
    TOOLS("tools", "Tools", Icons.Outlined.GridView, Icons.Filled.GridView),
    SETTINGS("settings", "Settings", Icons.Outlined.Tune, Icons.Outlined.Tune),
}

private val tabRoutes = setOf("home", "files", "tools", "settings")

@Composable
fun KopitiamApp(
    startTool: String? = null,
    startTab: String? = null,
    appearance: AppearanceChoice = AppearanceChoice.SYSTEM,
    onAppearance: (AppearanceChoice) -> Unit = {},
) {
    val nav = rememberNavController()
    val start = when {
        startTool != null && ToolRegistry.tool(startTool) != null -> "tool/$startTool"
        startTab != null && startTab in tabRoutes -> startTab
        else -> "home"
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val openTool: (String) -> Unit = { id -> nav.navigate("tool/$id") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // The bar only shows on the four tab pages; tool screens get the full height.
            if (route in tabRoutes) {
                FlatBottomBar(selectedRoute = route) { tab ->
                    if (tab == ShellTab.SCAN) openTool("document") else nav.switchTab(tab.route)
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable("home") {
                HomeScreen(onOpenTool = openTool, onShowAllTools = { nav.switchTab("tools") })
            }
            composable("files") { FilesScreen(onOpenTool = openTool) }
            composable("tools") { ToolsScreen(onOpenTool = openTool) }
            composable("settings") { SettingsScreen(appearance, onAppearance) }
            composable("tool/{id}") { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
                ToolRoute(id, back)
            }
        }
    }
}

/** Standard bottom-nav switch: one copy of each tab, state saved and restored. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Edge-to-edge bar: five equal icons, a hairline on top, the active one in
 * jade with a small dot under it. Labels are never drawn, only announced.
 */
@Composable
private fun FlatBottomBar(selectedRoute: String?, onSelect: (ShellTab) -> Unit) {
    val kopi = LocalKopi.current
    Column(Modifier.fillMaxWidth().background(kopi.surface).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = kopi.line)
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellTab.entries.forEach { tab ->
                val on = tab.route == selectedRoute
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(tab) }
                        .semantics {
                            contentDescription = tab.label
                            role = Role.Tab
                            selected = on
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        if (on) tab.selectedIcon else tab.icon, null,
                        tint = if (on) kopi.accent else kopi.textSoft,
                        modifier = Modifier.size(26.dp),
                    )
                    Box(
                        Modifier.size(4.dp).clip(CircleShape)
                            .background(if (on) kopi.accent else Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolRoute(id: String, onBack: () -> Unit) {
    when (id) {
        "edit" -> EditorScreen(onBack)
        "merge" -> MergeScreen(onBack)
        "split" -> SplitScreen(onBack)
        "extract" -> ExtractScreen(onBack)
        "compress" -> CompressScreen(onBack)
        "redact" -> RedactScreen(onBack)
        "document" -> ScanDocumentScreen(onBack)
        "id-card" -> ScanIdCardScreen(onBack)
        "batch" -> ScanBatchScreen(onBack)
        "filters" -> ScanFilterScreen(onBack)
        "ocr" -> OcrScreen(onBack)
        "convert" -> ConvertScreen(onBack)
        else -> {
            val tool = ToolRegistry.tool(id)
            if (tool != null) ComingSoonScreen(tool, onBack) else onBack()
        }
    }
}
