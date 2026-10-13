// Crossplay plugins (Geyser, Floodgate, ViaVersion, ViaBackwards) for the Play build.
//
// Google Play does not let the app download these JARs itself, and together they are too
// large for the base module, so they ride along as an install-time asset pack: Play
// delivers them with the app and they show up in the normal AssetManager.
// Fill src/main/assets/plugins with scripts/fetch_crossplay_plugins.sh before bundling.
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("crossplay_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
