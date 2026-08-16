
    alter table nop_datav_dashboard add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_export_task add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_chat_session add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_panel add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_tab add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dataset_ref add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_snapshot add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_filter_state add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_share add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_report_task add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen_widget add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_screen_snapshot add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_chat_message add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_alert_rule add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_report_delivery add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_alert_state add NOP_TENANT_ID VARCHAR2(32) DEFAULT '0' NOT NULL;

alter table nop_datav_dashboard drop constraint PK_nop_datav_dashboard;
alter table nop_datav_dashboard add constraint PK_nop_datav_dashboard primary key (NOP_TENANT_ID, DASHBOARD_ID);

alter table nop_datav_export_task drop constraint PK_nop_datav_export_task;
alter table nop_datav_export_task add constraint PK_nop_datav_export_task primary key (NOP_TENANT_ID, TASK_ID);

alter table nop_datav_screen drop constraint PK_nop_datav_screen;
alter table nop_datav_screen add constraint PK_nop_datav_screen primary key (NOP_TENANT_ID, SCREEN_ID);

alter table nop_datav_chat_session drop constraint PK_nop_datav_chat_session;
alter table nop_datav_chat_session add constraint PK_nop_datav_chat_session primary key (NOP_TENANT_ID, SESSION_ID);

alter table nop_datav_panel drop constraint PK_nop_datav_panel;
alter table nop_datav_panel add constraint PK_nop_datav_panel primary key (NOP_TENANT_ID, PANEL_ID);

alter table nop_datav_tab drop constraint PK_nop_datav_tab;
alter table nop_datav_tab add constraint PK_nop_datav_tab primary key (NOP_TENANT_ID, TAB_ID);

alter table nop_datav_dataset_ref drop constraint PK_nop_datav_dataset_ref;
alter table nop_datav_dataset_ref add constraint PK_nop_datav_dataset_ref primary key (NOP_TENANT_ID, DATASET_REF_ID);

alter table nop_datav_snapshot drop constraint PK_nop_datav_snapshot;
alter table nop_datav_snapshot add constraint PK_nop_datav_snapshot primary key (NOP_TENANT_ID, SNAPSHOT_ID);

alter table nop_datav_filter_state drop constraint PK_nop_datav_filter_state;
alter table nop_datav_filter_state add constraint PK_nop_datav_filter_state primary key (NOP_TENANT_ID, STATE_ID);

alter table nop_datav_share drop constraint PK_nop_datav_share;
alter table nop_datav_share add constraint PK_nop_datav_share primary key (NOP_TENANT_ID, SHARE_ID);

alter table nop_datav_report_task drop constraint PK_nop_datav_report_task;
alter table nop_datav_report_task add constraint PK_nop_datav_report_task primary key (NOP_TENANT_ID, REPORT_TASK_ID);

alter table nop_datav_screen_widget drop constraint PK_nop_datav_screen_widget;
alter table nop_datav_screen_widget add constraint PK_nop_datav_screen_widget primary key (NOP_TENANT_ID, WIDGET_ID);

alter table nop_datav_screen_snapshot drop constraint PK_nop_datav_screen_snapshot;
alter table nop_datav_screen_snapshot add constraint PK_nop_datav_screen_snapshot primary key (NOP_TENANT_ID, SNAPSHOT_ID);

alter table nop_datav_chat_message drop constraint PK_nop_datav_chat_message;
alter table nop_datav_chat_message add constraint PK_nop_datav_chat_message primary key (NOP_TENANT_ID, MESSAGE_ID);

alter table nop_datav_alert_rule drop constraint PK_nop_datav_alert_rule;
alter table nop_datav_alert_rule add constraint PK_nop_datav_alert_rule primary key (NOP_TENANT_ID, ALERT_RULE_ID);

alter table nop_datav_report_delivery drop constraint PK_nop_datav_report_delivery;
alter table nop_datav_report_delivery add constraint PK_nop_datav_report_delivery primary key (NOP_TENANT_ID, DELIVERY_ID);

alter table nop_datav_alert_state drop constraint PK_nop_datav_alert_state;
alter table nop_datav_alert_state add constraint PK_nop_datav_alert_state primary key (NOP_TENANT_ID, ALERT_STATE_ID);

alter table nop_datav_dashboard drop constraint UK_NOP_DATAV_DASHBOARD_NAME;
alter table nop_datav_dashboard add constraint UK_NOP_DATAV_DASHBOARD_NAME unique (NOP_TENANT_ID,DASHBOARD_NAME);

                alter table nop_datav_screen drop constraint UK_NOP_DATAV_SCREEN_NAME;
alter table nop_datav_screen add constraint UK_NOP_DATAV_SCREEN_NAME unique (NOP_TENANT_ID,SCREEN_NAME);

                alter table nop_datav_snapshot drop constraint UK_NOP_DATAV_SNAPSHOT_DASH_VER;
alter table nop_datav_snapshot add constraint UK_NOP_DATAV_SNAPSHOT_DASH_VER unique (NOP_TENANT_ID,DASHBOARD_ID,SNAPSHOT_VERSION);

                alter table nop_datav_filter_state drop constraint UK_NOP_DATAV_FILTER_STATE_USER_DASH;
alter table nop_datav_filter_state add constraint UK_NOP_DATAV_FILTER_STATE_USER_DASH unique (NOP_TENANT_ID,USER_NAME,DASHBOARD_ID);

                alter table nop_datav_share drop constraint UK_NOP_DATAV_SHARE_TOKEN;
alter table nop_datav_share add constraint UK_NOP_DATAV_SHARE_TOKEN unique (NOP_TENANT_ID,SHARE_TOKEN);

                alter table nop_datav_screen_snapshot drop constraint UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER;
alter table nop_datav_screen_snapshot add constraint UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER unique (NOP_TENANT_ID,SCREEN_ID,SNAPSHOT_VERSION);

                alter table nop_datav_chat_message drop constraint UK_NOP_DATAV_CHAT_MSG_SESSION_SEQ;
alter table nop_datav_chat_message add constraint UK_NOP_DATAV_CHAT_MSG_SESSION_SEQ unique (NOP_TENANT_ID,SESSION_ID,SEQ);

                alter table nop_datav_alert_state drop constraint UK_NOP_DATAV_ALERT_STATE_RULE;
alter table nop_datav_alert_state add constraint UK_NOP_DATAV_ALERT_STATE_RULE unique (NOP_TENANT_ID,ALERT_RULE_ID);

                
