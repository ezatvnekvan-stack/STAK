"""Add decision logic: dedup, no blind re-add, no quality swap, reconciliation."""

import pytest

from core.dedup import AddLedger, EpisodeState, TorrentRecord


def ledger_with_episode(episode_key="tmdb:1:S01E02", info_hash="a" * 40, state="added"):
    l = AddLedger()
    l.mark_added(episode_key, info_hash, "torrentio:guid-1")
    if state != "added":
        l.torrents[info_hash].state = state
    return l


def test_same_torrent_not_added_again():
    l = ledger_with_episode()
    action, _ = l.decide_add("tmdb:1:S01E02", "A" * 40, "torrentio:guid-1", "missing")
    assert action == "already_added"


def test_episode_owns_torrent_never_quality_swap():
    l = ledger_with_episode()
    action, detail = l.decide_add("tmdb:1:S01E02", "b" * 40, "torrentio:guid-2", "missing")
    assert action == "identical_state"
    assert "quality swap" in detail


def test_unknown_episode_adds_fresh():
    l = AddLedger()
    action, _ = l.decide_add("tmdb:1:S01E03", "c" * 40, "torrentio:guid-3", "missing")
    assert action == "add"


def test_provider_unrelated_torrent_forces_reconciliation():
    """Unrelated torrent on the provider: never blindly re-add; reconcile first."""
    l = AddLedger()
    action, detail = l.decide_add("tmdb:1:S01E02", "d" * 40, "guid", "unrelated")
    assert action == "reconcile_first"
    assert "unrelated" in detail


def test_provider_ownership_mismatch_forces_reconciliation():
    l = AddLedger()
    action, detail = l.decide_add("tmdb:1:S01E02", "e" * 40, "guid", "mine")
    assert action == "reconcile_first"  # provider says it IS ours but ledger has nothing


def test_ledger_record_with_missing_provider_reconciles():
    """Ledger has record, provider confirms nothing: reconcile before re-add."""
    l = AddLedger()
    l.mark_unconfirmed("tmdb:1:S01E02", "f" * 40, "guid")
    action, detail = l.decide_add("tmdb:1:S01E02", "f" * 40, "guid", "missing")
    assert action == "reconcile_first"


def test_reconcile_confirms_and_rolls_back():
    l = AddLedger()
    ep_key = "tmdb:1:S01E02"
    l.mark_unconfirmed(ep_key, "1" * 40, "guid-1")
    l.mark_unconfirmed("tmdb:1:S01E03", "2" * 40, "guid-2")
    results = l.reconcile({"1" * 40})
    mapping = dict(results)
    assert mapping["1" * 40] == "confirmed_added"
    assert mapping["2" * 40] == "rolled_back"
    assert l.episodes[ep_key].torrent_hash == "1" * 40
    assert "2" * 40 not in l.torrents
    # rolled-back episode becomes handle-able again without duplicate guard
    action, _ = l.decide_add("tmdb:1:S01E03", "2" * 40, "guid-2", "missing")
    assert action == "add"


def test_reconcile_does_not_steal_existing_episode_torrent():
    """Reconcile confirming an unconfirmed torrent never overrides an episode
    that already owns a different torrent."""
    l = ledger_with_episode()  # S01E02 owns aaaa...
    l.torrents["9" * 40] = TorrentRecord("9" * 40, "guid", "tmdb:1:S01E02", 1.0, "unconfirmed")
    l.reconcile({"9" * 40})
    assert l.episodes["tmdb:1:S01E02"].torrent_hash == "a" * 40


def test_restarts_reproduce_identical_decisions():
    """Deterministic across 'restarts' (re-instantiated ledger from same rows)."""
    rows_t = {"a" * 40: TorrentRecord("a" * 40, "guid-1", "tmdb:1:S01E02", 1.0)}
    rows_e = {"tmdb:1:S01E02": EpisodeState("tmdb:1:S01E02", "a" * 40, "guid-1")}
    one = AddLedger(dict(rows_t), dict(rows_e))
    two = AddLedger(dict(rows_t), dict(rows_e))
    assert one.decide_add("tmdb:1:S01E02", "a" * 40, "guid-1", "missing") == \
        two.decide_add("tmdb:1:S01E02", "a" * 40, "guid-1", "missing")


def test_ledger_rejects_double_marking():
    l = ledger_with_episode()
    with pytest.raises(ValueError):
        l.mark_added("tmdb:1:S01E02", "b" * 40, "guid-2")


def test_unknown_provider_state_rejected():
    l = AddLedger()
    with pytest.raises(ValueError):
        l.decide_add("tmdb:1:S01E02", "c" * 40, "guid", "maybe?")


def test_unconfirmed_timeout_then_reconcile_timeout_after_add():
    """Cache disappeared right after add request (timeout + unconfirmed): the
    next run reconciles and rolls back, allowing a later fresh handling."""
    l = AddLedger()
    ep_key = "tmdb:1:S01E02"
    l.mark_unconfirmed(ep_key, "7" * 40, "guid")
    # next provider cycle confirms the torrent is NOT in the account
    assert dict(l.reconcile(set()))["7" * 40] == "rolled_back"
    assert l.has_episode_torrent(ep_key) is None
    # and a later verified add is still allowed once
    action, _ = l.decide_add(ep_key, "7" * 40, "guid", "missing")
    assert action == "add"
