export interface BlogPost {
  id: string;
  title: string;
  date: string;
  category: string;
  excerpt: string;
  content: string;
}

export const blogPosts: BlogPost[] = [
  {
    id: "pockethost-vs-aternos",
    title: "PocketHost vs Aternos & Cloud Hosts: Why We Stay 100% Free With Zero Queues",
    date: "September 15, 2026",
    category: "Comparison",
    excerpt: "Tired of 45-minute queues and servers that shut down after 5 minutes of walking away? Here is how PocketHost gives you free, instant hosting right from your phone.",
    content: `
If you've ever tried hosting a Minecraft world for your friends on free cloud hosting services like Aternos or Minehut, you know the frustration:
- **Long Waiting Queues**: Waiting 20 to 45 minutes in a queue during peak gaming hours just to start your server.
- **Aggressive Auto-Shutdowns**: Walking away for 5 minutes only to find the server shut down because zero players were active.
- **Cloud World Wipes**: Having your world deleted or locked because you didn't log into a web panel for a couple of weeks.
- **Paywalled Limits**: Being capped at 10 player slots unless you pay recurring monthly fees.

Today, we launched our official **Capability Comparison Matrix** on the PocketHost homepage to clearly highlight why hosting directly on your Android phone is the superior choice.

## How PocketHost Solves These Problems

### 1. Zero Queue Times (Instant Start)
PocketHost runs a native PaperMC server engine directly on your phone's processor. When you tap **Start Server**, it boots up immediately in 5 to 10 seconds. You never wait in a queue behind thousands of other users.

### 2. No Inactivity Auto-Shutdown
PocketHost never terminates your world because nobody is connected. As long as your phone is running the app, your server stays online and ready for your friends to hop in anytime.

### 3. Your Worlds Are Saved Locally
Your server files, plugins, and world saves are stored 100% locally in your Android device storage. They are never deleted due to account inactivity or missed payments. You can export or backup your world folder anytime.

### 4. Zero Port Forwarding Needed
With our built-in encrypted relay tunneling, you don't need access to your home router or a public IPv4 address. PocketHost generates a join link that your friends on PC, mobile, or console can use to connect instantly from anywhere in the world.

### 5. Full Custom Plugin Support (.jar)
Unlike other free hosts that restrict you to a whitelist of approved plugins, PocketHost lets you install any Bukkit, Spigot, or PaperMC \`.jar\` plugin directly by dropping it into your plugins folder.

Check out the full comparison table on our [Home Page](/) and download PocketHost for free!
    `,
  },
  {
    id: "interactive-interface-and-community",
    title: "New Interactive Mobile Showcase & Official @pockethostmc Community",
    date: "September 15, 2026",
    category: "Update",
    excerpt: "Explore our new interactive screenshots gallery with full-screen phone previews, 50% faster page loads, and join our official @pockethostmc Instagram.",
    content: `
We are rolling out major usability and community updates across PocketHost to give players a transparent look at our mobile interface and connect server admins worldwide.

## Interactive Screenshot Gallery with Phone Lightbox
You can now preview the complete PocketHost interface directly on our website before downloading:
- **Direct Category Tabs**: Quickly switch between the Server Dashboard, Player Management, Operator Chat Terminal, Themes, Engine Optimization, Plugins, and World Backups.
- **High-Fidelity Phone Popup**: Tap on any screenshot card to open a full-resolution 3D phone mockup lightbox. Inspect live TPS telemetry, player kick/ban menus, and server command controls.
- **Keyboard Navigation**: Browse through screenshots using your arrow keys (\`←\` and \`→\`) or close with \`Escape\`.

## 50% Faster Website Performance
We re-engineered our website with modern route code-splitting, custom font preloading, and caching optimizations. The initial bundle size has been cut in half (down from 453 kB to 224 kB), delivering instant load times on mobile 4G and 5G networks.

## Join Us on Instagram: @pockethostmc
We have officially launched our community Instagram handle: **[@pockethostmc](https://www.instagram.com/pockethostmc)**!
Follow us for:
- Server setup tips and plugin tutorials.
- Community server spotlights and player builds.
- Early sneak peeks of upcoming PocketHost updates.

Thank you for being part of our growing community!
    `,
  },
  {
    id: "official-rebrand-and-specs",
    title: "PocketHost Official Rebranding & Android Hardware Guidelines",
    date: "September 6, 2026",
    category: "Announcement",
    excerpt: "PocketCraft is now officially PocketHost! Check out our new visual identity and realistic Android hardware requirements for smooth PaperMC hosting.",
    content: `
PocketCraft has officially transitioned to its permanent brand name: **PocketHost**!

Along with the new high-contrast emerald logo and refined visual identity, we've updated our official hardware recommendations to help you get the best possible performance out of your mobile server.

## Realistic Android Hardware Guidelines

PocketHost is engineered to be lightweight, but running a real Java Edition PaperMC server requires capable mobile hardware. Here are our verified recommendations:

### Minimum Requirements:
- **Operating System**: Android 8.0 (Oreo) or newer
- **Architecture**: 64-bit ARM (ARM64 / aarch64)
- **Total Device RAM**: 3 GB
- **Allocated Server RAM**: 1.5 GB
- **Player Capacity**: 2 to 5 players smoothly on vanilla gameplay

### Recommended Specifications:
- **Operating System**: Android 11 or newer
- **Processor**: Snapdragon 7xx / 8xx series, Dimensity 800+, or equivalent
- **Total Device RAM**: 6 GB or 8 GB
- **Allocated Server RAM**: 3 GB to 4 GB
- **Player Capacity**: 10 to 20+ concurrent players with plugins and Bedrock crossplay

## No Root Required
PocketHost runs as a completely standalone, sandboxed user application. You **do not** need root access, unlocked bootloaders, or custom ROMs. Just install the APK, configure your RAM slider, and press start!
    `,
  },
  {
    id: "direct-apk-downloads",
    title: "High-Speed Direct APK Downloads & Live Console Features",
    date: "August 21, 2026",
    category: "Release",
    excerpt: "Download the latest PocketHost APK directly from GitHub CDN with zero ad-links, plus preview real server dashboard and player stats screens.",
    content: `
We've upgraded our distribution infrastructure to provide direct, clean APK downloads without any third-party ad gateways, popups, or download counters.

## Direct CDN-Backed Releases
All download buttons across [pockethost.online](/) now link directly to GitHub's global release CDN mirrors:
- Instant one-tap download of the compiled \`PocketHost.apk\`.
- Cryptographic release verification.
- Completely free and open access.

## Live Operator Terminal & Player Controls
In this release, we've showcased two of PocketHost's most requested administrative tools:
1. **Live Command Console**: Execute vanilla and Bukkit commands directly from your phone's keyboard. Change gamemodes, grant items, adjust difficulty, and set spawn points without having to join as a player.
2. **Player Controls & Stats**: Real-time list of every connected Java and Bedrock player with their ping, IP, and one-tap kick, ban, and op buttons.

Grab the latest update and start hosting today!
    `,
  },
  {
    id: "beta-launch",
    title: "PocketHost Beta Launch",
    date: "March 31, 2026",
    category: "Announcement",
    excerpt: "I'm excited to announce the beta release of PocketHost. Host Minecraft servers on your Android phone with zero setup.",
    content: `
I'm thrilled to announce that PocketHost is now available in beta! After months of development and testing, I'm ready to share my vision with the world.

## What is PocketHost?

PocketHost lets you host a real Minecraft Java Edition server directly on your Android phone. No PC required, no complex setup, just tap and play. Your server runs on high-performance relay infrastructure, so your friends can join from anywhere in the world.

## Key Features (Beta)

- ✅ Real Java Edition server hosting on Android
- ✅ Bedrock compatibility for cross-platform play
- ✅ Plugin support (Bukkit/Spigot)
- ✅ Global relay network
- ✅ Intuitive mobile interface
- ✅ 24/7 server uptime capability

## Getting Started

1. Download PocketHost from the website
2. Select your Minecraft version
3. Start your server
4. Share the join address with your friends

That's it! Your server is live.
    `,
  },
  {
    id: "cross-platform-play",
    title: "Cross-Platform Play Now Supported",
    date: "April 1, 2026",
    category: "Feature",
    excerpt: "Bedrock and Java players can now play together on the same server. No more separate servers for different platforms.",
    content: `
One of the most requested features is now live: cross-platform play! Bedrock Edition players (console, mobile, Windows 10/11) can now join Java Edition servers hosted on PocketHost.

## How It Works

Your PocketHost server automatically supports Bedrock clients. When configured, GeyserMC translates network packets seamlessly between Bedrock and Java editions.

## Getting Started

1. Open PocketHost app
2. Enable Bedrock Crossplay in Server Settings
3. Start your server
4. Share the join address with Bedrock and Java friends alike!
    `,
  },
  {
    id: "server-optimization-tips",
    title: "Server Optimization Tips for Mobile Hardware",
    date: "April 2, 2026",
    category: "Guide",
    excerpt: "Learn how to optimize your PocketHost server for maximum performance and player slots on your Android device.",
    content: `
Running a server on mobile hardware is different from running one on a dedicated desktop CPU. Here are our top tips to maximize TPS and eliminate tick lag:

## 1. Manage Your RAM Allocation
Keep between 8-10 chunks view distance on mobile for smooth gameplay without excessive memory pressure.

## 2. Optimize World Settings
Set simulation distance slightly lower than render distance to minimize entity tick overhead while preserving sightlines.

## 3. Plugin Management
Only install plugins you actively use and ensure they are compiled for PaperMC.
    `,
  },
  {
    id: "plugin-support-released",
    title: "Plugin Support Released",
    date: "April 3, 2026",
    category: "Feature",
    excerpt: "PocketHost now supports Bukkit and Spigot plugins. Install your favorite plugins with zero downtime.",
    content: `
Plugins are fully supported in PocketHost! Install your favorite Bukkit, Spigot, and PaperMC plugins with zero downtime using our built-in file manager.

## What are Plugins?
Plugins extend your Minecraft server with custom features like economy, anti-cheat, land claims, and RPG skills.

## How to Install Plugins
1. Open PocketHost app
2. Navigate to Server Files > plugins
3. Drop in any valid .jar plugin file
4. Restart or reload your server!
    `,
  },
];
