# PocketCraft Onboarding Design Plan

## Overview
Professional, delightful onboarding experience that mirrors Duolingo's approach while maintaining PocketCraft's unique Minecraft-inspired aesthetic and light theme.

## Design Philosophy
- **Progressive Disclosure**: Introduce features gradually, not overwhelming new users
- **Visual Hierarchy**: Use PocketCraft's custom icons and Minecraft theme consistently
- **Engagement**: Celebrate progress with visual feedback
- **Clear Value**: Show benefits immediately rather than asking permissions upfront
- **Accessibility**: Readable text, proper contrast ratios, mobile-first design

## Design System

### Color Palette
- **Primary**: `#6C63FF` (PocketCraft Purple)
- **Primary Dark**: `#4A45A0` (Darker purple for emphasis)
- **Primary Muted**: `#F0ECFF` (Light purple background)
- **Accent Green**: `#3DDC84` (Success/Bedrock green)
- **Accent Gold**: `#FFB142` (Import/migration warm)
- **Offline Red**: `#FF4757` (Error/offline state)
- **Background**: `#F5F7F3` (Light cream)
- **Surface**: `#16161F` (Dark surface for cards)
- **Text Primary**: `#0A0A0F` (Near black)
- **Text Secondary**: `#666666` (Medium gray)
- **Border**: `#2A2A3A` (Subtle borders)

### Typography
- **Headlines**: Monocraft font (Minecraft-style) for primary headings
- **Body**: System font (clarity and readability)
- **Sizes**:
  - H1: 22-24px, Bold, Minecraft theme for page titles
  - H2: 18-20px, Bold, section headers
  - Body: 14-16px, Regular, main content
  - Caption: 12-13px, Regular, helper text

### Spacing & Layout
- Base unit: 8px
- Grid: 4-column mobile, 12-column desktop
- Padding: 16px (standard), 20px (generous), 12px (compact)
- Gaps: 12px (elements), 16px (sections), 24px (screens)
- Border Radius: 10-22px, rounded corners throughout
- Phone Mockup: 280px width × 560px height with notch

### Icons
**Custom PocketCraft Icons (Pixel Art Style, 44-64px)**:
- `ic_world_pixel` - World/dimension icon
- `ic_mods_pixel` - Mods/plugins icon
- Custom SVG icons for features:
  - Server/host icon
  - Internet/relay icon
  - Players/multiplayer icon
  - Settings/config icon
  - Backup/storage icon
  - Plugin installation icon

## Onboarding Flow

### Screen 1: Welcome / Hero
**Purpose**: Introduce PocketCraft's core value proposition
- **Visual**: Large animated pickaxe icon with glow effect
- **Headline**: "Your phone is now a Minecraft server"
- **Subheading**: "Host Paper servers for free. No PC required. Invite friends from anywhere."
- **Badge**: "Beta" or "Free" tag
- **CTA**: "Get Started" (Primary button)
- **Alternative**: "Skip" (text button)
- **Progress**: Dot indicator (screen 1/6)

### Screen 2: How It Works
**Purpose**: Explain the core mechanics and value
- **Visual**: 3-step flow diagram with icons
  - 📱 Your Phone → 🌐 Relay → 🎮 Friends Join
- **Headline**: "How it works"
- **Three Benefits** (cards):
  1. **Start your server** - Paper runs locally on your phone using its RAM
  2. **Share your address** - You get a unique address like mine.pocketcraft.online:25XXX
  3. **Friends join instantly** - Works with Java Edition. Cracked clients (TLauncher) supported
- **Button**: "Next →"

### Screen 3: Import from Aternos/Minehut
**Purpose**: Lower migration barrier, show world continuity
- **Visual**: Aternos/Minehut logo with import arrow
- **Headline**: "Moving from Aternos or Minehut?"
- **Subheading**: "Bring your world, plugins, and config with you. No starting over."
- **Three Features** (highlighted cards):
  1. **Upload world zip** - Export from Aternos, upload here. Same format, works instantly.
  2. **Keep your plugins** - Install the same .jar files you had. Plugin manager built right in.
  3. **Config & ops carry over** - server.properties, ops list, whitelist — all preserved.
- **Button**: "Next →"

### Screen 4: Full Control / Features Showcase
**Purpose**: Highlight power and flexibility
- **Visual**: 6-grid of feature icons with labels
  - Plugins (install any Paper-compatible plugin)
  - Live Console (run commands, watch logs)
  - Players (see inventory, stats, teleport, kick)
  - server.properties (edit any setting)
  - World Import (drag in any world zip)
  - Drive Backup (auto-backup to Google Drive)
- **Headline**: "Full control, right in your pocket"
- **Subheading**: "Not a stripped-down app. The real deal."
- **Button**: "Next →"

### Screen 5: Permissions / Battery & Notifications
**Purpose**: Set realistic expectations, get consent for critical features
- **Visual**: Two feature cards with icons
- **Headline**: "Two quick things"
- **Subheading**: "These keep your server running without interruption"
- **Card 1**: Battery Optimization
  - Icon: Shield with lightning bolt
  - Text: "Prevents Android from killing your server mid-game. We'll take you to settings."
- **Card 2**: Notifications
  - Icon: Bell with dot
  - Text: "Shows server status and player count while your server is running."
- **Warning Box**: "Without these, Android may stop your server after a few minutes of inactivity."
- **Button**: "Grant & continue →"

### Screen 6: Ready / Call to Action
**Purpose**: Celebrate setup, final motivation
- **Visual**: Animated checkmark with server icon
- **Headline**: "Ready to begin your journey"
- **Subheading**: "Your server awaits. Set it up, invite your friends, and start building."
- **Stats Row** (3 columns):
  - 🆓 Free / to host
  - ⏰ 24/7 / while app is open
  - ♾️ ∞ / players* (*with limits)
- **Button**: "Let's go →" (gradient: purple to green)
- **Fallback**: "← Back"

## Implementation Details

### Technical Architecture
```
/app/src/main/kotlin/com/pocketcraft/server/ui/screens/
  ├── OnboardingScreen.kt (main container & state)
  ├── OnboardingWelcome.kt
  ├── OnboardingHowItWorks.kt
  ├── OnboardingImport.kt
  ├── OnboardingFeatures.kt
  ├── OnboardingPermissions.kt
  └── OnboardingReady.kt

/app/src/main/kotlin/com/pocketcraft/server/ui/components/
  ├── OnboardingPhoneMockup.kt
  ├── OnboardingFeatureCard.kt
  ├── OnboardingProgressDots.kt
  ├── OnboardingFlowDiagram.kt
  └── OnboardingIcon.kt

/app/src/main/res/drawable/
  ├── ic_onboarding_pickaxe.xml (custom animated pickaxe)
  ├── ic_onboarding_world.xml
  ├── ic_onboarding_relay.xml
  └── [other feature icons...]
```

### State Management
- ViewModel: `OnboardingViewModel` tracks current screen, completion status
- DataStore: Persists `onboardingCompleted` flag, `importedWorldPath` (if applicable)
- Navigation: Conditional routing in `MainActivity` checks `onboardingCompleted`

### Animations & Transitions
- **Screen Transitions**: Fade or slide (consistent direction)
- **Icon Animations**: 
  - Pickaxe: Subtle swing on welcome screen
  - Checkmark: Pop-in with scale animation on final screen
  - Progress dots: Smooth color/width transitions
- **Button Interactions**: Ripple effect, scale-down on press
- **Progress Tracking**: Smooth linear fill of progress bar

### Composition Strategy
- **Phone Mockup**: Reusable `OnboardingPhoneMockup` composable with screen content parameter
- **Cards**: Consistent padding, shadows, border radii across all cards
- **Buttons**: Primary (purple), Secondary (outline), Text buttons
- **Spacing**: Consistent 16px padding, 12px gaps
- **LocalState**: Use `mutableStateOf` for screen transitions, collected in ViewModel

## Flow After Onboarding

After completing onboarding:
1. User is routed to `VersionPickerScreen`
2. Setup begins automatically with `SetupWorker`
3. JRE extraction → Paper download → Bedrock bridge install
4. User sees the splash screen and loading progress
5. App transitions to main `ServerScreen` UI

## Integration Points

### With Existing Screens
- **MainActivity**: Check `onboardingCompleted` in `onCreate()`
- **PocketCraftAppScreen**: Route `LOADING` state includes onboarding check
- **ServerStateHolder**: No changes needed
- **SetupWorker**: Runs after onboarding completes

### Deeplinks / Re-entry
- Onboarding can be manually triggered from Settings → "Reset Onboarding"
- Analytics track completion rate and screen-by-screen drop-off

## Design Tokens (Figma/Code Parity)

```kotlin
object OnboardingTheme {
    val colorPrimary = Color(0xFF6C63FF)
    val colorPrimaryDark = Color(0xFF4A45A0)
    val colorPrimaryMuted = Color(0xFFF0ECFF)
    val colorAccentGreen = Color(0xFF3DDC84)
    val colorAccentGold = Color(0xFFFFB142)
    val colorSurface = Color(0xFF16161F)
    val colorBackground = Color(0xFFF5F7F3)
    
    val spacingSmall = 8.dp
    val spacingMedium = 16.dp
    val spacingLarge = 24.dp
    
    val cornerRadiusSmall = 8.dp
    val cornerRadiusMedium = 10.dp
    val cornerRadiusLarge = 22.dp
}
```

## Success Metrics
- **Completion Rate**: % of users who reach "Ready" screen
- **Conversion Rate**: % who proceed to server setup
- **Drop-off Rate**: Identify which screens lose the most users
- **Time to Completion**: Average time to finish onboarding
- **Engagement**: Track feature discovery after onboarding

## Accessibility
- All text meets WCAG AA contrast standards (4.5:1 minimum)
- Interactive elements are ≥48px touch targets
- Proper content descriptions for all icons
- Support for system dark mode (future consideration)
- Voice-over compatibility for screen readers

## Launch Checklist
- [ ] Design all 6 screens in Figma
- [ ] Create all custom SVG icons
- [ ] Implement Kotlin composables
- [ ] Add animations and transitions
- [ ] Test on multiple device sizes (small phone, tablet)
- [ ] Implement DataStore persistence
- [ ] Add navigation routing
- [ ] Analytics integration
- [ ] Beta testing with early users
- [ ] Gather feedback and iterate
