const MC_ASSETS_BASE =
  "https://cdn.jsdelivr.net/gh/InventivetalentDev/minecraft-assets@1.21.4/assets/minecraft/textures";

export const MINECRAFT_ICONS = {
  brand: "/app-icon-circle-hd.png",
  hero: {
    sword: `${MC_ASSETS_BASE}/item/trident.png`,
    diamond: `${MC_ASSETS_BASE}/item/diamond.png`,
    pickaxe: `${MC_ASSETS_BASE}/item/diamond_pickaxe.png`,
    trident: `${MC_ASSETS_BASE}/item/trident.png`,
  },
  features: {
    vanilla: `${MC_ASSETS_BASE}/block/grass_block_top.png`,
    latency: `${MC_ASSETS_BASE}/item/redstone.png`,
    ddos: `${MC_ASSETS_BASE}/item/iron_chestplate.png`,
    arm64: `${MC_ASSETS_BASE}/block/purple_shulker_box.png`,
    social: `${MC_ASSETS_BASE}/item/nether_star.png`,
    plugin: `${MC_ASSETS_BASE}/item/hopper.png`,
  },
  steps: {
    download: `${MC_ASSETS_BASE}/item/compass_00.png`,
    start: `${MC_ASSETS_BASE}/item/command_block_minecart.png`,
    invite: `${MC_ASSETS_BASE}/item/ender_pearl.png`,
  },
} as const;
