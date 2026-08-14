
    alter table nop_datav_dashboard add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_export_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_chat_session add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_panel add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_tab add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dataset_ref add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_snapshot add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_filter_state add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_share add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_report_task add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen_widget add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen_snapshot add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_chat_message add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_alert_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_report_delivery add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_alert_state add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dashboard drop primary key;
alter table nop_datav_dashboard add primary key (NOP_TENANT_ID, DASHBOARD_ID);

alter table nop_datav_export_task drop primary key;
alter table nop_datav_export_task add primary key (NOP_TENANT_ID, TASK_ID);

alter table nop_datav_screen drop primary key;
alter table nop_datav_screen add primary key (NOP_TENANT_ID, SCREEN_ID);

alter table nop_datav_chat_session drop primary key;
alter table nop_datav_chat_session add primary key (NOP_TENANT_ID, SESSION_ID);

alter table nop_datav_panel drop primary key;
alter table nop_datav_panel add primary key (NOP_TENANT_ID, PANEL_ID);

alter table nop_datav_tab drop primary key;
alter table nop_datav_tab add primary key (NOP_TENANT_ID, TAB_ID);

alter table nop_datav_dataset_ref drop primary key;
alter table nop_datav_dataset_ref add primary key (NOP_TENANT_ID, DATASET_REF_ID);

alter table nop_datav_snapshot drop primary key;
alter table nop_datav_snapshot add primary key (NOP_TENANT_ID, SNAPSHOT_ID);

alter table nop_datav_filter_state drop primary key;
alter table nop_datav_filter_state add primary key (NOP_TENANT_ID, STATE_ID);

alter table nop_datav_share drop primary key;
alter table nop_datav_share add primary key (NOP_TENANT_ID, SHARE_ID);

alter table nop_datav_report_task drop primary key;
alter table nop_datav_report_task add primary key (NOP_TENANT_ID, REPORT_TASK_ID);

alter table nop_datav_screen_widget drop primary key;
alter table nop_datav_screen_widget add primary key (NOP_TENANT_ID, WIDGET_ID);

alter table nop_datav_screen_snapshot drop primary key;
alter table nop_datav_screen_snapshot add primary key (NOP_TENANT_ID, SNAPSHOT_ID);

alter table nop_datav_chat_message drop primary key;
alter table nop_datav_chat_message add primary key (NOP_TENANT_ID, MESSAGE_ID);

alter table nop_datav_alert_rule drop primary key;
alter table nop_datav_alert_rule add primary key (NOP_TENANT_ID, ALERT_RULE_ID);

alter table nop_datav_report_delivery drop primary key;
alter table nop_datav_report_delivery add primary key (NOP_TENANT_ID, DELIVERY_ID);

alter table nop_datav_alert_state drop primary key;
alter table nop_datav_alert_state add primary key (NOP_TENANT_ID, ALERT_STATE_ID);


