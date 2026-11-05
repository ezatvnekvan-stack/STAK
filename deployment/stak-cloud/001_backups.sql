-- Run once in the project's SQL editor. No passwords or service keys belong here.
begin;
create schema if not exists stak_private;
revoke all on schema stak_private from public, anon, authenticated;

create table if not exists stak_private.backup_heads (
    user_id uuid primary key references auth.users(id) on delete cascade,
    revision bigint not null default 0 check(revision>=0)
);
create table if not exists stak_private.backup_versions (
    user_id uuid not null references auth.users(id) on delete cascade,
    revision bigint not null check(revision>0),
    operation uuid not null,
    fingerprint text not null check(fingerprint ~ '^[0-9a-f]{64}$'),
    backup jsonb not null check(jsonb_typeof(backup)='object' and octet_length(backup::text)<=6291456),
    saved_at timestamptz not null default clock_timestamp(),
    primary key(user_id,revision), unique(user_id,operation)
);
alter table stak_private.backup_heads enable row level security;
alter table stak_private.backup_versions enable row level security;
revoke all on all tables in schema stak_private from public, anon, authenticated;

create or replace function public.stak_backup_read()
returns jsonb language plpgsql security definer set search_path='' as $$
declare uid uuid := auth.uid(); result jsonb;
begin
    if uid is null then raise insufficient_privilege; end if;
    select jsonb_build_object('revision',v.revision,'fingerprint',v.fingerprint,'backup',v.backup,'saved_at',v.saved_at)
      into result from stak_private.backup_versions v where v.user_id=uid order by v.revision desc limit 1;
    return coalesce(result,jsonb_build_object('revision',0,'fingerprint',null,'backup',null,'saved_at',null));
end; $$;

create or replace function public.stak_backup_write(p_expected_revision bigint,p_fingerprint text,p_backup jsonb,p_operation uuid)
returns jsonb language plpgsql security definer set search_path='' as $$
declare uid uuid := auth.uid(); current_revision bigint; prior stak_private.backup_versions%rowtype; result jsonb;
begin
    if uid is null then raise insufficient_privilege; end if;
    if p_expected_revision is null or p_expected_revision<0 or p_operation is null or p_fingerprint is null or p_fingerprint !~ '^[0-9a-f]{64}$'
       or p_backup is null or jsonb_typeof(p_backup)!='object' or octet_length(p_backup::text)>6291456
       or p_backup->>'format' is distinct from '1'
       or coalesce(p_backup->>'nonce','') !~ '^[A-Za-z0-9+/]{16}$'
       or coalesce(p_backup->>'data','') !~ '^[A-Za-z0-9+/]+={0,2}$'
       or (select count(*) from jsonb_object_keys(p_backup))!=3 then
        raise invalid_parameter_value using message='Invalid backup';
    end if;
    insert into stak_private.backup_heads(user_id) values(uid) on conflict do nothing;
    select h.revision into current_revision from stak_private.backup_heads h where h.user_id=uid for update;
    select * into prior from stak_private.backup_versions v where v.user_id=uid and v.operation=p_operation;
    if found then
        if prior.fingerprint!=p_fingerprint or prior.backup!=p_backup then raise invalid_parameter_value using message='Operation already used';end if;
        return jsonb_build_object('revision',prior.revision,'fingerprint',prior.fingerprint,'backup',prior.backup,'saved_at',prior.saved_at);
    end if;
    if current_revision!=p_expected_revision then return jsonb_build_object('conflict',true);end if;
    -- Retained prior copies and CAS protect against empty/stale installation writes.
    insert into stak_private.backup_versions(user_id,revision,operation,fingerprint,backup)
        values(uid,current_revision+1,p_operation,p_fingerprint,p_backup)
        returning jsonb_build_object('revision',revision,'fingerprint',fingerprint,'backup',backup,'saved_at',saved_at) into result;
    update stak_private.backup_heads set revision=current_revision+1 where user_id=uid;
    delete from stak_private.backup_versions where user_id=uid and revision<=current_revision+1-5;
    return result;
end; $$;

revoke all on function public.stak_backup_read() from public,anon;
revoke all on function public.stak_backup_write(bigint,text,jsonb,uuid) from public,anon;
grant execute on function public.stak_backup_read() to authenticated;
grant execute on function public.stak_backup_write(bigint,text,jsonb,uuid) to authenticated;
notify pgrst,'reload schema';
commit;
