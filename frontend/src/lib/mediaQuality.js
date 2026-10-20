// Best-effort quality badges derived from release filenames, not verified media metadata.
export function qualityTags(release = "") {
  const name = String(release);
  const rules = [
    [/\b(?:2160p|4k|uhd)\b/i, "4K"],
    [/\b(?:1080p|fhd)\b/i, "1080p"],
    [/\b(?:720p|hd)\b/i, "720p"],
    [/\b(?:dolby[ ._-]?vision|dovi|dv(?:[ ._-]?p\d)?)\b/i, "Dolby Vision"],
    [/\b(?:hdr10\+|hdr10plus)\b/i, "HDR10+"],
    [/\b(?:hdr10)\b/i, "HDR10"],
    [/\b(?:hdr)\b/i, "HDR"],
    [/\b(?:atmos)\b/i, "Atmos"],
    [/\b(?:aac)\b/i, "AAC"],
    [/\b(?:flac)\b/i, "FLAC"],
    [/\b(?:opus)\b/i, "Opus"],
    [/\b(?:10[ ._-]?bit)\b/i, "10-bit"],
    [/\b(?:truehd)\b/i, "TrueHD"],
    [/\b(?:dts[ ._-]?hd|dts[ ._-]?x)\b/i, "DTS-HD / X"],
    [/\b(?:ddp|eac3)\b/i, "DD+"],
    [/\b(?:remux)\b/i, "REMUX"],
    [/\b(?:web[ ._-]?dl|webdl)\b/i, "WEB-DL"],
    [/\b(?:bluray|blu[ ._-]?ray)\b/i, "Blu-ray"],
    [/\b(?:hevc|h265|x265)\b/i, "HEVC"],
    [/\b(?:av1)\b/i, "AV1"],
    [/\b(?:avc|h264|x264)\b/i, "H.264"],
    [/\b(?:hun|hungarian|magyar)\b/i, "HU"],
  ];
  return rules.filter(([pattern]) => pattern.test(name)).map(([, label]) => label);
}
