
    alter table nop_datav_dashboard add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_panel add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_tab add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dataset_ref add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_snapshot add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_filter_state add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_share add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dashboard drop constraint PK_nop_datav_dashboard;
alter table nop_datav_dashboard add constraint PK_nop_datav_dashboard primary key (NOP_TENANT_ID, dashboard_id);

alter table nop_datav_panel drop constraint PK_nop_datav_panel;
alter table nop_datav_panel add constraint PK_nop_datav_panel primary key (NOP_TENANT_ID, panel_id);

alter table nop_datav_tab drop constraint PK_nop_datav_tab;
alter table nop_datav_tab add constraint PK_nop_datav_tab primary key (NOP_TENANT_ID, tab_id);

alter table nop_datav_dataset_ref drop constraint PK_nop_datav_dataset_ref;
alter table nop_datav_dataset_ref add constraint PK_nop_datav_dataset_ref primary key (NOP_TENANT_ID, dataset_ref_id);

alter table nop_datav_snapshot drop constraint PK_nop_datav_snapshot;
alter table nop_datav_snapshot add constraint PK_nop_datav_snapshot primary key (NOP_TENANT_ID, snapshot_id);

alter table nop_datav_filter_state drop constraint PK_nop_datav_filter_state;
alter table nop_datav_filter_state add constraint PK_nop_datav_filter_state primary key (NOP_TENANT_ID, state_id);

alter table nop_datav_share drop constraint PK_nop_datav_share;
alter table nop_datav_share add constraint PK_nop_datav_share primary key (NOP_TENANT_ID, share_id);


