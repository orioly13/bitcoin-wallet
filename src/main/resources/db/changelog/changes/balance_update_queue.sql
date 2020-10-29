--liquibase formatted sql

--changeset prediger:create_wallet
CREATE TABLE PUBLIC.BALANCE_UPDATE_QUEUE(
   TS bigint not null primary key,
   BITCOINS DECIMAL(15,8)  not null
);