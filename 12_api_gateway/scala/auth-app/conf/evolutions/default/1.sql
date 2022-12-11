# --- !Ups
drop table if exists auth_user;
create table auth_user
(
    id         serial primary key,
    login      varchar not null unique,
    password   varchar,
    email      varchar not null default '',
    first_name varchar not null default '',
    last_name  varchar not null default ''
);

insert into auth_user (login, password)
values ('admin', 'password');

# --- !Downs

DROP TABLE IF EXISTS auth_user CASCADE;
