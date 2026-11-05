"""Readable filesystem labels are independent from provider-id verification."""
import html
import re


YEAR = r"(?:18[7-9]\d|19\d{2}|20\d{2}|21\d{2}|2200)"
_AD = re.compile(r"(?i)^\s*\[?(?:www\.)?[a-z0-9.-]+\.(?:org|com|net|re|to|tv|io|me)\]?\s*(?:[-_]+\s*|\s{2,})")
_SEASON = re.compile(r"(?i)(?:^|[ ._-])(?:S[ ._-]*\d{1,2}(?:[ ._-]*E[ ._-]*\d{1,3})?(?:(?:-E?\d+)|(?:E\d+))*|Season[ ._-]*\d{1,2}|\d{1,2}x\d{2})(?=$|[ ._-])")
_QUALITY = re.compile(r"(?i)(?:^|[ ._-])(?:2160p|1080[pi]|720p|480p|UHD|WEB[ ._-]?DL|WEBRip|Blu[ ._-]?Ray|REMUX|[Hx][ ._-]?26[45])\b")


def label(value, release=False):
    value = html.unescape(str(value or ""))
    value = re.sub(r"\[(?:imdbid|tmdbid|tvdbid)-[^\]]*\]", "", value, flags=re.I)
    value = value.replace(":", " - ").replace("_", " ")
    if release:
        value = value.replace(".", " ")
    value = re.sub(r'[<>"/\\|?*\x00-\x1f]', " ", value)
    return re.sub(r"\s+", " ", value).strip(" .-_")


def normalized(value):
    return re.sub(r"[\W_]+", "", label(value).casefold())


def media_kind(job, torrent):
    if job.get("type") in ("movie", "series"):
        return job["type"]
    names = [str(torrent.get("name") or ""), str(job.get("name") or "")]
    names += [str(f.get("name") or "") for f in torrent.get("files", []) if isinstance(f, dict)]
    if any(_SEASON.search(name.replace("_", " ")) for name in names):
        return "series"
    return "movie"


def release_title(value):
    raw = html.unescape(str(value or "")).strip()
    if "://" in raw or not raw:
        return "", None
    raw = _AD.sub("", raw)
    raw = re.sub(r"(?i)^\[(?:TGx|YTS[^\]]*)\][ ._-]*", "", raw)
    without_extension = re.sub(r"(?i)\.(?:mkv|mp4|m4v|avi|mov|ts|m2ts|webm|torrent)$", "", raw)
    technical = without_extension != raw
    raw = without_extension
    cuts = [m.start() for pattern in (_SEASON, _QUALITY) if (m := pattern.search(raw)) and m.start() > 0]
    if cuts:
        raw = raw[:min(cuts)]
        technical = True
    raw = raw.strip(" .-_")
    # The last suffix year is the release year, not numbers inside titles such
    # as 2001 or Blade Runner 2049. Clean app titles are retained verbatim.
    year = None
    suffix = re.search(r"[ ._(\[](" + YEAR + r")[ ._)\]]*$", raw)
    if suffix and (technical or raw.endswith(")") or raw.endswith("]")):
        year = int(suffix[1])
        raw = raw[:suffix.start()].rstrip(" .-_(")
    return label(raw, release=True), year


def title_year(job, torrent):
    media = job.get("media") if isinstance(job.get("media"), dict) else {}
    title = label(media.get("title")) if media.get("title") else ""
    hint = None
    if not title:
        raw = str(job.get("name") or "")
        if re.fullmatch(r"(?i)(?:torrent|download|untitled)(?:\s+\d+)?", raw.strip()):
            raw = ""
        title, hint = release_title(raw)
    if not title:
        title, hint = release_title(torrent.get("name", ""))
    if not title:
        # An explicitly approved but unlabelled file still gets a dedicated
        # folder, without guessing its series or publishing another torrent.
        title = "Series" if job.get("type") == "series" else "Movie"
        title += " " + str(job.get("id", "unknown"))
    year = media.get("year")
    if isinstance(year, bool) or not isinstance(year, int) or not 1870 <= year <= 2200:
        years = set(re.findall(r"(?<![a-zA-Z0-9])(" + YEAR + r")(?![a-zA-Z0-9])", str(torrent.get("name", ""))))
        years -= set(re.findall(r"\b\d{4}\b", title))
        year = hint or (int(next(iter(years))) if len(years) == 1 else None)
    return title, year
