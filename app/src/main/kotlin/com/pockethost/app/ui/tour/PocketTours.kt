package com.pockethost.app.ui.tour

import androidx.compose.ui.unit.dp

/**
 * The guided tours PocketCraft runs on the home screen and server creation flow.
 *
 * Tailored for the External APK build with direct in-app JAR downloads (no browser
 * download or external storage file picker steps).
 */
object PocketTours {

    /** Step keys screens report back on, so a completed action can move the tour on. */
    const val STEP_WELCOME = "welcome"
    const val STEP_SERVER_TYPE_BUTTON = "server_type_button"
    const val STEP_SHEET_SERVER_TYPES = "sheet_server_types"
    const val STEP_SHEET_VERSION = "sheet_version"
    const val STEP_SHEET_CONFIRM = "sheet_confirm"
    const val STEP_PRESS_START = "press_start"
    const val STEP_EULA_ACCEPT = "eula_accept"
    const val STEP_SERVER_SETTINGS = "server_settings"

    const val STEP_CREATE_NAME = "create_name"
    const val STEP_CREATE_TYPE = "create_type"
    const val STEP_CREATE_GAMEMODE = "create_gamemode"
    const val STEP_CREATE_DIFFICULTY = "create_difficulty"
    const val STEP_CREATE_CROSSPLAY = "create_crossplay"
    const val STEP_CREATE_GAMEPLAY = "create_gameplay"
    const val STEP_CREATE_SUBMIT = "create_submit"

    /**
     * Enhanced server setup walkthrough that guides the user from choosing a server
     * type, selecting version, downloading the jar directly in-app, starting the
     * server, accepting EULA, and exploring server settings while booting.
     */
    fun firstServer(): List<TourStep> = listOf(
        TourStep(
            key = STEP_WELCOME,
            anchor = null,
            title = "Let's Get You Online",
            body = "Your phone is the server! Follow these steps to configure, install, and start your Minecraft world.",
            advance = TourAdvance.Button("Let's Start")
        ),
        TourStep(
            key = STEP_SERVER_TYPE_BUTTON,
            anchor = TourAnchor.SERVER_TYPE_CARD,
            title = "Choose Server & Version",
            body = "Tap this card to pick your Minecraft server software (Paper, Purpur, Fabric, Vanilla) and game version.",
            advance = TourAdvance.TapTarget("Tap to open"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 18.dp
        ),
        TourStep(
            key = STEP_SHEET_SERVER_TYPES,
            anchor = TourAnchor.SHEET_SERVER_TYPES,
            title = "Select Server Type",
            body = "Choose your server software: Paper & Purpur provide high optimization and plugin support; Fabric supports mods; Vanilla provides the pure official experience. Select the one you want!",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 14.dp
        ),
        TourStep(
            key = STEP_SHEET_VERSION,
            anchor = TourAnchor.SHEET_VERSIONS,
            title = "Pick a Version",
            body = "Choose any Minecraft version you and your friends want to join on.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 4.dp,
            spotlightCornerRadius = 14.dp
        ),
        TourStep(
            key = STEP_SHEET_CONFIRM,
            anchor = TourAnchor.SHEET_CONFIRM_BUTTON,
            title = "Download Server File",
            body = "Tap Download to fetch and verify the server JAR directly in the app.",
            advance = TourAdvance.TapTarget("Tap to Download"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 14.dp
        ),
        TourStep(
            key = STEP_PRESS_START,
            anchor = TourAnchor.START_BUTTON,
            title = "Start the Server",
            body = "Your server files are ready! Now press START SERVER to boot up your world.",
            advance = TourAdvance.TapTarget("Press Start"),
            spotlightPadding = 8.dp,
            spotlightCornerRadius = 22.dp
        ),
        TourStep(
            key = STEP_EULA_ACCEPT,
            anchor = TourAnchor.EULA_ACCEPT_BUTTON,
            title = "Mojang EULA Agreement",
            body = "Mojang requires accepting the Minecraft End User License Agreement to run a server. Tap Accept & Continue to proceed.",
            advance = TourAdvance.TapTarget("Accept & Continue"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = STEP_SERVER_SETTINGS,
            anchor = TourAnchor.SETTINGS_SERVER_TAB,
            title = "All Server Settings",
            body = "While your server is starting up, take a look! All settings about the server — max players, difficulty, ports, world properties, and optimization presets — are right here in Settings.",
            advance = TourAdvance.Button("Got It!")
        )
    )

    /** Shown once, the first time a server actually reaches Online. */
    fun serverLive(): List<TourStep> = listOf(
        TourStep(
            key = "join_card",
            anchor = TourAnchor.JOIN_CARD,
            title = "Your server is live",
            body = "Send the internet address to friends and they can join from anywhere. The " +
                "Wi-Fi address is for people on the same network as you.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 8.dp,
            spotlightCornerRadius = 24.dp
        ),
        TourStep(
            key = "players_tab",
            anchor = TourAnchor.NAV_PLAYERS,
            title = "Manage who plays",
            body = "Kick, ban, whitelist or make someone an operator from the Players tab.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 4.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = "mods_tab",
            anchor = TourAnchor.NAV_MODS,
            title = "Add mods and plugins",
            body = "Install plugins or mods from the Mods tab, then restart the server so it " +
                "loads them.",
            advance = TourAdvance.Button("Done"),
            spotlightPadding = 4.dp,
            spotlightCornerRadius = 16.dp
        )
    )

    /**
     * Guided walkthrough for creating and configuring a new Minecraft server world.
     * Streamlined for direct in-app download on the external APK flavor.
     */
    fun createServer(): List<TourStep> = listOf(
        TourStep(
            key = STEP_CREATE_NAME,
            anchor = TourAnchor.CREATE_SERVER_NAME,
            title = "Name Your World",
            body = "Give your new Minecraft server a memorable name. This identifies your world on PocketHost and in your server list.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = STEP_CREATE_TYPE,
            anchor = TourAnchor.CREATE_SERVER_TYPE,
            title = "Choose Server & Version",
            body = "Tap here to pick your server software (Paper, Purpur, Fabric, Vanilla) and select your Minecraft version.",
            advance = TourAdvance.TapTarget("Tap to pick version"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = STEP_SHEET_SERVER_TYPES,
            anchor = TourAnchor.SHEET_SERVER_TYPES,
            title = "Select Server Software",
            body = "Choose your server software: Paper & Purpur provide high optimization and plugin support; Fabric supports mods; Vanilla provides the pure official experience.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 14.dp
        ),
        TourStep(
            key = STEP_SHEET_VERSION,
            anchor = TourAnchor.SHEET_VERSIONS,
            title = "Pick a Version",
            body = "Scroll and tap the Minecraft version you want to install.",
            advance = TourAdvance.TapTarget("Tap a version"),
            spotlightPadding = 4.dp,
            spotlightCornerRadius = 14.dp
        ),
        TourStep(
            key = STEP_SHEET_CONFIRM,
            anchor = TourAnchor.SHEET_CONFIRM_BUTTON,
            title = "Confirm & Download",
            body = "Tap Download to fetch and verify the server JAR directly in the app.",
            advance = TourAdvance.TapTarget("Tap to Download"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 14.dp
        ),
        TourStep(
            key = STEP_CREATE_GAMEMODE,
            anchor = TourAnchor.CREATE_SERVER_GAMEMODE,
            title = "Select World Mode",
            body = "Select your play style: Survival for gathering and crafting, Creative for building with infinite blocks, Adventure, or Spectator.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = STEP_CREATE_DIFFICULTY,
            anchor = TourAnchor.CREATE_SERVER_DIFFICULTY,
            title = "Difficulty & Terrain",
            body = "Pick your world difficulty (Peaceful, Easy, Normal, or Hard) and choose your terrain generation style (Default, Flat, Large Biomes, or Amplified).",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = STEP_CREATE_CROSSPLAY,
            anchor = TourAnchor.CREATE_SERVER_CROSSPLAY,
            title = "Bedrock Crossplay & Rules",
            body = "Enable Bedrock Crossplay (Geyser) so friends on Android, iOS, Xbox, PlayStation, Switch, and PC can join! You can also toggle PvP combat and flight rules.",
            advance = TourAdvance.Button("Next"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        ),
        TourStep(
            key = STEP_CREATE_SUBMIT,
            anchor = TourAnchor.CREATE_SERVER_SUBMIT,
            title = "Start Your Server",
            body = "When you're ready, tap CREATE SERVER! Once created, you will be taken directly to the console where you can press START SERVER to go live!",
            advance = TourAdvance.Button("Got It!"),
            spotlightPadding = 6.dp,
            spotlightCornerRadius = 16.dp
        )
    )
}
