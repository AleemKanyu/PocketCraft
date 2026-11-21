// PocketHost for PC downloads. The installers are GitHub release assets on this
// repository (AleemKanyu/PocketCraft), built by the PocketHost-Desktop release workflow.
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
  version: "1.1.0",
  date: "2026-11-21",
  get tag() {
    return `pc-v${this.version}`;
  },
};

const fileFor = (platform: string, ext: string) => `PocketHost-${desktopRelease.version}-${platform}.${ext}`;

export const desktopDownloads: DesktopDownload[] = [
  { os: "windows", label: "Windows installer", detail: "Windows 10 and 11, 64-bit (.msi)", file: fileFor("windows-x64", "msi"), sizeMb: 0 },
  { os: "macos", label: "macOS · Apple Silicon", detail: "M1, M2, M3 and newer Macs (.dmg)", file: fileFor("macos-arm64", "dmg"), sizeMb: 0 },
  { os: "macos", label: "macOS · Intel", detail: "Intel-based Macs (.dmg)", file: fileFor("macos-x64", "dmg"), sizeMb: 0 },
  { os: "linux", label: "Linux · Debian / Ubuntu", detail: "Ubuntu, Debian, Mint, Pop!_OS (.deb)", file: fileFor("linux-amd64", "deb"), sizeMb: 0 },
  { os: "linux", label: "Linux · Fedora / RHEL", detail: "Fedora, RHEL, openSUSE (.rpm)", file: fileFor("linux-x86_64", "rpm"), sizeMb: 0 },
];

export const RELEASES_REPO = "https://github.com/AleemKanyu/PocketCraft";

export function downloadUrl(file: string): string {
  return `${RELEASES_REPO}/releases/download/${desktopRelease.tag}/${file}`;
}

export const releasePageUrl = () => `${RELEASES_REPO}/releases/tag/${desktopRelease.tag}`;
