-- Materialize secondary indexes declared in nop-datav.orm.xml (P1-08, plan 2026-08-15-2146-3).
--
-- Applies to EXISTING Oracle databases (CreateTable DDL never emitted indexes; new installs
-- must apply this script too — the create-table pipeline emits tables/constraints only).
-- 21 indexes: IX_NOP_DATAV_ALERT_STATE_RULE was removed from the model (P2-11 convergence,
-- physically duplicated by UK_NOP_DATAV_ALERT_STATE_RULE unique constraint).

create index IX_NOP_DATAV_PANEL_DASHBOARD on nop_datav_panel (DASHBOARD_ID);
create index IX_NOP_DATAV_TAB_DASHBOARD on nop_datav_tab (DASHBOARD_ID);
create index IX_NOP_DATAV_DATASET_REF_DASHBOARD on nop_datav_dataset_ref (DASHBOARD_ID);
create index IX_NOP_DATAV_SNAPSHOT_DASHBOARD on nop_datav_snapshot (DASHBOARD_ID);
create index IX_NOP_DATAV_FILTER_STATE_DASHBOARD on nop_datav_filter_state (DASHBOARD_ID);
create index IX_NOP_DATAV_SHARE_DASHBOARD on nop_datav_share (DASHBOARD_ID);
create index IX_NOP_DATAV_EXPORT_TASK_OWNER on nop_datav_export_task (CREATED_BY);
create index IX_NOP_DATAV_EXPORT_TASK_STATUS on nop_datav_export_task (STATUS);
create index IX_NOP_DATAV_SCREEN_WIDGET_SCREEN on nop_datav_screen_widget (SCREEN_ID);
create index IX_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN on nop_datav_screen_snapshot (SCREEN_ID);
create index IX_NOP_DATAV_REPORT_TASK_DASHBOARD on nop_datav_report_task (DASHBOARD_ID);
create index IX_NOP_DATAV_REPORT_TASK_OWNER on nop_datav_report_task (CREATED_BY);
create index IX_NOP_DATAV_REPORT_TASK_STATUS on nop_datav_report_task (STATUS);
create index IX_NOP_DATAV_REPORT_DELIVERY_TASK on nop_datav_report_delivery (REPORT_TASK_ID);
create index IX_NOP_DATAV_REPORT_DELIVERY_STATUS on nop_datav_report_delivery (STATUS);
create index IX_NOP_DATAV_ALERT_RULE_PANEL on nop_datav_alert_rule (PANEL_ID);
create index IX_NOP_DATAV_ALERT_RULE_OWNER on nop_datav_alert_rule (CREATED_BY);
create index IX_NOP_DATAV_ALERT_RULE_STATUS on nop_datav_alert_rule (STATUS);
create index IX_NOP_DATAV_ALERT_STATE_STATE on nop_datav_alert_state (STATE);
create index IX_NOP_DATAV_CHAT_SESSION_USER on nop_datav_chat_session (USER_NAME);
create index IX_NOP_DATAV_CHAT_MSG_SESSION on nop_datav_chat_message (SESSION_ID);
