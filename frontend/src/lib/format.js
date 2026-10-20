import { normalizeHash } from "./torznabParser";

export const fmtBytes = (b) => {
  if (!b && b !== 0) return "—";
  const u = ["B", "KB", "MB", "GB", "TB"];
  let i = 0;
  let n = Number(b);
  while (n >= 1024 && i < u.length - 1) {
    n /= 1024;
    i++;
  }
  return `${n.toFixed(i >= 3 ? 2 : 0)} ${u[i]}`;
};

export const fmtDate = (d) => (d ? new Date(d).toLocaleDateString("en-US") : "—");

export const fmtSpeed = (b) => (b ? `${fmtBytes(b)}/s` : "");

export const statusOf = (t) => {
  const s = (t.download_state || "").toLowerCase();
  if (s.includes("error") || s.includes("fail") || s.includes("stalled")) return "error";
  if (t.download_finished || t.download_present || s === "completed" || s === "cached" || s.startsWith("uploading"))
    return "done";
  return "active";
};

export const STATUS_LABEL = { done: "Done", active: "Downloading", error: "Error" };
export const STATUS_COLOR = { done: "#10B981", active: "#3B82F6", error: "#D71921" };

const SIZE_UNITS = { KB: 1024, MB: 1024 ** 2, GB: 1024 ** 3, TB: 1024 ** 4 };

export const resolutionOf = (s) => {
  const t = (s || "").toLowerCase();
  if (/4k|2160/.test(t)) return "4K";
  if (/1080/.test(t)) return "1080p";
  if (/720/.test(t)) return "720p";
  if (/480|576|sd/.test(t)) return "SD";
  return "Other";
};

export const parseStream = (s) => {
  const lines = (s.title || "").split("\n");
  const meta = lines.find((l) => l.includes("👤") || l.includes("💾")) || "";
  const sizeM = meta.match(/💾\s*([\d.]+)\s*(KB|MB|GB|TB)/);
  const seedM = meta.match(/👤\s*(\d+)/);
  const srcM = meta.match(/⚙️\s*(.+)$/);
  const trackers = (s.sources || []).filter((x) => x.startsWith("tracker:")).map((x) => x.slice(8));
  return {
    hash: normalizeHash(s.infoHash),
    fileIdx: s.fileIdx,
    name: lines[0] || s.behaviorHints?.filename || "Ismeretlen",
    file: lines.length > 2 ? lines[1] : null,
    resolution: resolutionOf(s.name),
    tags: (s.name || "").split("\n").slice(1).join(" "),
    size_scope: "file",
    size: sizeM ? Math.round(parseFloat(sizeM[1]) * SIZE_UNITS[sizeM[2]]) : 0,
    seeders: seedM ? parseInt(seedM[1], 10) : 0,
    source: srcM ? srcM[1].trim() : "",
    trackers,
  };
};

// "Show.Name.2023.1080p.WEB-DL" -> "Show Name"
export const cleanName = (n) => {
  const s = String(n || "").replace(/[._]+/g, " ").trim();
  const cut = s.search(/\b(19|20)\d{2}\b|\bS\d{1,2}(E\d{1,3})?\b|\b(480|576|720|1080|2160)p\b|\b4K\b/i);
  return (cut > 2 ? s.slice(0, cut).trim() : s) || String(n || "");
};
