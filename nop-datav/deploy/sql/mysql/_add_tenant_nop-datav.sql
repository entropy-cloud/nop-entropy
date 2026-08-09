
    alter table nop_datav_dashboard add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_panel add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_tab add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dataset_ref add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_snapshot add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dashboard drop primary key;
alter table nop_datav_dashboard add primary key (NOP_TENANT_ID, DASHBOARD_ID);

alter table nop_datav_panel drop primary key;
alter table nop_datav_panel add primary key (NOP_TENANT_ID, PANEL_ID);

alter table nop_datav_tab drop primary key;
alter table nop_datav_tab add primary key (NOP_TENANT_ID, TAB_ID);

alter table nop_datav_dataset_ref drop primary key;
alter table nop_datav_dataset_ref add primary key (NOP_TENANT_ID, DATASET_REF_ID);

alter table nop_datav_snapshot drop primary key;
alter table nop_datav_snapshot add primary key (NOP_TENANT_ID, SNAPSHOT_ID);


