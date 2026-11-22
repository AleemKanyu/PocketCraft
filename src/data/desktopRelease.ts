// PocketHost PC downloads. The installers are GitHub release assets on this repository
// (AleemKanyu/PocketCraft), built by the PocketHost-Desktop release workflow.
// To ship a new version: publish a `pc-vX.Y.Z` release with the same file names, then
// bump `version` (and the sizes) here.

export type DesktopOs = "windows" | "macos" | "linux";

export interface DesktopDownload {
  os: DesktopOs;
  label: string;
  detail: string;
  file: string;
  sizeMb: number;
}

export const desktopRelease = {
  version: "0.5.0-beta",
  /** What people see: PocketHost PC is in beta until 1.0. */
  display: "0.5 Beta",
  date: "2026-11-21",
  get tag() {
    return `pc-v${this.version}`;
  },
};

const fileFor = (platform: string, ext: string) => `PocketHostPC-${desktopRelease.version}-${platform}.${ext}`;

export const desktopDownloads: DesktopDownload[] = [
  { os: "windows", label: "Windows installer", detail: "Windows 10 and 11, 64-bit (.msi)", file: fileFor("windows-x64", "msi"), sizeMb: 101 },
  { os: "macos", label: "macOS · Apple Silicon", detail: "M1, M2, M3 and newer Macs (.dmg)", file: fileFor("macos-arm64", "dmg"), sizeMb: 112 },
  { os: "macos", label: "macOS · Intel", detail: "Intel-based Macs (.dmg)", file: fileFor("macos-x64", "dmg"), sizeMb: 113 },
  { os: "linux", label: "Linux · Arch", detail: "Arch, Manjaro, EndeavourOS, CachyOS (pacman)", file: fileFor("arch-x86_64", "pkg.tar.zst"), sizeMb: 104 },
  { os: "linux", label: "Linux · Debian / Ubuntu", detail: "Ubuntu, Debian, Mint, Pop!_OS (.deb)", file: fileFor("linux-amd64", "deb"), sizeMb: 93 },
  { os: "linux", label: "Linux · Fedora / RHEL", detail: "Fedora, RHEL, openSUSE (.rpm)", file: fileFor("linux-x86_64", "rpm"), sizeMb: 93 },
  { os: "linux", label: "Linux · Any distro", detail: "Portable .tar.gz, no install needed", file: fileFor("linux-x86_64", "tar.gz"), sizeMb: 106 },
];

/** Server software PocketHost PC can run, shown on the /pc page. */
export const desktopServerTypes: { name: string; edition: "Java" | "Bedrock"; note: string }[] = [
  { name: "Paper", edition: "Java", note: "Plugins · recommended" },
  { name: "Purpur", edition: "Java", note: "Plugins · extra options" },
  { name: "Folia", edition: "Java", note: "Plugins · multithreaded" },
  { name: "Leaves", edition: "Java", note: "Plugins · vanilla parity" },
  { name: "Fabric", edition: "Java", note: "Mods · lightweight" },
  { name: "Forge", edition: "Java", note: "Mods · classic modpacks" },
  { name: "NeoForge", edition: "Java", note: "Mods · modern" },
  { name: "Vanilla", edition: "Java", note: "Mojang's own" },
  { name: "Bedrock (Official)", edition: "Bedrock", note: "Mojang's server · Windows & Linux" },
  { name: "PowerNukkitX", edition: "Bedrock", note: "Bedrock on any system" },
];

export const RELEASES_REPO = "https://github.com/AleemKanyu/PocketCraft";

export function downloadUrl(file: string): string {
  return `${RELEASES_REPO}/releases/download/${desktopRelease.tag}/${file}`;
}

export const releasePageUrl = () => `${RELEASES_REPO}/releases/tag/${desktopRelease.tag}`;
