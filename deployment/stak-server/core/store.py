"""Persistent SQLite store for rules, episodes, torrents and search negative cache.

Single connection + RLock: the pipeline worker and the HTTP API serialize on
the same database; WAL + FULL synchronous for crash resilience across restarts.
"""

from __future__ import annotations

import json
import hashlib
from .security import Redactor
import sqlite3
import threading
import time
from pathlib import Path

_UNCHANGED = object()

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (
  key TEXT PRIMARY KEY, value TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS rules (
  rule_id TEXT PRIMARY KEY,
  payload TEXT NOT NULL,
  updated_at REAL NOT NULL,
  enabled INTEGER NOT NULL DEFAULT 1,
  paused INTEGER NOT NULL DEFAULT 0,
  confirmed INTEGER NOT NULL DEFAULT 0);

CREATE TABLE IF NOT EXISTS episodes (
  episode_key TEXT PRIMARY KEY,
  rule_id TEXT NOT NULL,
  season INTEGER NOT NULL,
  episode_number INTEGER NOT NULL,
  air_date TEXT,
  status TEXT NOT NULL,
  status_detail TEXT,
  next_check REAL NOT NULL DEFAULT 0,
  last_check REAL,
  attempts INTEGER NOT NULL DEFAULT 0,
  torrent_hash TEXT,
  torrent_identity TEXT,
  torbox_id TEXT,
  added_at REAL,
  filter_state TEXT,
  jellyfin_verified INTEGER NOT NULL DEFAULT 0,
  series_jellyfin TEXT,
  UNIQUE(rule_id, season, episode_number));

CREATE TABLE IF NOT EXISTS torrents (
  info_hash TEXT PRIMARY KEY,
  episode_key TEXT NOT NULL,
  source_identity TEXT NOT NULL,
  torbox_id TEXT,
  state TEXT NOT NULL,            -- added | unconfirmed | rolled_back | otherwise-state
  added_at REAL NOT NULL);

CREATE TABLE IF NOT EXISTS search_cache (
  provider TEXT NOT NULL,
  episode_key TEXT NOT NULL,
  negative INTEGER NOT NULL,
  hashes TEXT,
  checked_at REAL NOT NULL,
  PRIMARY KEY(provider, episode_key));

CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at REAL NOT NULL,
  episode_key TEXT,
  kind TEXT NOT NULL,
  detail TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS metadata_jobs (
  rule_id TEXT PRIMARY KEY, version TEXT NOT NULL, due REAL NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0, last_success REAL, last_error TEXT);
CREATE INDEX IF NOT EXISTS metadata_due ON metadata_jobs(due);

CREATE INDEX IF NOT EXISTS episodes_next ON episodes(next_check);
CREATE INDEX IF NOT EXISTS episodes_rule ON episodes(rule_id);
"""


def now() -> float:
    return time.time()


_COLS = ("episode_key rule_id season episode_number air_date status status_detail "
         "next_check last_check attempts torrent_hash torrent_identity torbox_id "
         "added_at filter_state jellyfin_verified series_jellyfin").split()


class Store:
    def __init__(self, path: str, redact=None):
        self.redact = redact or Redactor()
        self._lock = threading.RLock()
        self._conn = sqlite3.connect(path, check_same_thread=False, timeout=5)
        self._conn.row_factory = sqlite3.Row
        with self._lock:
            self._conn.execute("PRAGMA journal_mode=WAL")
            self._conn.execute("PRAGMA synchronous=FULL")
            self._conn.executescript(SCHEMA)
            # migration: older databases may lack the Jellyfin series column
            existing = {r["name"] for r in self._conn.execute("PRAGMA table_info(episodes)")}
            if "series_jellyfin" not in existing:
                self._conn.execute("ALTER TABLE episodes ADD COLUMN series_jellyfin TEXT")
            columns={r['name'] for r in self._conn.execute('PRAGMA table_info(rules)')}
            migrated=False
            for name,default in [('enabled',1),('paused',0),('confirmed',0)]:
                if name not in columns:
                    self._conn.execute(f'ALTER TABLE rules ADD COLUMN {name} INTEGER NOT NULL DEFAULT {default}')
                    migrated=True
            if migrated:
                for row in self._conn.execute('SELECT rule_id,payload FROM rules').fetchall():
                    payload=json.loads(row['payload'])
                    self._conn.execute('UPDATE rules SET enabled=?,paused=?,confirmed=? WHERE rule_id=?',
                        (int(bool(payload.get('enabled',True))),int(bool(payload.get('paused'))),int(payload.get('confirmed') is True),row['rule_id']))
            self._conn.execute('CREATE INDEX IF NOT EXISTS rules_active ON rules(enabled,paused,confirmed)')
            self._conn.commit()

    # --- events ----------------------------------------------------------
    def event(self, kind: str, detail: str, episode_key: str | None = None) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT INTO events(at, episode_key, kind, detail) VALUES(?,?,?,?)",
                (now(), episode_key, kind, self.redact(detail)))
            if self._conn.execute("SELECT last_insert_rowid()").fetchone()[0] % 100 == 0:
                self._conn.execute("DELETE FROM events WHERE id < (SELECT COALESCE(MAX(id),0)-10000 FROM events)")
            self._conn.commit()

    def recent_events(self, limit: int = 200) -> list[dict]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT at, episode_key, kind, detail FROM events ORDER BY id DESC LIMIT ?",
                (limit,)).fetchall()
        return [{"at": r["at"], "episode": r["episode_key"], "kind": r["kind"], "detail": r["detail"]}
                for r in rows]

    # --- rules -----------------------------------------------------------
    def put_rule(self, rule_id: str, payload: dict, queue_metadata: bool = False) -> None:
        with self._lock, self._conn:
            encoded = json.dumps(payload, sort_keys=True)
            previous = self._conn.execute("SELECT payload FROM rules WHERE rule_id=?", (rule_id,)).fetchone()
            self._conn.execute(
                "INSERT INTO rules(rule_id, payload, updated_at,enabled,paused,confirmed) VALUES(?,?,?,?,?,?) "
                "ON CONFLICT(rule_id) DO UPDATE SET payload=excluded.payload,"
                " updated_at=excluded.updated_at,enabled=excluded.enabled,paused=excluded.paused,confirmed=excluded.confirmed",
                (rule_id, encoded, now(),int(bool(payload.get('enabled',True))),int(bool(payload.get('paused'))),int(payload.get('confirmed') is True)))
            if payload.get('paused') or not payload.get('enabled',True):
                self._conn.execute("DELETE FROM metadata_jobs WHERE rule_id=?",(rule_id,))
            job=self._conn.execute("SELECT version FROM metadata_jobs WHERE rule_id=?",(rule_id,)).fetchone()
            if queue_metadata and (previous is None or previous[0] != encoded or job is None or job[0]!=self.version(payload)):
                self._conn.execute("INSERT INTO metadata_jobs(rule_id,version,due) VALUES(?,?,?) ON CONFLICT(rule_id) DO UPDATE SET version=excluded.version,due=excluded.due,attempts=0,last_error=NULL", (rule_id,self.version(payload),now()))
                self._conn.execute("DELETE FROM search_cache WHERE episode_key IN (SELECT episode_key FROM episodes WHERE rule_id=?)", (rule_id,))
            self._conn.commit()

    def delete_rule(self, rule_id: str) -> None:
        """Removes the rule only; episode/torrent history is preserved."""
        with self._lock, self._conn:
            self._conn.execute("DELETE FROM rules WHERE rule_id=?", (rule_id,))
            self._conn.execute("DELETE FROM metadata_jobs WHERE rule_id=?", (rule_id,))
            self._conn.execute(
                "UPDATE episodes SET status='paused', "
                "status_detail='rule removed (history kept)' WHERE rule_id=?"
                " AND status<>'available_jellyfin'", (rule_id,))
            self._conn.commit()

    def rules(self) -> list[dict]:
        with self._lock:
            rows = self._conn.execute("SELECT payload FROM rules ORDER BY rule_id").fetchall()
        return [json.loads(r["payload"]) for r in rows]

    # --- episodes --------------------------------------------------------
    def upsert_episode(self, episode_key: str, rule_id: str, season: int, number: int,
                       air_date: str | None) -> None:
        """Create or refresh an episode row; never resets an existing status."""
        with self._lock:
            existing = self._conn.execute(
                "SELECT episode_key FROM episodes WHERE episode_key=?", (episode_key,)).fetchone()
            if existing:
                self._conn.execute("UPDATE episodes SET air_date=?, rule_id=?, season=?,"
                                   " episode_number=? WHERE episode_key=?",
                                   (air_date, rule_id, season, number, episode_key))
            else:
                self._conn.execute(
                    "INSERT INTO episodes(episode_key, rule_id, season, episode_number, air_date,"
                    " status, status_detail, next_check) VALUES(?,?,?,?,?,?,?,?)",
                    (episode_key, rule_id, season, number, air_date,
                     "waiting_release", "no check scheduled yet", 0.0))
            self._conn.commit()

    def episode(self, episode_key: str) -> dict | None:
        with self._lock:
            row = self._conn.execute("SELECT * FROM episodes WHERE episode_key=?",
                                     (episode_key,)).fetchone()
        return self._row(row) if row else None

    def episodes_for_rule(self, rule_id: str) -> list[dict]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT * FROM episodes WHERE rule_id=? ORDER BY season, episode_number",
                (rule_id,)).fetchall()
        return [self._row(r) for r in rows]

    def due_episodes(self, limit: int = 20) -> list[dict]:
        with self._lock:
            rows = self._conn.execute(
                "SELECT episodes.* FROM episodes JOIN rules ON rules.rule_id=episodes.rule_id WHERE rules.enabled=1 AND rules.paused=0 AND rules.confirmed=1 AND next_check>0 AND next_check<=? AND status NOT IN"
                " ('available_jellyfin','paused') ORDER BY next_check, last_check LIMIT ?",
                (now(), limit)).fetchall()
        return [self._row(r) for r in rows]

    def _row(self, row) -> dict:
        d = dict(row)
        d["jellyfin_verified"] = bool(d["jellyfin_verified"])
        return d

    def set_episode_status(self, episode_key: str, status: str, detail: str = "",
                           next_check=_UNCHANGED, attempts: int | None = None,
                           last_check: bool = False) -> None:
        with self._lock:
            sets = ["status=?", "status_detail=?"]
            args: list = [status, self.redact(detail)[:1000]]
            if next_check is not _UNCHANGED:
                sets.append("next_check=?")
                args.append(0.0 if next_check is None else next_check)
            if attempts is not None:
                sets.append("attempts=?")
                args.append(attempts)
            if last_check:
                sets.append("last_check=?")
                args.append(now())
            args.append(episode_key)
            self._conn.execute(f"UPDATE episodes SET {', '.join(sets)} WHERE episode_key=?", args)
            self._conn.commit()

    def set_episode_torrent(self, episode_key: str, info_hash: str, identity: str,
                            torbox_id, added_at: float) -> None:
        with self._lock:
            self._conn.execute(
                "UPDATE episodes SET torrent_hash=?, torrent_identity=?, torbox_id=?, added_at=?,"
                " status='added_torbox', status_detail='added to TorBox (cached-only)'"
                " WHERE episode_key=?",
                (info_hash, identity, str(torbox_id) if torbox_id is not None else None,
                 added_at, episode_key))
            self._conn.commit()

    def set_filter_state(self, episode_key: str, filter_state: str) -> None:
        with self._lock:
            self._conn.execute("UPDATE episodes SET filter_state=? WHERE episode_key=?",
                               (filter_state, episode_key))
            self._conn.commit()

    def set_series_jellyfin(self, episode_key: str, series_id: str) -> None:
        with self._lock:
            self._conn.execute("UPDATE episodes SET series_jellyfin=? WHERE episode_key=?",
                               (series_id, episode_key))
            self._conn.commit()

    def set_jellyfin_verified(self, episode_key: str, verified: bool) -> None:
        with self._lock:
            self._conn.execute(
                "UPDATE episodes SET jellyfin_verified=?, status=?, status_detail=? WHERE episode_key=?",
                (1 if verified else 0, "available_jellyfin" if verified else "waiting_library",
                 "verified by Jellyfin index" if verified else "not yet indexed in Jellyfin",
                 episode_key))
            self._conn.commit()

    def clear_library_status(self, episode_key: str) -> None:
        """Mount vanished / filter unmounted: go back to waiting_library without
        touching media, links or the torrent record."""
        with self._lock:
            self._conn.execute(
                "UPDATE episodes SET jellyfin_verified=0, filter_state=NULL, status=?,"
                " status_detail=? WHERE episode_key=?",
                ("waiting_library" if self._episode_has_torrent(episode_key) else "added_torbox",
                 "library verification reset (mount/source was unavailable)",
                 episode_key))
            self._conn.commit()

    def _episode_has_torrent(self, episode_key: str) -> bool:
        row = self._conn.execute("SELECT torbox_id FROM episodes WHERE episode_key=?",
                                 (episode_key,)).fetchone()
        return bool(row and row["torbox_id"])

    # --- torrent ledger persistence --------------------------------------
    def torrent(self, info_hash: str) -> dict | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT info_hash, episode_key, source_identity, torbox_id, state, added_at"
                " FROM torrents WHERE info_hash=?", (info_hash.lower(),)).fetchone()
        if not row:
            return None
        return {"info_hash": row["info_hash"], "episode_key": row["episode_key"],
                "source_identity": row["source_identity"], "torbox_id": row["torbox_id"],
                "state": row["state"], "added_at": row["added_at"]}

    def put_torrent(self, info_hash: str, episode_key: str, identity: str, state: str,
                    torbox_id=None, added_at: float | None = None) -> None:
        if "://" in identity:
            identity = "source:" + hashlib.sha256(identity.encode()).hexdigest()
        with self._lock:
            self._conn.execute(
                "INSERT INTO torrents(info_hash, episode_key, source_identity, torbox_id,"
                " state, added_at) VALUES(?,?,?,?,?,?)"
                " ON CONFLICT(info_hash) DO UPDATE SET state=excluded.state,"
                " torbox_id=excluded.torbox_id",
                (info_hash.lower(), episode_key, identity, str(torbox_id) if torbox_id is not None else None,
                 state, added_at or now()))
            self._conn.commit()

    def torrents_owned(self) -> list[str]:
        """Hashes the ledger recorded as added; reconciliation compares these
        with the actual account to detect vanished/foreign state safely."""
        with self._lock:
            rows = self._conn.execute("SELECT info_hash FROM torrents WHERE state='added'").fetchall()
        return [r["info_hash"] for r in rows]

    def torrents_unconfirmed(self) -> list[str]:
        """Hashes whose add outcome is still unclear; reconcile uses this so
        ambiguous adds are confirmed or rolled back, never blindly retried."""
        with self._lock:
            rows = self._conn.execute(
                "SELECT info_hash FROM torrents WHERE state='unconfirmed'").fetchall()
        return [r["info_hash"] for r in rows]

    # --- negative search cache -------------------------------------------
    def search_cache(self, provider: str, episode_key: str) -> dict | None:
        with self._lock:
            row = self._conn.execute(
                "SELECT negative, hashes, checked_at FROM search_cache"
                " WHERE provider=? AND episode_key=?", (provider, episode_key)).fetchone()
        if not row:
            return None
        return {"negative": bool(row["negative"]), "hashes": json.loads(row["hashes"] or "[]"),
                "checked_at": row["checked_at"]}

    def put_search_cache(self, provider: str, episode_key: str, negative: bool,
                         hashes: list[str]) -> None:
        with self._lock:
            self._conn.execute(
                "INSERT INTO search_cache(provider, episode_key, negative, hashes, checked_at)"
                " VALUES(?,?,?,?,?) ON CONFLICT(provider, episode_key) DO UPDATE SET"
                " negative=excluded.negative, hashes=excluded.hashes, checked_at=excluded.checked_at",
                (provider, episode_key, 1 if negative else 0,
                 json.dumps([h.lower() for h in hashes]), now()))
            self._conn.commit()

    def stale_search_cache_hashes(self, before: float) -> list[tuple[str, str]]:
        """(provider, episode_key) pairs whose negative result is older than
        `before`; a stale negative entry may be rechecked (an old negative
        cache result never blocks an episode forever)."""
        with self._lock:
            rows = self._conn.execute(
                "SELECT provider, episode_key FROM search_cache WHERE negative=1 AND checked_at<?",
                (before,)).fetchall()
        return [(r["provider"], r["episode_key"]) for r in rows]


    @staticmethod
    def version(payload):
        return hashlib.sha256(json.dumps(payload,sort_keys=True).encode()).hexdigest()

    def rule(self, rule_id):
        with self._lock:
            row=self._conn.execute("SELECT payload FROM rules WHERE rule_id=?",(rule_id,)).fetchone()
            return json.loads(row[0]) if row else None

    def ensure_metadata_jobs(self):
        with self._lock, self._conn:
            for payload in self.rules():
                if payload.get('enabled') and not payload.get('paused'):
                    self._conn.execute("INSERT OR IGNORE INTO metadata_jobs(rule_id,version,due) VALUES(?,?,?)",(payload['ruleId'],self.version(payload),now()))
            self._conn.commit()

    def due_metadata(self, limit=2):
        with self._lock:
            return [dict(r) for r in self._conn.execute("SELECT * FROM metadata_jobs WHERE due<=? ORDER BY due LIMIT ?",(now(),limit)).fetchall()]

    def finish_metadata(self, rule_id, version, error=None):
        with self._lock, self._conn:
            job=self._conn.execute("SELECT * FROM metadata_jobs WHERE rule_id=? AND version=?",(rule_id,version)).fetchone()
            if not job:return
            attempts=job['attempts']+1 if error else 0
            delay=min(60*2**min(attempts-1,5),1800) if error else 3600
            self._conn.execute("UPDATE metadata_jobs SET due=?,attempts=?,last_success=?,last_error=? WHERE rule_id=? AND version=?",(now()+delay,attempts,job['last_success'] if error else now(),self.redact(error) if error else None,rule_id,version))
            self._conn.commit()

    def metadata_state(self, rule_id):
        with self._lock:
            r=self._conn.execute("SELECT attempts,last_success,last_error,due FROM metadata_jobs WHERE rule_id=?",(rule_id,)).fetchone()
            return dict(r) if r else None

    def set_meta(self, key, value):
        with self._lock:
            self._conn.execute("INSERT INTO meta(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",(key,str(value)))
            self._conn.commit()

    def get_meta(self, key, default=None):
        with self._lock:
            row=self._conn.execute("SELECT value FROM meta WHERE key=?",(key,)).fetchone()
            return row[0] if row else default

    def attach_owned(self, info_hash, episode_key, torbox_id):
        """Atomically attach a known added hash; never import unknown account items."""
        with self._lock,self._conn:
            rec=self._conn.execute("SELECT * FROM torrents WHERE info_hash=? AND state='added'",(info_hash,)).fetchone()
            ep=self._conn.execute("SELECT torrent_hash FROM episodes WHERE episode_key=?",(episode_key,)).fetchone()
            if not rec or not ep or (ep['torrent_hash'] and ep['torrent_hash']!=info_hash):return False
            if rec['torbox_id'] is not None and str(rec['torbox_id'])!=str(torbox_id):return False
            self._conn.execute("UPDATE torrents SET torbox_id=? WHERE info_hash=?",(str(torbox_id),info_hash))
            self._conn.execute("UPDATE episodes SET torrent_hash=?,torrent_identity=?,torbox_id=?,added_at=?,status='added_torbox',status_detail='Reused existing watcher-owned torrent',next_check=? WHERE episode_key=?",(info_hash,rec['source_identity'],str(torbox_id),rec['added_at'],now(),episode_key))
            return True

    def reserve_add(self, info_hash, episode_key, identity):
        # Durable BEFORE network mutation: a crash can never turn it into a
        # fresh unrecorded add on restart. One reservation per episode as well.
        if '://' in identity:identity='source:'+hashlib.sha256(identity.encode()).hexdigest()
        with self._lock, self._conn:
            row=self._conn.execute("SELECT 1 FROM torrents WHERE (info_hash=? OR episode_key=?) AND state IN ('added','unconfirmed')",(info_hash,episode_key)).fetchone()
            if row:return False
            self._conn.execute("INSERT INTO torrents(info_hash,episode_key,source_identity,state,added_at) VALUES(?,?,?,'unconfirmed',?) ON CONFLICT(info_hash) DO UPDATE SET episode_key=excluded.episode_key,source_identity=excluded.source_identity,state='unconfirmed',torbox_id=NULL,added_at=excluded.added_at",(info_hash,episode_key,identity,now()))
            self._conn.execute("DELETE FROM meta WHERE key=?",('absent:'+info_hash,))
            self._conn.commit();return True

    def reset_rule_schedule(self, rule_id):
        with self._lock:
            self._conn.execute("UPDATE episodes SET next_check=? WHERE rule_id=? AND status NOT IN ('available_jellyfin','paused')",(now(),rule_id))
            self._conn.commit()

    def checkpoint(self):
        with self._lock:
            self._conn.execute('DELETE FROM search_cache WHERE checked_at<?',(now()-7*86400,))
            self._conn.commit()
            self._conn.execute('PRAGMA wal_checkpoint(PASSIVE)')

    def close(self):
        with self._lock:self._conn.close()

    def episode_counts(self):
        with self._lock:
            rows=self._conn.execute("SELECT rule_id,status,COUNT(*) AS n FROM episodes GROUP BY rule_id,status").fetchall()
            out={}
            for r in rows:out.setdefault(r['rule_id'],{})[r['status']]=r['n']
            return out

    def all_episodes(self, limit=10000):
        with self._lock:
            return [self._row(r) for r in self._conn.execute("SELECT episodes.* FROM episodes JOIN rules ON rules.rule_id=episodes.rule_id ORDER BY episodes.rule_id,season,episode_number LIMIT ?",(limit,)).fetchall()]

    def episode_reservation(self, key):
        with self._lock:
            r=self._conn.execute("SELECT * FROM torrents WHERE episode_key=? AND state IN ('added','unconfirmed') ORDER BY added_at DESC LIMIT 1",(key,)).fetchone()
            return dict(r) if r else None

    def apply_episode_refs(self, rule, refs):
        from .episode import EpisodeContext,episode_wanted,parse_air_date
        from datetime import date
        started=parse_air_date(rule.created_on) or date(1970,1,1)
        wanted=[];stamp=now()
        for ref in refs:
            season,number=ref['season'],ref['episode']
            if not isinstance(season,int) or not isinstance(number,int) or not 0<=season<=200 or not 0<number<=10000:continue
            context=EpisodeContext(rule.rule_id,season,number,parse_air_date(ref.get('air_date')),started)
            if episode_wanted(rule,context):wanted.append((f'{rule.rule_id}:S{season:02d}E{number:03d}',rule.rule_id,season,number,ref.get('air_date'),stamp))
        with self._lock,self._conn:
            self._conn.executemany("INSERT INTO episodes(episode_key,rule_id,season,episode_number,air_date,status,status_detail,next_check) VALUES(?,?,?,?,?,'waiting_release','Waiting for release check',?) ON CONFLICT(episode_key) DO UPDATE SET air_date=excluded.air_date, next_check=CASE WHEN episodes.status='paused' OR episodes.next_check=0 THEN excluded.next_check ELSE episodes.next_check END, status=CASE WHEN episodes.status='paused' THEN 'waiting_release' ELSE episodes.status END",wanted)
            paused=[]
            for row in self.episodes_for_rule(rule.rule_id):
                context=EpisodeContext(rule.rule_id,row['season'],row['episode_number'],parse_air_date(row['air_date']),started)
                if not episode_wanted(rule,context) and row['status']!='available_jellyfin':paused.append((row['episode_key'],))
            self._conn.executemany("UPDATE episodes SET status='paused',status_detail='Outside the selected episode scope',next_check=0 WHERE episode_key=?",paused)
        return len(wanted)

    def check_now(self,rule_id):
        with self._lock,self._conn:
            self._conn.execute('UPDATE metadata_jobs SET due=? WHERE rule_id=?',(now(),rule_id))
            self._conn.execute('DELETE FROM search_cache WHERE episode_key IN (SELECT episode_key FROM episodes WHERE rule_id=?)',(rule_id,))
            self._conn.execute("UPDATE episodes SET next_check=? WHERE rule_id=? AND status NOT IN ('paused','available_jellyfin')",(now(),rule_id))
