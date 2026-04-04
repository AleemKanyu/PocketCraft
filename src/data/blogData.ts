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
    id: "beta-launch",
    title: "PocketCraft Beta Launch",
    date: "March 31, 2026",
    category: "Announcement",
    excerpt: "I'm excited to announce the beta release of PocketCraft. Host Minecraft servers on your Android phone with zero setup.",
    content: `
I'm thrilled to announce that PocketCraft is now available in beta! After months of development and testing, I'm ready to share my vision with the world.

## What is PocketCraft?

PocketCraft lets you host a real Minecraft Java Edition server directly on your Android phone. No PC required, no complex setup, just tap and play. Your server runs on AWS-powered relay infrastructure, so your friends can join from anywhere in the world.

## Key Features (Beta)

- ✅ Real Java Edition server hosting on Android
- ✅ Bedrock compatibility for cross-platform play
- ✅ Plugin support (Bukkit/Spigot)
- ✅ Global relay network (AWS CDN)
- ✅ Intuitive mobile interface
- ✅ 24/7 server uptime

## Getting Started

1. Download PocketCraft from the website
2. Select your Minecraft version
3. Start your server
4. Share the join address with your friends

That's it! Your server is live.

## What's Next?

I have an exciting roadmap ahead. Phase 4 will bring world backups to Google Drive, advanced monitoring, and even more customization options.

Thank you for being part of the PocketCraft journey!
    `,
  },
  {
    id: "cross-platform-play",
    title: "Cross-Platform Play Now Supported",
    date: "April 1, 2026",
    category: "Feature",
    excerpt: "Bedrock and Java players can now play together on the same server. No more separate servers for different platforms.",
    content: `
One of the most requested features is now live: cross-platform play! Bedrock Edition players (console, mobile, Windows 10/11) can now join Java Edition servers hosted on PocketCraft.

## How It Works

Your PocketCraft server automatically supports Bedrock clients on the same WiFi network. When a Bedrock player enters your server's LAN IP address, they connect to the same world as your Java-playing friends.

## Getting Started

Good news! Bedrock compatibility is already enabled by default on all PocketCraft servers. Your Bedrock-playing friends can join immediately without any extra configuration needed.

**Quick Steps:**

1. Open PocketCraft app
2. Go to Server Console
3. Note your LAN IP address (example: 192.168.1.100)
4. Share this address with Bedrock players on your WiFi network

## How to Connect (Bedrock Players)

Bedrock Edition players should:

1. Open Minecraft Bedrock Edition
2. Go to Play > Friends > Add Server
3. Enter your server IP (example: 192.168.1.100)
4. **Important**: Do NOT include the port (:25565) - Bedrock connects on port 19132 automatically
5. Click Add and join!

## Current Limitations

**WiFi Only:** Cross-platform play is currently supported only on the same WiFi network. Internet connections are not yet supported for Bedrock Edition players (coming in Phase 5).

**Experimental Feature:** Bedrock support is still experimental and not officially supported. This means:
- Play at your own discretion
- Bugs and compatibility issues may occur
- Some features may not work as expected
- I'm actively improving this feature based on feedback

**Known Issues:**
- Some Java-exclusive features may not work perfectly for Bedrock players
- Skins display slightly differently (Bedrock vs Java)
- Some plugins may not support Bedrock clients
- Performance may vary depending on your device

## What's Coming Next

Phase 5 will bring a major upgrade: Bedrock players will be able to join through the internet relay, just like Java players can today! This means your Bedrock-playing friends won't need to be on your WiFi anymore.

I'm also working on better integration between Java and Bedrock editions, including unified chat, skin compatibility, and cross-platform achievements.

Thank you for testing this experimental feature and helping me improve PocketCraft!
    `,
  },
  {
    id: "server-optimization-tips",
    title: "Server Optimization Tips",
    date: "April 2, 2026",
    category: "Guide",
    excerpt: "Learn how to optimize your PocketCraft server for maximum performance and player slots on your Android device.",
    content: `
Running a server on mobile is different from running one on a PC. Here are my best tips to get the most out of your PocketCraft server.

## 1. Manage Your RAM

Android devices have limited RAM. Monitor your server's memory usage:
- View Distance: Keep between 8-12 chunks for smooth gameplay
- Entity Limit: Reduce max entities if experiencing lag

## 2. Optimize World Settings

- Disable weather if not needed
- Set spawn protection to reasonable limits
- Keep simulation distance lower than view distance

## 3. Plugin Management

- Only install plugins you actually use
- Regularly update plugins for performance improvements
- Remove outdated or unnecessary plugins

## 4. Server Settings

- Reduce max player count to match your device capability
- Enable compression for bandwidth savings
- Use server properties wisely

## 5. Storage Optimization

- Backup worlds to Google Drive regularly
- Delete unused worlds to free up space
- Use efficient world generation settings

## 6. Network Settings

- Relay is automatically optimized for your region
- Check latency settings for your players
- Monitor bandwidth usage

## Results

With these optimizations, I've seen Android devices handle 15-20 concurrent players smoothly!

Try these tips and let me know how it goes!
    `,
  },
  {
    id: "plugin-support-released",
    title: "Plugin Support Released",
    date: "April 3, 2026",
    category: "Feature",
    excerpt: "PocketCraft now supports Bukkit and Spigot plugins. Install your favorite plugins with zero downtime.",
    content: `
Plugins are now fully supported in PocketCraft! Install your favorite Bukkit and Spigot plugins with zero downtime using my new plugin manager.

## What are Plugins?

Plugins extend your Minecraft server with custom features like:
- Custom commands and gameplay mechanics
- Anti-cheat systems
- Economy and ranking systems
- Custom events and minigames
- World management tools

## How to Install Plugins

1. Open PocketCraft app
2. Navigate to Plugins Hub
3. Browse or search for plugins
4. Click "Install"
5. Restart server (if required)

That's it! Plugins are installed and ready to use.

## Finding Plugins

I've partnered with Modrinth to bring you thousands of plugins:
- Search by category
- Filter by compatibility
- View ratings and downloads
- Read reviews from other players

## Popular Plugins

Some community favorites:
- **EssentialsX** - Essential server commands
- **LiteBans** - Advanced ban/kick system
- **SkillAPI** - RPG-style progression
- **Citizens** - NPC management
- **WorldGuard** - Region protection

## Plugin Management

- Enable/disable plugins from settings
- View plugin versions and compatibility
- Auto-update plugins for security
- Uninstall with one click

## Performance Impact

Plugins do use additional resources. Monitor your server performance:
- Check memory usage
- Monitor TPS (ticks per second)
- Adjust settings if needed

Start with essential plugins and add more as you go. Happy customizing!
    `,
  },
];
