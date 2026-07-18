filepath = "/home/aleemkanyu/.gemini/antigravity/scratch/PocketCraft/app/src/main/kotlin/com/pocketcraft/server/ui/onboarding/OnboardingActivity.kt"

def modify():
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Update onboardingSteps() function
    old_steps = """private fun onboardingSteps(): List<OnboardingStep> {
    return listOf(
        OnboardingStep("WELCOME"),
        OnboardingStep("HOW IT WORKS"),
        OnboardingStep("BRING YOUR WORLD"),
        OnboardingStep("FULL CONTROL"),
        OnboardingStep("CROSS-PLAY READY"),
        OnboardingStep("PICK REGION"),
        OnboardingStep("PERMISSIONS"),
        OnboardingStep("GOOGLE SIGN-IN"),
        OnboardingStep("SETUP")
    )
}"""

    new_steps = """private fun onboardingSteps(s: AppStrings): List<OnboardingStep> {
    return listOf(
        OnboardingStep(s.onboardingStepWelcome),
        OnboardingStep(s.onboardingStepHowItWorks),
        OnboardingStep(s.onboardingStepBringYourWorld),
        OnboardingStep(s.onboardingStepFullControl),
        OnboardingStep(s.onboardingStepCrossPlay),
        OnboardingStep(s.onboardingStepPickRegion),
        OnboardingStep(s.onboardingStepPermissions),
        OnboardingStep(s.onboardingStepGoogleSignIn),
        OnboardingStep(s.onboardingStepSetup)
    )
}"""

    content = content.replace(old_steps, new_steps)

    # 2. Update steps = onboardingSteps() caller inside OnboardingScreen
    content = content.replace("val steps = remember { onboardingSteps() }", "val s = LocalAppStrings.current\n    val steps = remember(s) { onboardingSteps(s) }")

    # 3. Localize Footer navigation buttons in OnboardingScreen
    content = content.replace('text = "Back",', 'text = s.onboardingButtonBack,')
    content = content.replace('text = if (currentStep == steps.lastIndex) "Finish setup" else if (currentStep == 7) "Skip" else "Next",', 
                              'text = if (currentStep == steps.lastIndex) s.onboardingButtonFinish else if (currentStep == 7) s.onboardingButtonSkip else s.onboardingButtonNext,')
    content = content.replace('permissionStepError = "Allow notification permission to continue."', 'permissionStepError = s.onboardingPermissionsError')
    content = content.replace('text = "Scroll down",', 'text = s.onboardingScrollDown,')

    # 4. Localize WelcomeScreen
    old_welcome = """private fun WelcomeScreen(
    privacyAccepted: Boolean,
    onPrivacyChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        // ── Title ─────────────────────────────────────────────────────────────
        Text(
            text = "Your phone is now\\na Minecraft server",
            fontSize = 25.sp.scaledSp(scale),
            lineHeight = 31.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        // ── Subtitle ──────────────────────────────────────────────────────────
        Text(
            text = "Host Java Edition servers for free.\\nNo PC required. Play with anyone.","""

    new_welcome = """private fun WelcomeScreen(
    privacyAccepted: Boolean,
    onPrivacyChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        // ── Title ─────────────────────────────────────────────────────────────
        Text(
            text = s.onboardingWelcomeTitle,
            fontSize = 25.sp.scaledSp(scale),
            lineHeight = 31.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        // ── Subtitle ──────────────────────────────────────────────────────────
        Text(
            text = s.onboardingWelcomeSubtitle,"""

    content = content.replace(old_welcome, new_welcome)
    
    # Feature cards inside WelcomeScreen
    content = content.replace('Triple(Icons.Filled.Star,        "Free to host", accentGold),', 'Triple(Icons.Filled.Star,        s.onboardingFreeToHost, accentGold),')
    content = content.replace('Triple(Icons.Filled.PhoneAndroid, "No PC needed", accentGreen),', 'Triple(Icons.Filled.PhoneAndroid, s.onboardingNoPcNeeded, accentGreen),')
    content = content.replace('Triple(Icons.Filled.Group,        "Invite anyone", accentPurple)', 'Triple(Icons.Filled.Group,        s.onboardingInviteAnyone, accentPurple)')
    content = content.replace('text = "I agree to the terms & policy",', 'text = s.onboardingAgreeTermsPolicy,')
    content = content.replace('text = "Privacy Policy",', 'text = s.onboardingPrivacyPolicy,')
    content = content.replace('text = "Terms of Use",', 'text = s.onboardingTermsOfUse,')

    # 5. Localize HowItWorksScreen
    old_how_it_works = """private fun HowItWorksScreen() {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        Text(
            text = "How it works",
            fontSize = 21.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        FlowDiagram()

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Dns,
            title = "1. Start your server",
            body = "Paper runs locally on your phone and uses the memory you already have."
        )
        DetailCard(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Public,
            title = "2. Share your address",
            body = "PocketCraft gives you a unique relay address that friends can connect to."
        )
        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Group,
            title = "3. Friends join instantly",
            body = "Java Edition players connect straight through the relay with minimal friction."
        )"""

    new_how_it_works = """private fun HowItWorksScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        Text(
            text = s.onboardingHowItWorksTitle,
            fontSize = 21.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        FlowDiagram()

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Dns,
            title = s.onboardingStep1Title,
            body = s.onboardingStep1Body
        )
        DetailCard(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Public,
            title = s.onboardingStep2Title,
            body = s.onboardingStep2Body
        )
        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Group,
            title = s.onboardingStep3Title,
            body = s.onboardingStep3Body
        )"""

    content = content.replace(old_how_it_works, new_how_it_works)

    # 6. Localize ImportScreen
    old_import_screen = """private fun ImportScreen() {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Upload
        )

        Text(
            text = "Moving from Aternos or Minehut?",
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Bring your world, plugins, and config with you. No starting over.",
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        FeatureListCard(
            accent = onboardingAccentGold(),
            entries = listOf(
                "Upload world zip" to "Export from Aternos, drop the archive in PocketCraft, and keep going.",
                "Keep your plugins" to "Reuse the same .jar files. Your plugin stack moves with you.",
                "Config & ops carry over" to "server.properties, whitelist, and operator access remain intact."
            )
        )"""

    new_import_screen = """private fun ImportScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGold(),
            icon = Icons.Filled.Upload
        )

        Text(
            text = s.onboardingImportTitle,
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingImportSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        FeatureListCard(
            accent = onboardingAccentGold(),
            entries = listOf(
                s.onboardingImportWorldTitle to s.onboardingImportWorldBody,
                s.onboardingImportPluginsTitle to s.onboardingImportPluginsBody,
                s.onboardingImportConfigTitle to s.onboardingImportConfigBody
            )
        )"""

    content = content.replace(old_import_screen, new_import_screen)

    # 7. Localize FeaturesScreen
    old_features_screen = """private fun FeaturesScreen() {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Extension
        )

        Text(
            text = "Full control, right in your pocket",
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Not a stripped-down app. This is the real server toolkit.",
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 18.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Surface(
            color = onboardingAccentGreen().copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = "Everything you need in one place",
                modifier = Modifier.padding(horizontal = 12.dp.scaled(scale), vertical = 6.dp.scaled(scale)),
                color = onboardingAccentPurpleDark(),
                fontSize = 10.sp.scaledSp(scale),
                fontWeight = FontWeight.Bold,
                fontFamily = Monocraft
            )
        }

        FeatureGrid(
            items = listOf(
                FeatureItem("Plugins", "Install compatible plugins", Icons.Filled.Extension),
                FeatureItem("Live console", "Run commands and watch logs", Icons.Filled.Dns),
                FeatureItem("Players", "Manage players", Icons.Filled.Group),
                FeatureItem("properties", "Edit settings directly", Icons.Filled.Settings)
            )
        )"""

    new_features_screen = """private fun FeaturesScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Extension
        )

        Text(
            text = s.onboardingFeaturesTitle,
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingFeaturesSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 18.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        Surface(
            color = onboardingAccentGreen().copy(alpha = 0.12f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = s.onboardingFeaturesBanner,
                modifier = Modifier.padding(horizontal = 12.dp.scaled(scale), vertical = 6.dp.scaled(scale)),
                color = onboardingAccentPurpleDark(),
                fontSize = 10.sp.scaledSp(scale),
                fontWeight = FontWeight.Bold,
                fontFamily = Monocraft
            )
        }

        FeatureGrid(
            items = listOf(
                FeatureItem(s.plugins, s.onboardingFeaturesPluginsBody, Icons.Filled.Extension),
                FeatureItem(s.onboardingFeaturesConsoleTitle, s.onboardingFeaturesConsoleBody, Icons.Filled.Dns),
                FeatureItem(s.players, s.onboardingFeaturesPlayersBody, Icons.Filled.Group),
                FeatureItem(s.onboardingFeaturesConfigTitle, s.onboardingFeaturesConfigBody, Icons.Filled.Settings)
            )
        )"""

    content = content.replace(old_features_screen, new_features_screen)

    # 8. Localize CrossPlayScreen
    old_crossplay = """private fun CrossPlayScreen() {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Public
        )

        Text(
            text = "Cross-play is supported",
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "PocketCraft supports Java and Bedrock cross-play when the bridge is enabled in your server setup.",
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 18.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.PhoneAndroid,
            title = "Java + Bedrock",
            body = "Friends on PC and mobile can join the same world together through the relay."
        )

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Extension,
            title = "No extra app for players",
            body = "Share your server address and players can connect from their own edition right away."
        )

        Surface(
            color = onboardingAccentGold().copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "⚠️ Experimental Feature - Play at Your Own Discretion\\n\\nCross-play is still in active development. Bugs and stability issues may occur as this feature is not yet officially supported. Use at your own risk.",
                modifier = Modifier.padding(horizontal = 14.dp.scaled(scale), vertical = 12.dp.scaled(scale)),
                color = onboardingTextPrimary(),
                fontSize = 11.sp.scaledSp(scale),
                lineHeight = 16.sp.scaledSp(scale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 5
            )
        }"""

    new_crossplay = """private fun CrossPlayScreen() {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.Public
        )

        Text(
            text = s.onboardingCrossPlayTitle,
            fontSize = 20.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingCrossPlaySubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 18.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        DetailCard(
            accent = onboardingAccentGreen(),
            icon = Icons.Filled.PhoneAndroid,
            title = s.onboardingCrossPlayJavaBedrock,
            body = s.onboardingCrossPlayJavaBedrockBody
        )

        DetailCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Extension,
            title = s.onboardingCrossPlayNoExtraApp,
            body = s.onboardingCrossPlayNoExtraAppBody
        )

        Surface(
            color = onboardingAccentGold().copy(alpha = 0.12f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = s.onboardingCrossPlayExperimental,
                modifier = Modifier.padding(horizontal = 14.dp.scaled(scale), vertical = 12.dp.scaled(scale)),
                color = onboardingTextPrimary(),
                fontSize = 11.sp.scaledSp(scale),
                lineHeight = 16.sp.scaledSp(scale),
                fontWeight = FontWeight.SemiBold,
                maxLines = 5
            )
        }"""

    content = content.replace(old_crossplay, new_crossplay)

    # 9. Localize RelayRegionOnboardingScreen
    old_relay = """private fun RelayRegionOnboardingScreen(
    selectedHost: String,
    regions: List<RelayRegion>,
    isFindingBestRelay: Boolean,
    recommendation: String?,
    onSelectHost: (String) -> Unit
) {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {
        Text(
            text = "Choose your relay region",
            fontSize = 24.sp.scaledSp(scale),
            fontWeight = FontWeight.ExtraBold,
            color = onboardingTextPrimary(),
            fontFamily = Monocraft
        )
        Text(
            text = "Pick the relay server closest to your players. You can change this later from the dashboard too.",
            fontSize = 13.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            lineHeight = 18.sp.scaledSp(scale)
        )
        if (isFindingBestRelay) {
            Text(
                text = "Finding best server...",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = onboardingAccentPurpleDark()
            )
        } else if (!recommendation.isNullOrBlank()) {
            Text(
                text = "Recommended: $recommendation. You can still change it below.",
                fontSize = 12.sp,"""

    new_relay = """private fun RelayRegionOnboardingScreen(
    selectedHost: String,
    regions: List<RelayRegion>,
    isFindingBestRelay: Boolean,
    recommendation: String?,
    onSelectHost: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp.scaled(scale))
    ) {
        Text(
            text = s.onboardingRegionTitle,
            fontSize = 24.sp.scaledSp(scale),
            fontWeight = FontWeight.ExtraBold,
            color = onboardingTextPrimary(),
            fontFamily = Monocraft
        )
        Text(
            text = s.onboardingRegionSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            lineHeight = 18.sp.scaledSp(scale)
        )
        if (isFindingBestRelay) {
            Text(
                text = s.onboardingRegionFinding,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = onboardingAccentPurpleDark()
            )
        } else if (!recommendation.isNullOrBlank()) {
            Text(
                text = s.onboardingRegionRecommended.format(recommendation),
                fontSize = 12.sp,"""

    content = content.replace(old_relay, new_relay)

    # 10. Localize OnboardingGoogleSignInScreen
    old_google_signin = """private fun OnboardingGoogleSignInScreen(
    signedInAccountEmail: String,
    onSignInClick: () -> Unit
) {
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.AccountCircle
        )

        Text(
            text = "Optional Google sign-in",
            fontSize = 21.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Sign in now to enable private Google Drive backups. You can skip this and finish setup first.",
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = onboardingSurfaceColor(),
            border = BorderStroke(
                width = 1.dp,
                color = if (signedInAccountEmail.isBlank()) onboardingBorderColor() else onboardingAccentGreen().copy(alpha = 0.4f)
            ),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (signedInAccountEmail.isBlank()) {
                                    onboardingAccentPurple().copy(alpha = 0.1f)
                                } else {
                                    onboardingAccentGreen().copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (signedInAccountEmail.isBlank()) Icons.Filled.AccountCircle else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (signedInAccountEmail.isBlank()) onboardingAccentPurple() else onboardingAccentGreen(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (signedInAccountEmail.isBlank()) "Cloud Sync Status" else "Backup Account Connected",
                            fontSize = 13.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            color = onboardingTextPrimary()
                        )
                        Text(
                            text = if (signedInAccountEmail.isBlank()) "Inactive (Local Only)" else "Active & Secured",
                            fontSize = 11.sp.scaledSp(scale),
                            color = if (signedInAccountEmail.isBlank()) onboardingTextSecondary() else onboardingAccentGreen(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = if (signedInAccountEmail.isBlank()) {
                        "Backups are stored inside your private Google Drive app folder. PocketCraft cannot see or access your other Drive files."
                    } else {
                        "Your server worlds, plugins, and settings will automatically backup to: $signedInAccountEmail"
                    },
                    fontSize = 11.sp.scaledSp(scale),
                    lineHeight = 16.sp.scaledSp(scale),
                    color = onboardingTextSecondary()
                )
            }
        }

        DuoButton(
            text = if (signedInAccountEmail.isBlank()) "Sign in with Google" else "Signed in",
            onClick = onSignInClick,
            enabled = signedInAccountEmail.isBlank(),
            iconContent = if (signedInAccountEmail.isBlank()) {
                { GoogleLogoIcon() }
            } else {
                null
            },
            variant = DuoButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 60.dp
        )

        Text(
            text = "You can press Next without signing in.",
            fontSize = 11.sp.scaledSp(scale),
            color = onboardingTextMuted(),
            textAlign = TextAlign.Center
        )"""

    new_google_signin = """private fun OnboardingGoogleSignInScreen(
    signedInAccountEmail: String,
    onSignInClick: () -> Unit
) {
    val s = LocalAppStrings.current
    val scale = onboardingCompactScale()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp.scaled(scale))
    ) {
        HaloIconBox(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.AccountCircle
        )

        Text(
            text = s.onboardingGoogleTitle,
            fontSize = 21.sp.scaledSp(scale),
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingGoogleSubtitle,
            fontSize = 13.sp.scaledSp(scale),
            lineHeight = 19.sp.scaledSp(scale),
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(20.dp),
            color = onboardingSurfaceColor(),
            border = BorderStroke(
                width = 1.dp,
                color = if (signedInAccountEmail.isBlank()) onboardingBorderColor() else onboardingAccentGreen().copy(alpha = 0.4f)
            ),
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (signedInAccountEmail.isBlank()) {
                                    onboardingAccentPurple().copy(alpha = 0.1f)
                                } else {
                                    onboardingAccentGreen().copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (signedInAccountEmail.isBlank()) Icons.Filled.AccountCircle else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (signedInAccountEmail.isBlank()) onboardingAccentPurple() else onboardingAccentGreen(),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (signedInAccountEmail.isBlank()) s.onboardingGoogleStatusTitle else s.onboardingGoogleConnectedTitle,
                            fontSize = 13.sp.scaledSp(scale),
                            fontWeight = FontWeight.Bold,
                            color = onboardingTextPrimary()
                        )
                        Text(
                            text = if (signedInAccountEmail.isBlank()) s.onboardingGoogleStatusInactive else s.onboardingGoogleStatusActive,
                            fontSize = 11.sp.scaledSp(scale),
                            color = if (signedInAccountEmail.isBlank()) onboardingTextSecondary() else onboardingAccentGreen(),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    text = if (signedInAccountEmail.isBlank()) {
                        s.onboardingGoogleStatusDesc
                    } else {
                        s.onboardingGoogleConnectedDesc.format(signedInAccountEmail)
                    },
                    fontSize = 11.sp.scaledSp(scale),
                    lineHeight = 16.sp.scaledSp(scale),
                    color = onboardingTextSecondary()
                )
            }
        }

        DuoButton(
            text = if (signedInAccountEmail.isBlank()) s.onboardingGoogleSignInButton else s.onboardingGoogleSignedInButton,
            onClick = onSignInClick,
            enabled = signedInAccountEmail.isBlank(),
            iconContent = if (signedInAccountEmail.isBlank()) {
                { GoogleLogoIcon() }
            } else {
                null
            },
            variant = DuoButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 60.dp
        )

        Text(
            text = s.onboardingGoogleSkipNotice,
            fontSize = 11.sp.scaledSp(scale),
            color = onboardingTextMuted(),
            textAlign = TextAlign.Center
        )"""

    content = content.replace(old_google_signin, new_google_signin)

    # 11. Localize PermissionsScreen
    old_permissions = """        Surface(
            color = if (notificationsPermissionGranted) {
                onboardingAccentGreen().copy(alpha = 0.14f)
            } else {
                onboardingAccentGold().copy(alpha = 0.14f)
            },
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = if (notificationsPermissionGranted) "Permission complete" else "Required before continuing",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (notificationsPermissionGranted) onboardingAccentGreen() else onboardingAccentPurpleDark(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Monocraft
            )
        }

        Text(
            text = "Allow notifications",
            fontSize = 21.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = "This is used for server status, player count, and important background updates while your server is running.",
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        PermissionCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Notifications,
            title = "Notifications",
            body = "Shows server status and player count while the server is running."
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = notificationsShakeOffset.value.dp)
        ) {
            DuoButton(
                text = if (notificationsPermissionGranted) "NOTIFICATIONS ENABLED" else "ALLOW NOTIFICATIONS",
                onClick = onAllowNotifications,
                enabled = !notificationsPermissionGranted,
                variant = if (notificationsPermissionGranted) DuoButtonVariant.Secondary else DuoButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp
            )
        }

        Surface(
            color = when {
                notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.1f)
                shouldWarnNotifications() -> Color(0xFFFFF1F0)
                else -> onboardingSurfaceSoftColor()
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                when {
                    notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.35f)
                    shouldWarnNotifications() -> Color(0xFFFFB3AE)
                    else -> onboardingBorderColor().copy(alpha = 0.8f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    notificationsPermissionGranted -> "You're all set. Tap Next to keep going."
                    shouldWarnNotifications() -> "Notification access is required on this step. Tap the button above, then allow it in Android."
                    else -> "Tap the button above, then accept the Android permission prompt to unlock Next."
                },"""

    new_permissions = """        Surface(
            color = if (notificationsPermissionGranted) {
                onboardingAccentGreen().copy(alpha = 0.14f)
            } else {
                onboardingAccentGold().copy(alpha = 0.14f)
            },
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = if (notificationsPermissionGranted) s.onboardingPermissionsComplete else s.onboardingPermissionsRequired,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (notificationsPermissionGranted) onboardingAccentGreen() else onboardingAccentPurpleDark(),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = Monocraft
            )
        }

        Text(
            text = s.onboardingPermissionsTitle,
            fontSize = 21.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft,
            textAlign = TextAlign.Center
        )

        Text(
            text = s.onboardingPermissionsSubtitle,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = onboardingTextSecondary(),
            textAlign = TextAlign.Center,
            maxLines = 3
        )

        PermissionCard(
            accent = onboardingAccentPurple(),
            icon = Icons.Filled.Notifications,
            title = s.onboardingPermissionsCardTitle,
            body = s.onboardingPermissionsCardBody
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = notificationsShakeOffset.value.dp)
        ) {
            DuoButton(
                text = if (notificationsPermissionGranted) s.onboardingPermissionsButtonEnabled else s.onboardingPermissionsButtonAllow,
                onClick = onAllowNotifications,
                enabled = !notificationsPermissionGranted,
                variant = if (notificationsPermissionGranted) DuoButtonVariant.Secondary else DuoButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
                minHeight = 52.dp
            )
        }

        Surface(
            color = when {
                notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.1f)
                shouldWarnNotifications() -> Color(0xFFFFF1F0)
                else -> onboardingSurfaceSoftColor()
            },
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                when {
                    notificationsPermissionGranted -> onboardingAccentGreen().copy(alpha = 0.35f)
                    shouldWarnNotifications() -> Color(0xFFFFB3AE)
                    else -> onboardingBorderColor().copy(alpha = 0.8f)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = when {
                    notificationsPermissionGranted -> s.onboardingPermissionsStatusSet
                    shouldWarnNotifications() -> s.onboardingPermissionsStatusWarn
                    else -> s.onboardingPermissionsStatusTap
                },"""

    content = content.replace(old_permissions, new_permissions)
    # Define s in PermissionsScreen body
    content = content.replace("private fun PermissionsScreen(", "private fun PermissionsScreen(\n    s: AppStrings = LocalAppStrings.current,")
    # Call PermissionsScreen with s in OnboardingScreen (specifically matching 6 -> PermissionsScreen)
    content = content.replace("6 -> PermissionsScreen(", "6 -> PermissionsScreen(\n                                    s = s,")

    # 12. Localize OnboardingSetupScreen
    old_setup_screen = """private fun OnboardingSetupScreen(
    serverName: String,
    onServerNameChange: (String) -> Unit,
    worldDescription: String,
    onWorldDescriptionChange: (String) -> Unit,
    selectedServerType: ServerType,
    selectedVersion: String,
    onVersionClick: () -> Unit,
    worldSeed: String,
    onWorldSeedChange: (String) -> Unit,
    showVersionError: Boolean,
    versionShakeTick: Int
) {
    val versionShakeOffset = remember { Animatable(0f) }
    LaunchedEffect(versionShakeTick) {
        if (versionShakeTick == 0) return@LaunchedEffect
        val keyframes = listOf(0f, -7f, 7f, -5f, 5f, -3f, 3f, 0f)
        keyframes.forEach {
            versionShakeOffset.animateTo(it, animationSpec = tween(durationMillis = 32))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Final setup",
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        OutlinedTextField(
            value = serverName,
            onValueChange = onServerNameChange,
            singleLine = true,
            label = { Text("Server name (required)") },"""

    new_setup_screen = """private fun OnboardingSetupScreen(
    serverName: String,
    onServerNameChange: (String) -> Unit,
    worldDescription: String,
    onWorldDescriptionChange: (String) -> Unit,
    selectedServerType: ServerType,
    selectedVersion: String,
    onVersionClick: () -> Unit,
    worldSeed: String,
    onWorldSeedChange: (String) -> Unit,
    showVersionError: Boolean,
    versionShakeTick: Int
) {
    val s = LocalAppStrings.current
    val versionShakeOffset = remember { Animatable(0f) }
    LaunchedEffect(versionShakeTick) {
        if (versionShakeTick == 0) return@LaunchedEffect
        val keyframes = listOf(0f, -7f, 7f, -5f, 5f, -3f, 3f, 0f)
        keyframes.forEach {
            versionShakeOffset.animateTo(it, animationSpec = tween(durationMillis = 32))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = s.onboardingSetupTitle,
            fontSize = 20.sp,
            color = onboardingTextPrimary(),
            fontWeight = FontWeight.ExtraBold,
            fontFamily = Monocraft
        )

        OutlinedTextField(
            value = serverName,
            onValueChange = onServerNameChange,
            singleLine = true,
            label = { Text(s.onboardingSetupServerLabel) },"""

    content = content.replace(old_setup_screen, new_setup_screen)

    # Rest of OnboardingSetupScreen
    content = content.replace('label = { Text("World description (optional)") },', 'label = { Text(s.onboardingSetupWorldDescLabel) },')
    content = content.replace('if (hasSelectedVersion) "${selectedServerType.displayName} $selectedVersion" else "Select server type + version"',
                              'if (hasSelectedVersion) "${selectedServerType.displayName} $selectedVersion" else s.onboardingSetupVersionPlaceholder')
    content = content.replace('"${selectedServerType.displayName} (Custom JAR)"', 's.onboardingSetupCustomJar.format(selectedServerType.displayName)')
    content = content.replace('label = { Text("Game version (required)") },', 'label = { Text(s.onboardingSetupVersionLabel) },')
    content = content.replace('text = "Please select a game version",', 'text = s.onboardingSetupVersionError,')
    content = content.replace('label = { Text("World seed (optional)") },', 'label = { Text(s.onboardingSetupSeedLabel) },')
    content = content.replace('text = "You can restore world backups later in Settings whenever you are ready.",', 'text = s.onboardingSetupBackupNotice,')

    # Errors inside OnboardingScreen for validation
    content = content.replace('setupVersionError = "Game version is required."', 'setupVersionError = s.onboardingSetupErrorVersion')
    content = content.replace('setupServerNameError = "Server name is required."', 'setupServerNameError = s.onboardingSetupErrorServerName')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    print("OnboardingActivity.kt modified successfully!")

if __name__ == '__main__':
    modify()
