# Inventory Feature Code

This code was temporarily removed from `PlayerDetailScreen.kt` to focus on stability.

## `InventoryWebView` Composable
```kotlin
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InventoryWebView(
    inventoryJson: String?,
    modifier: Modifier = Modifier
) {
    var pageReady by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    fun inject(wv: WebView, json: String) {
        val escaped = json
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "")
        wv.evaluateJavascript("renderInventory('${escaped}')", null)
    }

    LaunchedEffect(inventoryJson) {
        val wv = webViewRef.value
        if (inventoryJson != null) {
            if (pageReady && wv != null) inject(wv, inventoryJson)
            else pendingJson = inventoryJson
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        pageReady = true
                        pendingJson?.let { json ->
                            inject(view, json)
                            pendingJson = null
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        android.util.Log.d("INV_JS", consoleMessage?.message() ?: "")
                        return true
                    }
                }
                loadUrl("file:///android_asset/inventory.html")
                webViewRef.value = this
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    )
}
```

## Relevant NBT Polling Code
```kotlin
val invLine = stateHolder.sendRconCommand("data get entity $commandTarget Inventory")
val heldSlotLine = stateHolder.sendRconCommand("data get entity $commandTarget SelectedItemSlot")

val newInventory = NBTParser.parseInventory(invLine)
val heldSlot = NBTParser.parseDataIntValue(heldSlotLine) ?: 0

val json = JSONObject().apply {
    put("heldSlot", heldSlot)
    put("slots", JSONArray().apply {
        newInventory.forEach { item ->
            put(JSONObject().apply {
                put("slot", item.slot)
                put("id", item.id)
                put("count", item.count)
                put("empty", false)
            })
        }
    })
}.toString()

PlayerDataManager.updateInventoryJson(json)
```
