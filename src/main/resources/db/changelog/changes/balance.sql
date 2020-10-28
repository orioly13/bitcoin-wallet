--liquibase formatted sql

--changeset prediger:create_balance_table
-- index should be created by default, since it's a primary key
CREATE TABLE PUBLIC.BALANCE(
   TS bigint not null primary key,
   BITCOINS bigint not null,
   SATOSHI bigint not null
);