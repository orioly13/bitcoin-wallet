--liquibase formatted sql

--changeset prediger:create_wallet
CREATE SEQUENCE SEQ_ID;
CREATE TABLE WALLLET(
   ID bigint default SEQ_ID.nextval primary key,
   TS bigint not null,
   DOLLARS bigint not null,
   CENTS int not null
);

--changeset prediger:create_wallet_timestamp_index
CREATE INDEX WALLET_BY_TS_IDX ON WALLLET(TS);