-- Every row belongs to a workspace: each visitor (and their paired phone) works on a private copy.
create table workspace (
    id            uuid primary key,
    join_token    varchar(40) not null unique,
    created_at    timestamptz not null,
    last_seen_at  timestamptz not null,
    seed_date     date not null
);
create index workspace_last_seen on workspace (last_seen_at);

create table article (
    id            uuid primary key,
    workspace_id  uuid not null references workspace (id) on delete cascade,
    number        varchar(32) not null,
    ean           varchar(13) not null,
    name          varchar(200) not null,
    article_group varchar(60) not null,
    unit          varchar(12) not null,
    min_stock     numeric(12, 3) not null default 0,
    photo_id      uuid,
    unique (workspace_id, number),
    unique (workspace_id, ean)
);

create table bin (
    id            uuid primary key,
    workspace_id  uuid not null references workspace (id) on delete cascade,
    code          varchar(32) not null,
    zone          varchar(60) not null,
    unique (workspace_id, code)
);

create table stock (
    id             uuid primary key,
    workspace_id   uuid not null references workspace (id) on delete cascade,
    article_number varchar(32) not null,
    bin_code       varchar(32) not null,
    quantity       numeric(12, 3) not null check (quantity >= 0),
    version        bigint not null default 0,
    unique (workspace_id, article_number, bin_code)
);

create table photo (
    id             uuid primary key,
    workspace_id   uuid not null references workspace (id) on delete cascade,
    article_number varchar(32),
    content_type   varchar(40) not null,
    data           bytea not null,
    created_at     timestamptz not null
);

-- Bookings carry the id generated on the device: re-sending an offline booking is harmless.
create table booking (
    id             uuid primary key,
    workspace_id   uuid not null references workspace (id) on delete cascade,
    client_id      uuid not null,
    type           varchar(16) not null,
    article_number varchar(32) not null,
    from_bin       varchar(32),
    to_bin         varchar(32),
    quantity       numeric(12, 3),
    counted        numeric(12, 3),
    difference     numeric(12, 3),
    note           varchar(500),
    photo_id       uuid,
    device         varchar(16) not null,
    booked_at      timestamptz not null,
    created_at     timestamptz not null,
    unique (workspace_id, client_id)
);
create index booking_recent on booking (workspace_id, booked_at desc);
