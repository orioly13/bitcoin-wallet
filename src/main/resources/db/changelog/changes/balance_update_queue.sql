--liquibase formatted sql

--changeset prediger:create_balance_queue
CREATE SEQUENCE BALANCE_UPDATE_QUEUE_SEQ_ID;
CREATE TABLE PUBLIC.BALANCE_UPDATE_QUEUE(
   ID bigint default BALANCE_UPDATE_QUEUE_SEQ_ID.nextval primary key,
   TS bigint not null,
   BITCOINS DECIMAL(15,8)  not null
);

--changeset prediger:create_index
CREATE INDEX QUEUE_BY_TS_IDX ON BALANCE_UPDATE_QUEUE(TS);