create table refresh_tokens (
    id           uuid primary key,
    user_id      uuid not null references users(id),
    session_id   uuid not null,
    token_hash   char(64) not null,
    consumed_at  timestamptz null,
    expires_at   timestamptz not null,
    created_at   timestamptz not null,
    updated_at   timestamptz not null
);

create unique index refresh_tokens_token_hash_key on refresh_tokens (token_hash);
create index refresh_tokens_session_id_idx on refresh_tokens (session_id);
