import os

filepath = "/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pocketcraft/server/util/AppStrings.kt"

ONBOARDING_PROPERTIES = """    val filesDeleteCancel: String = "CANCEL",

    // Onboarding strings
    val onboardingStepWelcome: String = "WELCOME",
    val onboardingStepHowItWorks: String = "HOW IT WORKS",
    val onboardingStepBringYourWorld: String = "BRING YOUR WORLD",
    val onboardingStepFullControl: String = "FULL CONTROL",
    val onboardingStepCrossPlay: String = "CROSS-PLAY READY",
    val onboardingStepPickRegion: String = "PICK REGION",
    val onboardingStepPermissions: String = "PERMISSIONS",
    val onboardingStepGoogleSignIn: String = "GOOGLE SIGN-IN",
    val onboardingStepSetup: String = "SETUP",
    val onboardingWelcomeTitle: String = "Your phone is now\\na Minecraft server",
    val onboardingWelcomeSubtitle: String = "Host Java Edition servers for free.\\nNo PC required. Play with anyone.",
    val onboardingFreeToHost: String = "Free to host",
    val onboardingNoPcNeeded: String = "No PC needed",
    val onboardingInviteAnyone: String = "Invite anyone",
    val onboardingAgreeTermsPolicy: String = "I agree to the terms & policy",
    val onboardingPrivacyPolicy: String = "Privacy Policy",
    val onboardingTermsOfUse: String = "Terms of Use",
    val onboardingHowItWorksTitle: String = "How it works",
    val onboardingStep1Title: String = "1. Start your server",
    val onboardingStep1Body: String = "Paper runs locally on your phone and uses the memory you already have.",
    val onboardingStep2Title: String = "2. Share your address",
    val onboardingStep2Body: String = "PocketCraft gives you a unique relay address that friends can connect to.",
    val onboardingStep3Title: String = "3. Friends join instantly",
    val onboardingStep3Body: String = "Java Edition players connect straight through the relay with minimal friction.",
    val onboardingImportTitle: String = "Moving from Aternos or Minehut?",
    val onboardingImportSubtitle: String = "Bring your world, plugins, and config with you. No starting over.",
    val onboardingImportWorldTitle: String = "Upload world zip",
    val onboardingImportWorldBody: String = "Export from Aternos, drop the archive in PocketCraft, and keep going.",
    val onboardingImportPluginsTitle: String = "Keep your plugins",
    val onboardingImportPluginsBody: String = "Reuse the same .jar files. Your plugin stack moves with you.",
    val onboardingImportConfigTitle: String = "Config & ops carry over",
    val onboardingImportConfigBody: String = "server.properties, whitelist, and operator access remain intact.",
    val onboardingFeaturesTitle: String = "Full control, right in your pocket",
    val onboardingFeaturesSubtitle: String = "Not a stripped-down app. This is the real server toolkit.",
    val onboardingFeaturesBanner: String = "Everything you need in one place",
    val onboardingFeaturesPluginsBody: String = "Install compatible plugins",
    val onboardingFeaturesConsoleTitle: String = "Live console",
    val onboardingFeaturesConsoleBody: String = "Run commands and watch logs",
    val onboardingFeaturesPlayersBody: String = "Manage players",
    val onboardingFeaturesConfigTitle: String = "properties",
    val onboardingFeaturesConfigBody: String = "Edit settings directly",
    val onboardingCrossPlayTitle: String = "Cross-play is supported",
    val onboardingCrossPlaySubtitle: String = "PocketCraft supports Java and Bedrock cross-play when the bridge is enabled in your server setup.",
    val onboardingCrossPlayJavaBedrock: String = "Java + Bedrock",
    val onboardingCrossPlayJavaBedrockBody: String = "Friends on PC and mobile can join the same world together through the relay.",
    val onboardingCrossPlayNoExtraApp: String = "No extra app for players",
    val onboardingCrossPlayNoExtraAppBody: String = "Share your server address and players can connect from their own edition right away.",
    val onboardingCrossPlayExperimental: String = "⚠️ Experimental Feature - Play at Your Own Discretion\\n\\nCross-play is still in active development. Bugs and stability issues may occur as this feature is not yet officially supported. Use at your own risk.",
    val onboardingRegionTitle: String = "Choose your relay region",
    val onboardingRegionSubtitle: String = "Pick the relay server closest to your players. You can change this later from the dashboard too.",
    val onboardingRegionFinding: String = "Finding best server...",
    val onboardingRegionRecommended: String = "Recommended: %s. You can still change it below.",
    val onboardingPermissionsComplete: String = "Permission complete",
    val onboardingPermissionsRequired: String = "Required before continuing",
    val onboardingPermissionsTitle: String = "Allow notifications",
    val onboardingPermissionsSubtitle: String = "This is used for server status, player count, and important background updates while your server is running.",
    val onboardingPermissionsCardTitle: String = "Notifications",
    val onboardingPermissionsCardBody: String = "Shows server status and player count while the server is running.",
    val onboardingPermissionsButtonEnabled: String = "NOTIFICATIONS ENABLED",
    val onboardingPermissionsButtonAllow: String = "ALLOW NOTIFICATIONS",
    val onboardingPermissionsStatusSet: String = "You\\'re all set. Tap Next to keep going.",
    val onboardingPermissionsStatusWarn: String = "Notification access is required on this step. Tap the button above, then allow it in Android.",
    val onboardingPermissionsStatusTap: String = "Tap the button above, then accept the Android permission prompt to unlock Next.",
    val onboardingPermissionsError: String = "Allow notification permission to continue.",
    val onboardingGoogleTitle: String = "Optional Google sign-in",
    val onboardingGoogleSubtitle: String = "Sign in now to enable private Google Drive backups. You can skip this and finish setup first.",
    val onboardingGoogleStatusTitle: String = "Cloud Sync Status",
    val onboardingGoogleConnectedTitle: String = "Backup Account Connected",
    val onboardingGoogleStatusInactive: String = "Inactive (Local Only)",
    val onboardingGoogleStatusActive: String = "Active & Secured",
    val onboardingGoogleStatusDesc: String = "Backups are stored inside your private Google Drive app folder. PocketCraft cannot see or access your other Drive files.",
    val onboardingGoogleConnectedDesc: String = "Your server worlds, plugins, and settings will automatically backup to: %s",
    val onboardingGoogleSignInButton: String = "Sign in with Google",
    val onboardingGoogleSignedInButton: String = "Signed in",
    val onboardingGoogleSkipNotice: String = "You can press Next without signing in.",
    val onboardingSetupTitle: String = "Final setup",
    val onboardingSetupServerLabel: String = "Server name (required)",
    val onboardingSetupWorldDescLabel: String = "World description (optional)",
    val onboardingSetupVersionPlaceholder: String = "Select server type + version",
    val onboardingSetupCustomJar: String = "%s (Custom JAR)",
    val onboardingSetupVersionLabel: String = "Game version (required)",
    val onboardingSetupVersionError: String = "Please select a game version",
    val onboardingSetupSeedLabel: String = "World seed (optional)",
    val onboardingSetupBackupNotice: String = "You can restore world backups later in Settings whenever you are ready.",
    val onboardingSetupErrorServerName: String = "Server name is required.",
    val onboardingSetupErrorVersion: String = "Game version is required.",
    val onboardingButtonBack: String = "Back",
    val onboardingButtonSkip: String = "Skip",
    val onboardingButtonFinish: String = "Finish setup",
    val onboardingButtonNext: String = "Next",
    val onboardingScrollDown: String = "Scroll down\""""

DYNAMIC_METHODS = """

fun appStringsFor(context: Context, lang: String): AppStrings {
    val targetLang = if (lang == "system" || lang.isEmpty()) {
        java.util.Locale.getDefault().language
    } else {
        lang
    }
    if (targetLang == "en" || targetLang.startsWith("en_") || targetLang.startsWith("en-")) {
        return AppStrings()
    }
    return try {
        val assetPath = "locales/$targetLang.json"
        val jsonStr = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        Gson().fromJson(jsonStr, AppStrings::class.java) ?: AppStrings()
    } catch (e: Exception) {
        if (targetLang.contains("-") || targetLang.contains("_")) {
            val base = targetLang.split('-', '_')[0]
            if (base != "en") {
                try {
                    val assetPath = "locales/$base.json"
                    val jsonStr = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                    return Gson().fromJson(jsonStr, AppStrings::class.java) ?: AppStrings()
                } catch (e2: Exception) {
                    // fall through
                }
            }
        }
        AppStrings()
    }
}

val LocalAppStrings = compositionLocalOf { AppStrings() }
"""

def modify():
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    # 1. Update imports
    import_sig = "import androidx.compose.runtime.compositionLocalOf"
    new_imports = "import android.content.Context\nimport androidx.compose.runtime.compositionLocalOf\nimport com.google.gson.Gson"
    content = content.replace(import_sig, new_imports)
    
    # 2. Add properties to default AppStrings constructor
    # Find filesDeleteCancel in the constructor
    target_constructor_end = 'val filesDeleteCancel: String = "CANCEL"\n)'
    new_constructor_end = ONBOARDING_PROPERTIES + "\n)"
    
    if target_constructor_end not in content:
        print("Error: Could not find target constructor end signature!")
        return
        
    content = content.replace(target_constructor_end, new_constructor_end)
    
    # 3. Replace static languages with dynamic appStringsFor method
    # Everything after the constructor parenthesis of AppStrings class
    # We find the new constructor end, and we keep everything up to it, and replace everything after it.
    idx = content.find(new_constructor_end)
    if idx == -1:
        print("Error: Could not find constructor end after inserting properties!")
        return
        
    class_end_idx = idx + len(new_constructor_end)
    content_first_part = content[:class_end_idx]
    
    # Write the file back
    final_content = content_first_part + DYNAMIC_METHODS
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(final_content)
    print("AppStrings.kt modified successfully!")

if __name__ == '__main__':
    modify()
