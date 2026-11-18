/** A coloured letter tile for a player (no skin lookups: nothing is sent to other sites). */
const AVATAR_COLORS = ["#7FE620", "#1CB0F6", "#FF9600", "#CE82FF", "#FF4B4B", "#2BD9A8"];

export function Avatar({ name, size = 32, dim }: { name: string; size?: number; dim?: boolean }) {
  const clean = name.replace(/^\./, "");
  let h = 0;
  for (let i = 0; i < clean.length; i++) h = (h * 31 + clean.charCodeAt(i)) >>> 0;
  const color = AVATAR_COLORS[h % AVATAR_COLORS.length];
  return (
    <span
      className="shrink-0 inline-flex items-center justify-center rounded-lg font-extrabold text-black/80"
      style={{ width: size, height: size, background: color, opacity: dim ? 0.45 : 1, fontSize: size * 0.42 }}
      aria-hidden="true"
    >
      {(clean[0] || "?").toUpperCase()}
    </span>
  );
}
