package com.pockethost.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pockethost.app.data.model.PlayerInfo
import com.pockethost.app.map.AnvilTerrainExtractor
import com.pockethost.app.map.ChunkEligibilityChecker
import com.pockethost.app.map.DetectedDimension
import com.pockethost.app.map.MapCacheManager
import com.pockethost.app.map.MapManager
import com.pockethost.app.map.MapState
import com.pockethost.app.service.ServerFileManager
import java.io.File
import com.pockethost.app.ui.components.DuoButton
import com.pockethost.app.ui.components.DuoButtonVariant
import com.pockethost.app.ui.components.GameCard
import com.pockethost.app.ui.theme.Monocraft
import com.pockethost.app.ui.theme.PocketColors
import com.pockethost.app.BuildConfig
import com.pockethost.app.ui.theme.bottomShadow
import com.pockethost.app.ui.theme.pocketIsDarkTheme
import kotlinx.coroutines.launch

/**
 * JavaScript interface bridge between Android WebView (WebGL) and Jetpack Compose.
 */
class PocketMapJsBridge(
    private val coordinatesCallback: (Double, Double, Double) -> Unit,
    private val terrainProvider: (String) -> String,
    private val playersProvider: () -> String,
    private val readyCallback: () -> Unit = {}
) {
    @JavascriptInterface
    fun updateCoordinates(x: Double, y: Double, z: Double) {
        coordinatesCallback(x, y, z)
    }

    @JavascriptInterface
    fun onCoordinates(x: Double, y: Double, z: Double) {
        coordinatesCallback(x, y, z)
    }

    @JavascriptInterface
    fun getTerrainData(dimensionId: String): String {
        Log.d("PocketMapJsBridge", "getTerrainData called from JS for dimension: $dimensionId")
        val data = terrainProvider(dimensionId)
        Log.d("PocketMapJsBridge", "getTerrainData returned data string with ${data.length} characters")
        return data
    }

    @JavascriptInterface
    fun getOnlinePlayers(): String {
        return playersProvider()
    }

    @JavascriptInterface
    fun onMapReady() {
        Log.d("PocketMapJsBridge", "onMapReady called from JS")
        readyCallback()
    }
}

/** Loopback retries before giving up on the tile server. */
private const val MAX_MAP_LOAD_ATTEMPTS = 4

/** JS snippet that switches the viewer between isometric 3D and top-down. */
private fun viewModeJs(is3d: Boolean): String =
    if (is3d) "if (window.zoomBy) window.zoomBy(1.6);" else "if (window.zoomBy) window.zoomBy(0.625);"

/** Quotes a value for safe interpolation into an evaluateJavascript string literal. */
private fun jsQuote(value: String): String = org.json.JSONObject.quote(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldMapScreen(
    stateHolder: ServerStateHolder,
    onNavigateBack: () -> Unit,
    onMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = pocketIsDarkTheme()

    val worldName = stateHolder.activeWorld
    val serverDir = remember(worldName) { ServerFileManager.getServerDir(context, worldName) }

    // Map Manager instance
    val mapManager = remember(worldName) {
        MapManager(context) { cmd -> stateHolder.sendCommand(cmd) }
    }

    // Initialize MapManager
    LaunchedEffect(worldName) {
        mapManager.initialize(
            scope = scope,
            worldName = worldName,
            serverDir = serverDir,
            isServerActive = { stateHolder.status == ServerStatus.ONLINE },
            currentTps = { stateHolder.tps.toDouble() },
            getOnlinePlayers = {
                stateHolder.onlinePlayers.map { player ->
                    mapOf(
                        "name" to player.name,
                        "uuid" to player.uuid,
                        "x" to (player.x?.toDouble() ?: 0.0),
                        "y" to (player.y?.toDouble() ?: 64.0),
                        "z" to (player.z?.toDouble() ?: 0.0),
                        "dimension" to player.worldName.ifBlank { "minecraft:overworld" }
                    )
                }
            }
        )
    }

    DisposableEffect(worldName) {
        onDispose {
            mapManager.shutdown()
        }
    }

    // State bindings
    val availableDimensions by mapManager.availableDimensions.collectAsState()
    val activeDimension by mapManager.activeDimension.collectAsState()
    val is3dMode by mapManager.is3dMode.collectAsState()
    val renderStatus by mapManager.renderStatus.collectAsState()
    val renderRadius by mapManager.renderRadius.collectAsState()
    val cacheSizeBytes by mapManager.cacheSizeBytes.collectAsState()
    val localServerReady by mapManager.localServerReady.collectAsState()

    var cameraX by remember { mutableDoubleStateOf(0.0) }
    var cameraY by remember { mutableDoubleStateOf(64.0) }
    var cameraZ by remember { mutableDoubleStateOf(0.0) }

    var showPlayersSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var lastAppliedViewMode by remember { mutableStateOf<Boolean?>(null) }
    var mapLoadAttempts by remember { mutableIntStateOf(0) }
    val isServerOnline = stateHolder.status == ServerStatus.ONLINE

    val activeDim = activeDimension
    val bridge = remember(worldName, availableDimensions, activeDimension) {
        PocketMapJsBridge(
            coordinatesCallback = { x, y, z ->
                cameraX = x
                cameraY = y
                cameraZ = z
                mapManager.updateCoordinates(x, y, z)
            },
            terrainProvider = { dimId ->
                val targetDim = availableDimensions.firstOrNull { it.id == dimId } ?: activeDimension
                val regionDir = targetDim?.regionDir ?: File(serverDir, "world/dimensions/minecraft/overworld/region")
                AnvilTerrainExtractor.extractTerrain(regionDir, dimId, 64).toJsonString()
            },
            playersProvider = {
                val jsonArr = org.json.JSONArray()
                stateHolder.onlinePlayers.forEach { p ->
                    val obj = org.json.JSONObject()
                    obj.put("name", p.name)
                    obj.put("x", p.x?.toDouble() ?: 0.0)
                    obj.put("y", p.y?.toDouble() ?: 64.0)
                    obj.put("z", p.z?.toDouble() ?: 0.0)
                    jsonArr.put(obj)
                }
                jsonArr.toString()
            }
        )
    }

    // Point the WebView at the viewer once the loopback tile server is accepting.
    LaunchedEffect(isServerOnline, localServerReady, webViewInstance) {
        val wv = webViewInstance ?: return@LaunchedEffect
        val desired = mapManager.resolveMapUrl(isServerOnline) ?: return@LaunchedEffect
        if (wv.url != desired) {
            lastAppliedViewMode = null
            mapLoadAttempts = 0
            wv.loadUrl(desired)
        }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1217))
    ) {
        // --- 1. Center WebGL Map Viewport ---
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                if (BuildConfig.DEBUG) {
                    WebView.setWebContentsDebuggingEnabled(true)
                }
                WebView(ctx).apply {
                    webViewInstance = this
                    @SuppressLint("SetJavaScriptEnabled")
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT

                    setBackgroundColor(android.graphics.Color.parseColor("#0D1217"))

                    addJavascriptInterface(bridge, "PocketHostBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("WorldMapWeb", "${consoleMessage?.message()} [${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}]")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            lastAppliedViewMode = is3dMode
                            view?.evaluateJavascript(viewModeJs(is3dMode), null)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            if (failingUrl?.startsWith("http://127.0.0.1") == true) {
                                // Retry briefly: the loopback server may still be binding.
                                // Only fall back once it is clearly not coming up, otherwise
                                // a one-off refusal would strand us on the bundled viewer.
                                if (mapLoadAttempts < MAX_MAP_LOAD_ATTEMPTS) {
                                    mapLoadAttempts++
                                    Log.w("WorldMapScreen", "Map server not ready ($description); retry $mapLoadAttempts")
                                    view?.postDelayed({ view.loadUrl(failingUrl) }, 1500L)
                                } else {
                                    Log.w("WorldMapScreen", "Tile server unreachable: $description")
                                }
                            }
                        }
                    }

                    // Canvas tile viewer; tiles stream in from the loopback server.
                    mapManager.resolveMapUrl(isServerOnline)?.let { loadUrl(it) }
                }
            },
            update = { wv ->
                // Only push the view mode when it actually changed. Firing on every
                // recomposition would snap the camera back while the user is panning,
                // because camera coordinates recompose this screen continuously.
                if (lastAppliedViewMode != is3dMode) {
                    lastAppliedViewMode = is3dMode
                    wv.evaluateJavascript(viewModeJs(is3dMode), null)
                }
            }
        )

        // --- 2. Top Header & Dimension Deck ---
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topInset)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Main App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE161F28))
                    .bottomShadow(shadowHeight = 3.dp, cornerRadius = 16.dp)
                    .border(1.dp, Color(0xFF233140), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "World Map",
                        fontFamily = Monocraft,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Map Status Badge
                MapStatusIndicator(status = renderStatus, isServerOnline = isServerOnline)
            }

            // Dimension Pills Row & 3D/Top-Down Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Dimensions Selector
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (availableDimensions.isEmpty()) {
                        DimensionPill(
                            title = "Overworld",
                            icon = "🌍",
                            isSelected = true,
                            onClick = {}
                        )
                    } else {
                        availableDimensions.forEach { dim ->
                            DimensionPill(
                                title = dim.displayName,
                                icon = dim.type.iconEmoji,
                                isSelected = activeDimension?.id == dim.id,
                                onClick = {
                                    mapManager.switchDimension(dim)
                                    webViewInstance?.evaluateJavascript(
                                        "if (window.setDimension) window.setDimension(${jsQuote(dim.id)});",
                                        null
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 3D Isometric vs Top-Down Toggle Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD161F28))
                        .border(1.dp, Color(0xFF233140), RoundedCornerShape(12.dp))
                        .padding(2.dp)
                ) {
                    ViewModeOption(
                        title = "3D",
                        isActive = is3dMode,
                        onClick = {
                            mapManager.set3dMode(true)
                            webViewInstance?.evaluateJavascript(viewModeJs(true), null)
                        }
                    )
                    ViewModeOption(
                        title = "TOP",
                        isActive = !is3dMode,
                        onClick = {
                            mapManager.set3dMode(false)
                            webViewInstance?.evaluateJavascript(viewModeJs(false), null)
                        }
                    )
                }
            }

            // Offline Notice Banner if server is stopped
            if (!isServerOnline) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF94A3B8))
                        )
                        Text(
                            text = "Server Offline — Showing cached map",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    DuoButton(
                        text = "START",
                        onClick = { stateHolder.startServer() },
                        variant = DuoButtonVariant.StartServer,
                        minHeight = 28.dp,
                        fillMaxWidth = false
                    )
                }
            }
        }

        // --- 3. Bottom HUD & Control Deck ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = bottomInset)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Coordinate HUD
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xEE161F28))
                    .border(1.dp, Color(0xFF233140), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CoordinateItem(label = "X", value = cameraX.toInt())
                CoordinateItem(label = "Y", value = cameraY.toInt())
                CoordinateItem(label = "Z", value = cameraZ.toInt())
            }

            // Action Buttons Deck
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Players Sheet
                DuoButton(
                    text = "PLAYERS (${stateHolder.onlinePlayers.size})",
                    onClick = { showPlayersSheet = true },
                    variant = DuoButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp
                )

                // Locate Me / Center Camera
                DuoButton(
                    text = "LOCATE",
                    onClick = {
                        val firstPlayer = stateHolder.onlinePlayers.firstOrNull()
                        if (firstPlayer != null) {
                            webViewInstance?.evaluateJavascript("if (window.centerOn) window.centerOn(${firstPlayer.x?.toInt() ?: 0}, ${firstPlayer.z?.toInt() ?: 0});", null)
                            onMessage("Focused on ${firstPlayer.name}")
                        } else {
                            webViewInstance?.evaluateJavascript("if (window.resetView) window.resetView();", null)
                            onMessage("Reset camera to world center")
                        }
                    },
                    variant = DuoButtonVariant.Primary,
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp
                )

                // Settings
                IconButton(
                    onClick = { showSettingsSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xEE161F28))
                        .border(1.dp, Color(0xFF233140), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Map Settings",
                        tint = Color.White
                    )
                }
            }
        }

        // --- 4. Online Players Bottom Sheet ---
        if (showPlayersSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPlayersSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF161F28)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = bottomInset),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Online Players",
                        fontFamily = Monocraft,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    if (stateHolder.onlinePlayers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No players currently online.",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(stateHolder.onlinePlayers) { player ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF4ADE80))
                                        )
                                        Text(
                                            text = player.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }

                                    DuoButton(
                                        text = "LOCATE",
                                        onClick = {
                                            webViewInstance?.evaluateJavascript(
                                                "if (window.centerOn) window.centerOn(${player.x?.toInt() ?: 0}, ${player.z?.toInt() ?: 0});",
                                                null
                                            )
                                            showPlayersSheet = false
                                            onMessage("Camera centered on ${player.name}")
                                        },
                                        variant = DuoButtonVariant.Primary,
                                        minHeight = 28.dp,
                                        fillMaxWidth = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Map Settings Bottom Sheet ---
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF161F28)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .padding(bottom = bottomInset),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Map Settings",
                        fontFamily = Monocraft,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Render Radius Slider
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Render Safety Boundary",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "$renderRadius blocks",
                                fontSize = 12.sp,
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                fontFamily = Monocraft
                            )
                        }
                        Slider(
                            value = renderRadius.toFloat(),
                            onValueChange = { mapManager.updateRenderRadius(scope, it.toInt()) },
                            valueRange = 1000f..6000f,
                            steps = 4,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4ADE80),
                                activeTrackColor = Color(0xFF4ADE80)
                            )
                        )
                        Text(
                            text = "Unexplored chunks beyond this boundary will never be rendered.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Cache Storage Section
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Map Storage Cache",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = MapCacheManager.formatSizeBytes(cacheSizeBytes),
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        DuoButton(
                            text = "PURGE",
                            onClick = { showPurgeConfirmDialog = true },
                            variant = DuoButtonVariant.Danger,
                            minHeight = 28.dp,
                            fillMaxWidth = false
                        )
                    }
                }
            }
        }

        // Purge confirmation dialog
        if (showPurgeConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showPurgeConfirmDialog = false },
                containerColor = Color(0xFF161F28),
                title = {
                    Text(
                        text = "Purge Map Cache?",
                        fontFamily = Monocraft,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "This will delete all rendered 3D map tiles to free up storage space. Your Minecraft world saves and chunks will NOT be touched.\n\nThe map will re-render progressively as you explore.",
                        fontSize = 13.sp,
                        color = Color(0xFFCBD5E1)
                    )
                },
                confirmButton = {
                    DuoButton(
                        text = "PURGE TILES",
                        onClick = {
                            mapManager.purgeCache(scope)
                            showPurgeConfirmDialog = false
                            onMessage("Map cache purged.")
                        },
                        variant = DuoButtonVariant.Danger,
                        minHeight = 36.dp,
                        fillMaxWidth = false
                    )
                },
                dismissButton = {
                    DuoButton(
                        text = "CANCEL",
                        onClick = { showPurgeConfirmDialog = false },
                        variant = DuoButtonVariant.Secondary,
                        minHeight = 36.dp,
                        fillMaxWidth = false
                    )
                }
            )
        }
    }
}

@Composable
private fun DimensionPill(
    title: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF22C55E) else Color(0xDD161F28))
            .border(1.dp, if (isSelected) Color(0xFF16A34A) else Color(0xFF233140), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = icon, fontSize = 12.sp)
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}

@Composable
private fun ViewModeOption(
    title: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) Color(0xFF22C55E) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = Monocraft,
            color = if (isActive) Color.Black else Color(0xFF94A3B8)
        )
    }
}

@Composable
private fun MapStatusIndicator(
    status: com.pockethost.app.map.MapRenderStatus,
    isServerOnline: Boolean
) {
    val (dotColor, label) = when {
        !isServerOnline -> Pair(Color(0xFF94A3B8), "Offline")
        status.isServerBusy -> Pair(Color(0xFFF59E0B), "Paused")
        status.state == MapState.RENDERING -> Pair(Color(0xFF38BDF8), "Rendering")
        else -> Pair(Color(0xFF4ADE80), "Ready")
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun CoordinateItem(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFF94A3B8),
            fontFamily = Monocraft,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value.toString(),
            fontSize = 11.sp,
            color = Color.White,
            fontFamily = Monocraft,
            fontWeight = FontWeight.Bold
        )
    }
}
