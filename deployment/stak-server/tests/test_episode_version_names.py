"""Regression against Jellyfin's actual SxxEyy multi-episode expressions.

Source: jellyfin/Emby.Naming/Common/NamingOptions.cs MultipleEpisodeExpressions
and TV/EpisodePathParser.cs ending-number guard (official upstream).
Only the .NET named-group syntax is adapted to Python. No media/network access.
"""
import json
import os
import re
import pytest
import filter_server as legacy
from test_unified import organized, add_media


PATTERNS = [
    r".*(\\|\/)(?P<seriesname>[^\\\/]*)[sS](?P<seasonnumber>[0-9]{1,4})[xX\.]?[eE](?P<epnumber>[0-9]{1,3})((-| - )?[xXeE](?P<endingepnumber>[0-9]{1,3}))+[^\\\/]*$",
    r".*(\\|\/)(?P<seriesname>[^\\\/]*)[sS](?P<seasonnumber>[0-9]{1,4})[xX\.]?[eE](?P<epnumber>[0-9]{1,3})(-[xX]?[eE]?(?P<endingepnumber>[0-9]{1,3}))+[^\\\/]*$",
]


def jellyfin_ending(path):
    for expression in PATTERNS:
        match = re.match(expression, path)
        if not match:
            continue
        end = int(match['endingepnumber'])
        following = path[match.end('endingepnumber'):match.end('endingepnumber')+1]
        if (not following or following not in '0123456789iIpP') and end >= int(match['epnumber']):
            return end
    return None


@pytest.mark.parametrize('episode', range(1, 9))
def test_owner_old_e5_hash_reproduces_exact_false_range(episode):
    name=f'/media/shows/The Leftovers/Season 03/The Leftovers S03E{episode:02d} - e5dbdc39 1080p.mkv'
    assert jellyfin_ending(name) == (5 if episode <= 5 else None)


@pytest.mark.parametrize('hash_prefix', ['e5dbdc39','e123abcd','E02abcde','5abc1234','a5d92294','f7e70c2d','ee012abc','00000005'])
@pytest.mark.parametrize('episode', [1,2,5,6,8])
def test_version_tags_never_extend_a_single_episode(organized, hash_prefix, episode):
    job,torrent=add_media(organized,'release',[f'The.Leftovers.S03E{episode:02d}.1080p.mkv'],h=hash_prefix.lower()+'a'*32,
                          media={'title':'The Leftovers','year':2014,'imdb':'tt2699128'})
    planned=organized.plan(job,torrent,legacy.index_source(organized.source))[3]
    assert len(planned)==1
    path=planned[0][0]
    assert '[stak-' in path.name and path.name.endswith('1080p].mkv')
    assert jellyfin_ending(str(path)) is None


def test_real_multi_episode_range_is_still_detected(organized):
    job,torrent=add_media(organized,'release',['Show.S03E01-E02.1080p.mkv'],h='e5dbdc39'+'a'*32)
    path=organized.plan(job,torrent,legacy.index_source(organized.source))[3][0][0]
    assert jellyfin_ending(str(path))==2


def install_old_links(lib,job,torrent):
    """Install exactly the owned 1.2.2 names, including matching subtitles."""
    key,title,folder,planned=lib.plan(job,torrent,legacy.index_source(lib.source))
    links=[]
    for path,target in planned:
        name=re.sub(r' \[stak-([^\]]+)\]',r' - \1',path.name)
        old=path.with_name(name);old.parent.mkdir(parents=True,exist_ok=True);old.symlink_to(target);links.append(str(old))
    lib.db.execute('UPDATE approvals SET links=?,state="published" WHERE id=?',(json.dumps(links),job['id']))
    lib.db.execute('INSERT OR IGNORE INTO media_folders VALUES(?,?,?)',(key,title,folder));lib.db.commit()
    return links


def test_existing_owned_links_and_subtitles_migrate_once_without_touching_sources(organized):
    name='Show.S03E01.1080p.mkv'
    job,torrent=add_media(organized,'release',[name],h='e5dbdc39'+'a'*32)
    source=organized.source/'release'/name;before=source.read_bytes()
    subtitle=source.with_suffix('.en.srt');subtitle.write_text('fixture subtitle')
    old=install_old_links(organized,job,torrent)
    assert organized.publish(organized.get('1'),torrent,legacy.index_source(organized.source)) is True
    assert all(not os.path.lexists(path) for path in old)
    assert source.read_bytes()==before and subtitle.read_text()=='fixture subtitle'
    assert len(list(organized.output.rglob('*.mkv')))==1 and len(list(organized.output.rglob('*.srt')))==1
    assert organized.publish(organized.get('1'),torrent,legacy.index_source(organized.source)) is False


def test_new_destination_collision_retains_all_old_links(organized):
    job,torrent=add_media(organized,'release',['Show.S03E01.mkv'],h='e5dbdc39'+'a'*32)
    old=install_old_links(organized,job,torrent)
    destination=organized.plan(organized.get('1'),torrent,legacy.index_source(organized.source))[3][0][0]
    destination.write_text('unmanaged fixture')
    with pytest.raises(ValueError):organized.publish(organized.get('1'),torrent,legacy.index_source(organized.source))
    assert all(os.path.islink(path) for path in old)
    assert destination.read_text()=='unmanaged fixture'


def test_rename_interruption_recovers_from_the_existing_journal(organized,monkeypatch):
    job,torrent=add_media(organized,'release',['Show.S03E01.mkv'],h='e5dbdc39'+'a'*32)
    old=install_old_links(organized,job,torrent);unlink=organized.unlink_owned
    monkeypatch.setattr(organized,'unlink_owned',lambda _: (_ for _ in ()).throw(OSError('fixture interruption')))
    with pytest.raises(OSError):organized.publish(organized.get('1'),torrent,legacy.index_source(organized.source))
    assert all(os.path.islink(path) for path in old)
    monkeypatch.setattr(organized,'unlink_owned',unlink);organized.recover_layout()
    assert all(not os.path.lexists(path) for path in old)
    assert len(list(organized.output.rglob('*.mkv')))==1


def test_movie_filename_is_unchanged(organized):
    job,torrent=add_media(organized,'release',['Film.1080p.mkv'],h='e5dbdc39'+'a'*32,kind='movie')
    path=organized.plan(job,torrent,legacy.index_source(organized.source))[3][0][0]
    assert path.name.endswith(' - e5dbdc39 1080p.mkv')
