// Search landing pages. Each entry becomes a prerendered page (see LandingPage.tsx and
// landingRoutes in App.tsx). Keep every claim true: no ratings, no invented numbers, and
// only fair statements about other hosts (they change plans, so comparisons say "check their site").

export interface LandingSection {
  heading: string;
  paragraphs?: string[];
  bullets?: string[];
}

export interface LandingFaq {
  question: string;
  answer: string;
}

export interface LandingPageData {
  path: string;
  title: string;
  description: string;
  h1: string;
  intro: string;
  sections: LandingSection[];
  faqs: LandingFaq[];
  related: string[];
}

export const landingPages: LandingPageData[] = [
  {
    path: "/free-minecraft-server-hosting",
    title: "Free Minecraft Server Hosting: No Queue, No Credit Card | PocketHost",
    description:
      "Looking for a free Minecraft server? PocketHost runs a Java server with Bedrock crossplay on your Android phone: free, no queue, no PC, no port forwarding.",
    h1: "Free Minecraft Server Hosting With No Queue",
    intro:
      "PocketHost is a free Android app that runs a real Minecraft Java Edition server on your phone. Your friends join with an address, Bedrock players can join too, and you never wait in a queue or pay for slots.",
    sections: [
      {
        heading: "Why most free Minecraft servers feel bad",
        paragraphs: [
          "Free cloud hosts have to pay for data centers, so they limit what you get. That usually means a start-up queue at busy hours, a server that stops when nobody is online, and caps on players, plugins or world size unless you pay.",
          "PocketHost skips the data center. The server runs on the phone you already own, so there is nothing to rent and nothing to limit.",
        ],
      },
      {
        heading: "What you get for free",
        bullets: [
          "A real PaperMC Java Edition server, started in seconds with no queue",
          "Bedrock crossplay through Geyser: Android, iOS, Windows, Xbox, PlayStation and Switch players can join",
          "A public join address from the built-in relay, so no router port forwarding",
          "Bukkit, Spigot and Paper plugin support",
          "World backups, including Google Drive",
          "Your worlds stay on your phone and are never deleted for inactivity",
          "No PC, VPS or root needed",
        ],
      },
      {
        heading: "How to start a free server in 4 steps",
        bullets: [
          "Install PocketHost from Google Play (or the direct APK on pockethost.online)",
          "Create a server and pick a Minecraft version",
          "Tap Start Server",
          "Send the join address to your friends",
        ],
      },
      {
        heading: "What to know before you start",
        paragraphs: [
          "The server uses your phone's battery, memory and mobile data or Wi-Fi, and it is online only while the app is running. A recent phone handles a small group of friends well. Very large public servers need a dedicated machine, and no phone replaces one.",
        ],
      },
    ],
    faqs: [
      {
        question: "Is there a really free Minecraft server host?",
        answer:
          "Yes. PocketHost is a free Android app that hosts a Minecraft Java server on your phone with no subscription, no credit card and no queue. Cloud hosts such as Aternos and Minehut also have free tiers, with limits that differ by host.",
      },
      {
        question: "Can my friends on console or mobile join?",
        answer:
          "Yes. Bedrock Edition players on Android, iOS, Windows, Xbox, PlayStation and Switch can join through the built-in Geyser crossplay, alongside Java players.",
      },
      {
        question: "Do I need to pay for plugins or more player slots?",
        answer:
          "No. PocketHost does not sell slots. How many players fit depends on your phone's hardware.",
      },
    ],
    related: ["/aternos-alternative", "/host-minecraft-server-on-android", "/free-minecraft-server-hosts-compared"],
  },
  {
    path: "/aternos-alternative",
    title: "Best Aternos Alternative: Free Minecraft Hosting With No Queue | PocketHost",
    description:
      "Tired of the Aternos queue? PocketHost is a free Aternos alternative that runs your Minecraft server on your own Android phone: instant start, no queue, no shutdown timer.",
    h1: "A Free Aternos Alternative With No Queue",
    intro:
      "Aternos is a popular free Minecraft host, but many players hit its start-up queue and its shutdown when nobody is online. PocketHost is a free alternative that runs the server on your own Android phone, so there is no queue to join.",
    sections: [
      {
        heading: "Why people look for an Aternos alternative",
        bullets: [
          "Waiting in a queue to start the server at busy times",
          "The server stopping when no one is connected",
          "Worlds being removed from accounts that stay inactive for a long time",
          "Limits on plugins and mods compared with running your own server",
        ],
      },
      {
        heading: "How PocketHost is different",
        paragraphs: [
          "Aternos runs your server in the cloud on shared machines. PocketHost runs it on your phone, which changes the trade-offs.",
        ],
        bullets: [
          "No queue: tap Start Server and it boots on your phone",
          "No inactivity shutdown timer: the server runs while the app runs",
          "Your world files live on your phone, with optional Google Drive backup",
          "Free Java server with Bedrock crossplay and plugin support",
        ],
      },
      {
        heading: "Where Aternos is still the better pick",
        paragraphs: [
          "Be honest about the trade-off: a cloud host does not depend on your phone. If you want a server that is reachable while your phone is off, charging elsewhere, or out of battery, a cloud host fits that better. PocketHost fits best when you want a free server for you and your friends, started when you want to play.",
        ],
      },
      {
        heading: "Moving your world from Aternos",
        paragraphs: [
          "Download your world from the Aternos files page, then import the world folder into your PocketHost server. The Discord community can help if a version does not match.",
        ],
      },
    ],
    faqs: [
      {
        question: "What is the best free alternative to Aternos?",
        answer:
          "It depends on what you need. PocketHost is a free alternative if you want no queue and want to host from your own Android phone with Java and Bedrock crossplay. Minehut is another cloud option. See the comparison page for the differences.",
      },
      {
        question: "Does PocketHost have a queue like Aternos?",
        answer:
          "No. The server starts on your own phone, so there is no shared queue to wait in.",
      },
      {
        question: "Can I use my Aternos world on PocketHost?",
        answer:
          "Yes. Download your world from Aternos and import the folder into a PocketHost Java server of the same or a newer Minecraft version.",
      },
    ],
    related: ["/free-minecraft-server-hosts-compared", "/free-minecraft-server-hosting", "/minecraft-server-no-port-forwarding"],
  },
  {
    path: "/host-minecraft-server-on-android",
    title: "How to Host a Minecraft Server on Your Android Phone | PocketHost",
    description:
      "Step-by-step guide to hosting a Minecraft Java server on an Android phone with PocketHost: requirements, setup, joining, plugins and performance tips. Free, no PC or root.",
    h1: "How to Host a Minecraft Server on Your Android Phone",
    intro:
      "You can run a real Minecraft Java server on an Android phone. PocketHost packages PaperMC, a public join address and Bedrock crossplay into one free app, so setup takes a few minutes and needs no PC.",
    sections: [
      {
        heading: "What you need",
        bullets: [
          "An Android phone on Android 8.0 or later with a 64-bit (ARM64) processor",
          "A few GB of free storage and a recent phone with enough RAM for your player count",
          "A Wi-Fi or mobile data connection",
          "The PocketHost app from Google Play",
        ],
      },
      {
        heading: "Set up the server",
        bullets: [
          "Install PocketHost and open it",
          "Create a new server and choose a Minecraft version",
          "Optionally add plugins or restore a world",
          "Tap Start Server and wait for the console to say it is ready",
        ],
      },
      {
        heading: "Let friends join",
        paragraphs: [
          "PocketHost gives you a public address through its relay, so friends can connect from anywhere without port forwarding. Java players type the address in Multiplayer. Bedrock players use the same address with the Bedrock port shown in the app.",
        ],
      },
      {
        heading: "Keep it running smoothly",
        bullets: [
          "Keep the phone plugged in for long sessions and let it stay cool",
          "Lower the view distance and simulation distance for more players",
          "Close heavy apps in the background",
          "Back up your world before trying new plugins",
        ],
      },
    ],
    faqs: [
      {
        question: "Can you really host a Minecraft server on a phone?",
        answer:
          "Yes. A phone has a capable processor and enough memory to run a PaperMC server for a group of friends. PocketHost handles the setup and networking.",
      },
      {
        question: "How many players can an Android phone host?",
        answer:
          "It depends on the phone, the view distance and the plugins. Small groups of friends are the best fit. There is no artificial player cap in the app.",
      },
      {
        question: "Does the server stay on when the screen is locked?",
        answer:
          "Yes. PocketHost runs as a background service, so the server keeps running with the screen off.",
      },
    ],
    related: ["/free-minecraft-server-hosting", "/minecraft-server-no-port-forwarding", "/minecraft-java-bedrock-crossplay-server"],
  },
  {
    path: "/minecraft-server-no-port-forwarding",
    title: "Minecraft Server Without Port Forwarding (Free, Android) | PocketHost",
    description:
      "Host a Minecraft server without port forwarding or router access. PocketHost gives your phone server a public address through a built-in relay. Free for Android.",
    h1: "Host a Minecraft Server Without Port Forwarding",
    intro:
      "Port forwarding needs router access and a public IP, which many homes, schools and mobile networks do not allow. PocketHost avoids it with a built-in relay that gives your server a public address.",
    sections: [
      {
        heading: "Why port forwarding is a problem",
        bullets: [
          "You need admin access to the router, which you often do not have",
          "Many internet providers share one public IP between customers",
          "Mobile data and school or dorm networks usually block incoming connections",
          "Opening ports can expose your network if done wrong",
        ],
      },
      {
        heading: "How the PocketHost relay works",
        paragraphs: [
          "Your phone makes an outgoing connection to the relay. Friends connect to the relay address, and the traffic is passed to your server. Because the connection starts from your phone, nothing needs to be opened on the router.",
        ],
      },
      {
        heading: "Local play on the same Wi-Fi",
        paragraphs: [
          "Players on the same Wi-Fi can also join with the phone's local address, which skips the relay and has the lowest delay.",
        ],
      },
    ],
    faqs: [
      {
        question: "Can I host a Minecraft server without opening ports?",
        answer:
          "Yes. PocketHost uses an outgoing relay connection, so you get a public join address with no port forwarding and no router changes.",
      },
      {
        question: "Does it work on mobile data?",
        answer:
          "Yes. Because the phone connects out to the relay, hosting works over mobile data as well as Wi-Fi, though a stable connection gives a better experience.",
      },
    ],
    related: ["/host-minecraft-server-on-android", "/free-minecraft-server-hosting", "/aternos-alternative"],
  },
  {
    path: "/minecraft-java-bedrock-crossplay-server",
    title: "Minecraft Java and Bedrock Crossplay Server (Free) | PocketHost",
    description:
      "Run a free Minecraft server where Java and Bedrock players join together. PocketHost bundles PaperMC and Geyser so Android, iOS, console and PC friends can play on one world.",
    h1: "A Free Minecraft Server for Java and Bedrock Crossplay",
    intro:
      "Java and Bedrock players cannot normally join the same server. PocketHost runs PaperMC with Geyser built in, so friends on phones, consoles and PC can all play on one world.",
    sections: [
      {
        heading: "Who can join",
        bullets: [
          "Java Edition on Windows, macOS and Linux",
          "Bedrock Edition on Android and iOS",
          "Bedrock Edition on Windows, Xbox, PlayStation and Nintendo Switch",
        ],
      },
      {
        heading: "How it works",
        paragraphs: [
          "PaperMC is the Java server. Geyser translates Bedrock connections so they appear as normal players. PocketHost configures both and shows the join details in the app.",
        ],
      },
      {
        heading: "Things to know",
        paragraphs: [
          "Some Java-only plugins and mechanics behave differently for Bedrock players. Joining from a console depends on that console's own rules for adding custom servers, so check the PocketHost Discord if you get stuck.",
        ],
      },
    ],
    faqs: [
      {
        question: "Can Bedrock and Java players play together?",
        answer:
          "Yes, with Geyser. PocketHost includes it, so Bedrock players join the same Java server as everyone else.",
      },
      {
        question: "Is crossplay hosting free on PocketHost?",
        answer: "Yes. PocketHost is free and crossplay is included.",
      },
    ],
    related: ["/free-minecraft-server-hosting", "/host-minecraft-server-on-android", "/free-minecraft-server-hosts-compared"],
  },
  {
    path: "/free-minecraft-server-hosts-compared",
    title: "Free Minecraft Server Hosts Compared: Aternos, Minehut, PocketHost | PocketHost",
    description:
      "A fair comparison of free Minecraft server options: Aternos, Minehut, self-hosting and PocketHost. Queues, shutdowns, plugins, crossplay and who each one suits.",
    h1: "Free Minecraft Server Hosts Compared (2026)",
    intro:
      "There is no single best free Minecraft server. Cloud hosts run without your phone but come with limits, while PocketHost runs on your own Android phone. This page compares the main free options by what they trade off. Plans change, so check each host's site for current limits.",
    sections: [
      {
        heading: "Quick comparison",
        bullets: [
          "Aternos (cloud): free, ad-supported, known for a start-up queue at busy times and shutting down when empty. Good if you want a server that does not depend on your devices.",
          "Minehut (cloud): free tier with a player cap and sleep behaviour, with paid upgrades. Good for a public, always-listed server.",
          "PocketHost (your Android phone): free, no queue, no inactivity shutdown, Java plus Bedrock crossplay, plugins. Online only while your phone is running the app.",
          "Self-hosting on a PC or VPS: full control and the best performance, but needs hardware, setup and often port forwarding or a paid VPS.",
        ],
      },
      {
        heading: "Which one should you pick?",
        bullets: [
          "Want to play with friends tonight, no queue, no cost: PocketHost",
          "Want a server that is up while your phone and PC are off: a cloud host",
          "Want a large public server with many players: a paid VPS or dedicated host",
          "Want full control over everything: self-hosting",
        ],
      },
      {
        heading: "How we keep this fair",
        paragraphs: [
          "PocketHost is made by the team behind this site, so we list the trade-offs of our own approach too: the server depends on your phone staying powered and connected, and a phone cannot match a dedicated machine for very large player counts.",
        ],
      },
    ],
    faqs: [
      {
        question: "What is the best free Minecraft server host?",
        answer:
          "It depends on your needs. PocketHost is strong if you want no queue and a Java and Bedrock server on your own phone. Cloud hosts like Aternos and Minehut suit people who want a server independent of their devices.",
      },
      {
        question: "Which free Minecraft host has no queue?",
        answer:
          "PocketHost has no queue because the server runs on your own phone instead of shared cloud machines.",
      },
      {
        question: "Which free Minecraft host supports Bedrock players?",
        answer:
          "PocketHost includes Geyser crossplay so Bedrock players can join its Java server. Other hosts vary, so check their current features.",
      },
    ],
    related: ["/aternos-alternative", "/free-minecraft-server-hosting", "/minecraft-java-bedrock-crossplay-server"],
  },
];

export const landingPageByPath = (path: string) => landingPages.find((page) => page.path === path);
