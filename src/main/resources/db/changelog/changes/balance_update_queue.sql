--liquibase formatted sql

--changeset prediger:create_wallet
CREATE SEQUENCE BALANCE_UPDATE_QUEUE_SEQ_ID;
CREATE TABLE PUBLIC.BALANCE_UPDATE_QUEUE(
   ID bigint default BALANCE_UPDATE_QUEUE_SEQ_ID.nextval primary key,
   TS bigint not null,
   BITCOINS bigint not null,
   SATOSHI bigint not null
);