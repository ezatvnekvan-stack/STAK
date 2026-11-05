"""Watch decision deduplication: stable identities for torrents + episodes.

Pure decision core of "never add the same episode again across concurrent
runs, restarts, or after a timeout reconciliation". The storage layer plugs
an add-coordinator callback into `AddLedger`.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field


@dataclass
class TorrentRecord:
    """One torrent identity ever added (or attempted) for an episode."""

    info_hash: str          # lowercase 40-hex
    source_identity: str    # provider identity (indexer + guid) actually used
    episode_key: str
    added_at: float
    state: str = "added"    # added | unconfirmed | reverted


@dataclass
class EpisodeState:
    """Episode-level guard: once a torrent belongs here, no new decisions."""

    episode_key: str
    torrent_hash: str | None = None
    added_identity: str | None = None
    decisions: list[str] = field(default_factory=list)


class AddLedger:
    """Deterministic add ledger with provider-state reconciliation-first rules.

    The actual persistence is external (SQLite). This object applies the
    decision LOGIC: on ambiguity, reconcile provider state instead of blindly
    re-adding; on known added episode, never re-add (no quality swap in v1).
    """

    def __init__(self, torrents: dict[str, TorrentRecord] | None = None,
                 episodes: dict[str, EpisodeState] | None = None):
        self.torrents: dict[str, TorrentRecord] = dict(torrents or {})
        self.episodes: dict[str, EpisodeState] = {k: v for k, v in (episodes or {}).items()}

    def has_episode_torrent(self, episode_key: str) -> str | None:
        added = self.episodes.get(episode_key)
        return added.torrent_hash if added else None

    def has_torrent(self, info_hash: str) -> TorrentRecord | None:
        return self.torrents.get(info_hash.lower())

    def decide_add(self, episode_key: str, info_hash: str, source_identity: str,
                   provider_state: str) -> tuple[str, str]:
        """Decide the next action for (episode, torrent).

        provider_state is the reconciled TorBox-side state for this torrent:
          "mine"      — torrent exists in the TorBox account
          "missing"   — provider confirms nothing exists
          "unrelated" — torrent exists but belongs to another source/app

        Returns (action, detail) where action is one of:
          already_added   — this exact torrent is already the episode's torrent
          identical_state — episode already has some torrent (no quality swap)
          reuse           — add reconciliation: reuse the existing torrent
          reconcile_first — ambiguous provider answer, reconcile before any add
          add             — fresh, verified add

        The ledger NEVER answers "add" for a torrent that is already the
        episode's identity, and NEVER while provider state is ambiguous.
        """
        info_hash = info_hash.lower()
        episode = self.episodes.get(episode_key)
        if episode and episode.torrent_hash == info_hash:
            return "already_added", "same torrent already owns this episode"
        if episode and episode.torrent_hash is not None:
            return "identical_state", (
                f"episode already has torrent {episode.torrent_hash}; no automatic quality swap in v1")
        record = self.torrents.get(info_hash)
        if provider_state == "unrelated":
            return "reconcile_first", "torbox has an unrelated torrent with this hash; reconcile before any add"
        if provider_state == "mine":
            if record:
                return "reuse", "torbox already has this torrent; reuse the known identity"
            return "reconcile_first", "torbox reports ownership, ledger does not; reconcile before any add"
        if provider_state == "missing":
            if record:
                return "reconcile_first", "ledger has a record but torbox confirms nothing; reconcile before any add"
            return "add", "confirmed not present either side: safe fresh add"
        raise ValueError(f"unknown provider_state: {provider_state!r}")

    def mark_added(self, episode_key: str, info_hash: str, source_identity: str,
                   at: float | None = None) -> TorrentRecord:
        info_hash = info_hash.lower()
        existing = self.episodes.get(episode_key)
        if existing and existing.torrent_hash:
            raise ValueError(f"episode {episode_key} already owns torrent {existing.torrent_hash}")
        if not info_hash:
            raise ValueError("empty torrent hash")
        record = TorrentRecord(
            info_hash=info_hash,
            source_identity=source_identity,
            episode_key=episode_key,
            added_at=at or time.time(),
        )
        self.torrents[info_hash] = record
        self.episodes[episode_key] = EpisodeState(
            episode_key=episode_key, torrent_hash=info_hash, added_identity=source_identity)
        if source_identity not in self.episodes[episode_key].decisions:
            self.episodes[episode_key].decisions.append(source_identity)
        return record

    def mark_unconfirmed(self, episode_key: str, info_hash: str, source_identity: str) -> None:
        """Timeout / unclear consequence of an add request: keep the identity
        in the ledger as 'unconfirmed' so restarts reconcile, never blind-add."""
        info_hash = info_hash.lower()
        record = self.torrents.get(info_hash)
        if record:
            record.state = "unconfirmed"
            return
        self.torrents[info_hash] = TorrentRecord(
            info_hash=info_hash, source_identity=source_identity,
            episode_key=episode_key, added_at=time.time(), state="unconfirmed")

    def reconcile(self, provider_hashes_now: set[str]) -> list[tuple[str, str]]:
        """Reconcile unconfirmed records against the actual TorBox account.

        Returns a list of (info_hash, result) where result is
        'confirmed_added' (ledger record becomes definitive) or
        'rolled_back' (the add never happened: forget the record, so the
        episode may be handled again later as fresh).
        """
        results: list[tuple[str, str]] = []
        for info_hash, record in list(self.torrents.items()):
            if record.state != "unconfirmed":
                continue
            if info_hash in provider_hashes_now:
                record.state = "added"
                episode = self.episodes.setdefault(
                    record.episode_key, EpisodeState(record.episode_key))
                if episode.torrent_hash is None:
                    episode.torrent_hash = info_hash
                    episode.added_identity = record.source_identity
                results.append((info_hash, "confirmed_added"))
            else:
                del self.torrents[info_hash]
                episode = self.episodes.get(record.episode_key)
                if episode and episode.torrent_hash == info_hash:
                    episode.torrent_hash = None
                    episode.added_identity = None
                results.append((info_hash, "rolled_back"))
        return results
