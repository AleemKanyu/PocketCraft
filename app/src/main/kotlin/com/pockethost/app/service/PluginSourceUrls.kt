package com.pockethost.app.service

import android.net.Uri
import java.util.Locale

object PluginSourceUrls {
    private val knownPluginPages = mapOf(
        "essentialsx" to "https://essentialsx.net/downloads.html",
        "worldedit" to "https://enginehub.org/worldedit/",
        "luckperms" to "https://luckperms.net/download",
        "vault" to "https://www.spigotmc.org/resources/vault.34315/",
        "chunky" to "https://modrinth.com/plugin/chunky",
        "viaversion" to "https://hangar.papermc.io/ViaVersion/ViaVersion"
    )

    fun getContentPage(item: PluginManager.RemoteCatalogItem, type: PluginManager.ContentType): String {
        knownPluginPages[item.projectId.lowercase(Locale.US)]?.let { return it }
        return when (item.source) {
            "modrinth" -> {
                val typePath = when (type) {
                    PluginManager.ContentType.PLUGINS -> "plugin"
                    PluginManager.ContentType.MODS -> "mod"
                    PluginManager.ContentType.RESOURCE_PACKS -> "resourcepack"
                }
                "https://modrinth.com/$typePath/${Uri.encode(item.slug.ifBlank { item.projectId })}"
            }
            "hangar" -> {
                val owner = item.owner ?: item.author
                if (!owner.isNullOrBlank()) {
                    "https://hangar.papermc.io/${Uri.encode(owner)}/${Uri.encode(item.slug)}"
                } else {
                    "https://hangar.papermc.io/?q=${Uri.encode(item.title)}"
                }
            }
            else -> "https://www.google.com/search?q=${Uri.encode(item.title)}"
        }
    }
}
