create table users (
    id            uuid primary key,
    email         text not null,
    password_hash text not null,
    created_at    timestamptz not null,
    updated_at    timestamptz not null
);

create unique index users_email_key on users (email);
