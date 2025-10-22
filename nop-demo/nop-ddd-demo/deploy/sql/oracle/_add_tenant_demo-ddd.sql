
    alter table voyage add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table location add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table cargo add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table carrier_movement add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table handling_event add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table leg add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table voyage drop constraint PK_voyage;
alter table voyage add constraint PK_voyage primary key (NOP_TENANT_ID, ID);

alter table location drop constraint PK_location;
alter table location add constraint PK_location primary key (NOP_TENANT_ID, ID);

alter table cargo drop constraint PK_cargo;
alter table cargo add constraint PK_cargo primary key (NOP_TENANT_ID, ID);

alter table carrier_movement drop constraint PK_carrier_movement;
alter table carrier_movement add constraint PK_carrier_movement primary key (NOP_TENANT_ID, ID);

alter table handling_event drop constraint PK_handling_event;
alter table handling_event add constraint PK_handling_event primary key (NOP_TENANT_ID, ID);

alter table leg drop constraint PK_leg;
alter table leg add constraint PK_leg primary key (NOP_TENANT_ID, ID);


