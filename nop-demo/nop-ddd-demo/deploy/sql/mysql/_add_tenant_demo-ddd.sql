
    alter table voyage add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table location add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table cargo add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table carrier_movement add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table handling_event add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table leg add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table voyage drop primary key;
alter table voyage add primary key (NOP_TENANT_ID, ID);

alter table location drop primary key;
alter table location add primary key (NOP_TENANT_ID, ID);

alter table cargo drop primary key;
alter table cargo add primary key (NOP_TENANT_ID, ID);

alter table carrier_movement drop primary key;
alter table carrier_movement add primary key (NOP_TENANT_ID, ID);

alter table handling_event drop primary key;
alter table handling_event add primary key (NOP_TENANT_ID, ID);

alter table leg drop primary key;
alter table leg add primary key (NOP_TENANT_ID, ID);


