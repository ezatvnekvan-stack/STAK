"""One verified media identity -> one directory. Symlinks and metadata only."""
import hashlib
import json
import os
import re
from pathlib import Path, PurePosixPath

import filter_server as legacy
from server.titles import label, normalized, title_year, media_kind


class NeedsIdentity(ValueError):
    pass


def portable_name(value, limit=140):
    value = legacy.safe_name(value)
    while len(value.encode("utf-8")) > limit:
        value = value[:-1]
    return value.rstrip(" .") or "Untitled"


def clean_media(value, kind):
    if not isinstance(value, dict):
        return {}
    result = {}
    for key, pattern in (("imdb", r"tt\d{3,12}"), ("tmdb", r"\d{1,12}"), ("tvdb", r"\d{1,12}")):
        raw = str(value.get(key) or "")
        if raw:
            if not re.fullmatch(pattern, raw):
                raise ValueError("Invalid media identity")
            result[key] = raw
    if value.get("title"):
        result["title"] = label(value["title"])
    year = value.get("year")
    if year is not None:
        if isinstance(year, bool) or not re.fullmatch(r"\d{4}", str(year)) or not 1870 <= int(year) <= 2200:
            raise ValueError("Invalid media year")
        result["year"] = int(year)
    return result


def identity(job, torrent, year_hint=None):
    kind = media_kind(job, torrent)
    if kind not in ("movie", "series"):
        raise NeedsIdentity("Choose Movies or Series")
    media = clean_media(job.get("media"), kind)
    title, year = title_year(dict(job, media=media), torrent)
    title = portable_name(title)
    if year is None:
        year = year_hint
    provider = next((k for k in ("imdb", "tmdb", "tvdb") if media.get(k)), None)
    # A provider id improves metadata matching but is not required for tidy
    # folders. Filename-based grouping never fabricates an IMDb/TMDB identity.
    key = f"{kind}:{provider}:{media[provider]}" if provider else f"{kind}:title:{title.casefold()}:{year}"
    folder = title + (f" ({year})" if year else "")
    if provider:
        folder += f" [{provider}id-{media[provider]}]"
    return key, title, folder


def episode_numbers(name, parents=()):
    """Use explicit file markers or an explicit season parent; never assume a season."""
    name = name.replace("\\", "/").rsplit("/", 1)[-1].replace("_", " ")
    match = re.search(r"(?i)\bS[ .-]*(\d{1,2})[ .-]*E[ .-]*(\d{1,3})((?:(?:-E?\d{1,3})|(?:E\d{1,3}))*)\b", name)
    if not match:
        other = re.search(r"(?i)\b(\d{1,2})x(\d{2})(?:-(\d{2}))?\b", name)
        if not other:
            seasons = set()
            for parent in parents:
                value = str(parent).replace("_", " ")
                seasons.update(int(x) for x in re.findall(r"(?i)(?:^|[ .-])(?:Season[ .-]*|S)(\d{1,2})(?=$|[ .-])", value))
            episode = re.search(r"(?i)(?:^|[ .-])(?:E|Episode[ .-]*)(\d{1,3})(?=$|[ .-])", name)
            episode = episode or re.match(r"^(\d{1,3})[ .-]+", name)
            if len(seasons) != 1 or not episode:
                raise NeedsIdentity("Episode number missing from video filename")
            return next(iter(seasons)), [int(episode[1])]
        season, first, end = int(other[1]), int(other[2]), int(other[3] or other[2])
        numbers = list(range(first, end + 1))
    else:
        season, first = int(match[1]), int(match[2])
        tail = match[3]
        if tail.startswith("-"):
            end = int(tail.lstrip("-Ee")) if re.fullmatch(r"-E?\d{1,3}", tail, re.I) else -1
            numbers = list(range(first, end + 1))
        else:
            numbers = [first] + [int(x) for x in re.findall(r"E(\d{1,3})", tail, re.I)]
    if not numbers or len(numbers) > 20 or numbers != list(range(numbers[0], numbers[-1] + 1)):
        raise NeedsIdentity("Ambiguous multi-episode video")
    return season, numbers


def is_extra(path):
    # Release/series names can legitimately contain "Extras" or "Sample".
    # Only dedicated internal folders and standalone/suffixed files are extras.
    directories = [part.casefold() for part in path.parts[1:-1]]
    if any(part in ("sample", "samples", "extras", "trailers", "featurettes") for part in directories):
        return True
    return bool(re.search(r"(?i)(?:^|[. _-])(?:sample|trailer|featurette|extra|extras)$", path.stem))


class OrganizedFilter(legacy.Filter):
    def __init__(self, config):
        if config.get("layout", "organized") not in ("organized", "legacy"):
            raise ValueError("Unsupported library layout")
        super().__init__(config)
        if any(job.get("scope") != self.scope for job in self.get()):
            raise ValueError("Saved library approvals belong to a different TorBox account")
        self.db.execute("CREATE TABLE IF NOT EXISTS media_folders (identity TEXT PRIMARY KEY, title TEXT NOT NULL, folder TEXT NOT NULL)")
        self.db.commit()
        self.heartbeat = 0
        self._year_hints = {}

    def catalog(self):
        catalog = super().catalog()
        years = {}
        for job in self.get():
            if not job["approved"] or job["id"] not in catalog:
                continue
            title, year = title_year(job, catalog[job["id"]])
            if year is not None:
                years.setdefault((media_kind(job, catalog[job["id"]]), normalized(title)), set()).add(year)
        self._year_hints = {key: next(iter(values)) for key, values in years.items() if len(values) == 1}
        return catalog

    def status(self):
        return dict(super().status(), server_protocol="stak-server/1", layout=self.c.get("layout", "organized"))

    def identify(self, body):
        """Metadata-only update; never change an approval or its torrent identity."""
        with self.lock:
            current = self.get(str(body.get("id", "")))
            if not current or body.get("scope") != self.scope or not current.get("hash") or body.get("hash") != current["hash"]:
                raise ValueError("Approval identity mismatch")
            media = clean_media(body.get("media"), current["type"])
            if not media:
                raise ValueError("Media identity required")
            return self.approve(dict(current, media=media))

    def approve(self, body):
        media = clean_media(body.get("media"), body.get("type")) if body.get("media") is not None else None
        with self.lock:
            # Preserve verified metadata when an older app sends an edit.
            previous = self.get(body.get("id"))
            result = super().approve(body)
            chosen = media if media is not None else (previous or {}).get("media")
            if chosen:
                saved = json.loads(self.db.execute("SELECT body FROM approvals WHERE id=?", (body["id"],)).fetchone()[0])
                saved["media"] = chosen
                self.db.execute("UPDATE approvals SET body=? WHERE id=?", (json.dumps(saved), body["id"]))
                self.db.commit()
            return self.get(body["id"])

    def import_watcher_identities(self, watcher_store):
        """Backfill only hashes already linked to this watcher's owned ledger."""
        pending = {}
        for job in self.get():
            if job["approved"] and job.get("hash") and not job.get("media"):
                pending.setdefault(job["hash"], []).append(job)
        if not pending:
            return
        records, rules = {}, {}
        for episode in watcher_store.all_episodes():
            h = episode.get("torrent_hash")
            if h not in pending:
                continue
            if h not in records:
                records[h] = watcher_store.torrent(h)
            if episode["rule_id"] not in rules:
                rules[episode["rule_id"]] = watcher_store.rule(episode["rule_id"])
            record, rule = records[h], rules[episode["rule_id"]]
            if not record or record["state"] != "added" or not rule:
                continue
            series = rule.get("series", {})
            media = {k: series[k] for k in ("imdb", "tmdb", "tvdb") if series.get(k)}
            media["title"] = series.get("name") or ""
            with self.lock:
                for job in pending.pop(h):
                    current = self.get(job["id"])
                    if current and current["approved"] and current.get("hash") == h and not current.get("media"):
                        self.approve(dict(current, media=media))

    def plan(self, job, torrent, groups):
        actual = legacy.hash_value(torrent.get("hash"))
        if job.get("hash") and job["hash"] != actual:
            raise ValueError("TorBox hash mismatch")
        if torrent.get("download_finished") is not True:
            raise ValueError("Waiting for TorBox completion")
        paths, confidence = legacy.match_source(groups, torrent.get("files", []), actual)
        job = dict(job, type=media_kind(job, torrent))
        raw_title, _ = title_year(job, torrent)
        key, title, folder = identity(job, torrent, self._year_hints.get((job["type"], normalized(raw_title))))
        with self.lock:
            stable = self.db.execute("SELECT title,folder FROM media_folders WHERE identity=?", (key,)).fetchone()
        if stable:
            title, folder = stable
        category = "shows" if job["type"] == "series" else "movies"
        videos = [p for p in paths if not is_extra(p.relative_to(self.source))]
        if not videos:
            raise NeedsIdentity("No main video found")
        if category == "movies" and len(videos) != 1:
            raise NeedsIdentity("Multiple movie videos need explicit identification")
        result = []
        for path in videos:
            quality = re.search(r"(?i)\b(2160p|1080p|720p|480p)\b", path.name.replace("_", " "))
            version = actual[:8] + (" " + quality[1].lower() if quality else "")
            base = self.output / category / folder
            if category == "shows":
                season, episodes = episode_numbers(path.name, path.relative_to(self.source).parts[:-1])
                marker = f"S{season:02d}E{episodes[0]:02d}"
                if len(episodes) > 1:
                    marker += f"-E{episodes[-1]:02d}"
                base /= f"Season {season:02d}"
                # A naked hash such as e5dbdc39 after S03E01 is parsed by Jellyfin
                # as another episode marker (E05). Delimit and label version data
                # so only a real -E02 marker can create an episode range.
                filename = f"{title} {marker} [stak-{version}]{path.suffix.lower()}"
            else:
                filename = f"{folder} - {version}{path.suffix.lower()}"
            link = base / filename
            target = str(PurePosixPath(self.c["target_root"]) / path.relative_to(self.source).as_posix())
            result.append((link, target))
            # Only matching subtitle sidecars are linked; media bytes stay unopened.
            for sidecar in path.parent.iterdir():
                if sidecar.is_symlink() or not sidecar.is_file() or sidecar.suffix.lower() not in (".srt", ".ass", ".ssa", ".vtt"):
                    continue
                if sidecar.stem == path.stem or sidecar.stem.startswith(path.stem + "."):
                    suffix = sidecar.name[len(path.stem):]
                    result.append((link.with_name(link.stem + suffix), str(PurePosixPath(self.c["target_root"]) / sidecar.relative_to(self.source).as_posix())))
        if len({str(link) for link, target in result}) != len(result):
            raise ValueError("Episode destination collision")
        with self.lock:
            saved = self.db.execute("SELECT links FROM approvals WHERE id=?", (job["id"],)).fetchone()
        owned = json.loads(saved[0]) if saved else []
        for link, target in result:
            if self.output not in link.parent.resolve().parents:
                raise ValueError("Output path escaped its root")
            if link.is_symlink():
                if str(link) not in owned or os.readlink(link) != target:
                    raise ValueError("Destination collision")
            elif link.exists():
                raise ValueError("Destination collision")
        return key, title, folder, result

    def publish(self, job, torrent, groups):
        if self.c.get("layout", "organized") == "legacy":
            return super().publish(job, torrent, groups)
        with self.lock:
            # Never let an in-flight old job undo an edit or revocation.
            current = self.get(job["id"])
            if not current or not current["approved"] or any(current.get(k) != job.get(k) for k in ("hash", "type", "media")):
                return False
            key, title, folder, planned = self.plan(current, torrent, groups)
            actual = legacy.hash_value(torrent.get("hash"))
            peers = self.get()
            if any(p["id"] != job["id"] and p["approved"] and p.get("hash") == actual and p["state"] == "published" for p in peers):
                self.update(job["id"], "duplicate", "The same content is already published")
                return False
            old = json.loads(self.db.execute("SELECT links FROM approvals WHERE id=?", (job["id"],)).fetchone()[0])
            created = []
            try:
                for link, target in planned:
                    # resolve parent only: final symlinks intentionally point outside output.
                    if self.output not in link.parent.resolve().parents:
                        raise ValueError("Output path escaped its root")
                    link.parent.mkdir(parents=True, exist_ok=True)
                    if link.is_symlink():
                        if os.readlink(link) != target or str(link) not in old:
                            raise ValueError("Destination collision")
                    elif link.exists():
                        raise ValueError("Destination collision")
                    else:
                        link.symlink_to(target)
                        created.append(str(link))
                links = [str(p) for p, target in planned]
                # A committed plan has complete links before old managed links go away.
                # Keep a recovery journal so interruption can finish deterministically.
                self.db.execute("CREATE TABLE IF NOT EXISTS layout_journal (id TEXT PRIMARY KEY, old_links TEXT NOT NULL, new_links TEXT NOT NULL)")
                targets = {str(path): target for path, target in planned}
                self.db.execute("INSERT OR REPLACE INTO layout_journal VALUES(?,?,?)", (job["id"], json.dumps(old), json.dumps(targets)))
                self.db.commit()
                self.unlink_owned([x for x in old if x not in links])
                current["hash"] = actual
                current["type"] = media_kind(current, torrent)
                self.db.execute("UPDATE approvals SET body=?,links=?,state='published',note='Organized library; verified source files' WHERE id=?", (json.dumps(current), json.dumps(links), job["id"]))
                self.db.execute("INSERT OR IGNORE INTO media_folders VALUES(?,?,?)", (key, title, folder))
                self.db.execute("DELETE FROM layout_journal WHERE id=?", (job["id"],))
                self.db.commit()
            except Exception:
                # If a journal exists, preserve new links for recovery rather than
                # removing the last complete layout after an interrupted migration.
                journal = self.db.execute("SELECT name FROM sqlite_master WHERE name='layout_journal'").fetchone()
                pending = journal and self.db.execute("SELECT 1 FROM layout_journal WHERE id=?", (job["id"],)).fetchone()
                if not pending:
                    self.unlink_owned(created)
                raise
            for raw in old:
                parent = Path(raw).parent
                while parent != self.output and self.output in parent.parents:
                    try:
                        parent.rmdir()
                    except OSError:
                        break
                    parent = parent.parent
            return set(old) != set(links)

    def recover_layout(self):
        with self.lock:
            self.db.execute("CREATE TABLE IF NOT EXISTS layout_journal (id TEXT PRIMARY KEY, old_links TEXT NOT NULL, new_links TEXT NOT NULL)")
            for identity_, old_raw, new_raw in self.db.execute("SELECT * FROM layout_journal").fetchall():
                job = self.get(identity_)
                targets = json.loads(new_raw)
                if not isinstance(targets, dict):
                    raise ValueError("Invalid layout recovery journal")
                new = list(targets)
                if not job or not job["approved"]:
                    self.unlink_owned(new)
                elif all(Path(p).is_symlink() and os.readlink(p) == targets[p] for p in new):
                    self.unlink_owned([p for p in json.loads(old_raw) if p not in new])
                    self.db.execute("UPDATE approvals SET links=?,state='published' WHERE id=?", (json.dumps(new), identity_))
                else:
                    raise ValueError("Interrupted layout requires recovery; existing links retained")
                self.db.execute("DELETE FROM layout_journal WHERE id=?", (identity_,))
            self.db.commit()
