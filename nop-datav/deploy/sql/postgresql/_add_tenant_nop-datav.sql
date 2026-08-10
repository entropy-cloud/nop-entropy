
    alter table nop_datav_dashboard add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_export_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_panel add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_tab add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dataset_ref add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_snapshot add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_filter_state add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_share add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_report_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen_widget add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen_snapshot add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_alert_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_report_delivery add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_alert_state add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dashboard drop constraint PK_nop_datav_dashboard;
alter table nop_datav_dashboard add constraint PK_nop_datav_dashboard primary key (NOP_TENANT_ID, dashboard_id);

alter table nop_datav_export_task drop constraint PK_nop_datav_export_task;
alter table nop_datav_export_task add constraint PK_nop_datav_export_task primary key (NOP_TENANT_ID, task_id);

alter table nop_datav_screen drop constraint PK_nop_datav_screen;
alter table nop_datav_screen add constraint PK_nop_datav_screen primary key (NOP_TENANT_ID, screen_id);

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

alter table nop_datav_report_task drop constraint PK_nop_datav_report_task;
alter table nop_datav_report_task add constraint PK_nop_datav_report_task primary key (NOP_TENANT_ID, report_task_id);

alter table nop_datav_screen_widget drop constraint PK_nop_datav_screen_widget;
alter table nop_datav_screen_widget add constraint PK_nop_datav_screen_widget primary key (NOP_TENANT_ID, widget_id);

alter table nop_datav_screen_snapshot drop constraint PK_nop_datav_screen_snapshot;
alter table nop_datav_screen_snapshot add constraint PK_nop_datav_screen_snapshot primary key (NOP_TENANT_ID, snapshot_id);

alter table nop_datav_alert_rule drop constraint PK_nop_datav_alert_rule;
alter table nop_datav_alert_rule add constraint PK_nop_datav_alert_rule primary key (NOP_TENANT_ID, alert_rule_id);

alter table nop_datav_report_delivery drop constraint PK_nop_datav_report_delivery;
alter table nop_datav_report_delivery add constraint PK_nop_datav_report_delivery primary key (NOP_TENANT_ID, delivery_id);

alter table nop_datav_alert_state drop constraint PK_nop_datav_alert_state;
alter table nop_datav_alert_state add constraint PK_nop_datav_alert_state primary key (NOP_TENANT_ID, alert_state_id);


