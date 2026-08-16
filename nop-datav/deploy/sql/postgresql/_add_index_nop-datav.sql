-- Materialize secondary indexes declared in nop-datav.orm.xml (P1-08, plan 2026-08-15-2146-3).
--
-- Applies to EXISTING PostgreSQL databases (CreateTable DDL never emitted indexes; new installs
-- must apply this script too — the create-table pipeline emits tables/constraints only).
-- 21 indexes: IX_NOP_DATAV_ALERT_STATE_RULE was removed from the model (P2-11 convergence,
-- physically duplicated by UK_NOP_DATAV_ALERT_STATE_RULE unique constraint).

create index IX_NOP_DATAV_PANEL_DASHBOARD on nop_datav_panel (dashboard_id);
create index IX_NOP_DATAV_TAB_DASHBOARD on nop_datav_tab (dashboard_id);
create index IX_NOP_DATAV_DATASET_REF_DASHBOARD on nop_datav_dataset_ref (dashboard_id);
create index IX_NOP_DATAV_SNAPSHOT_DASHBOARD on nop_datav_snapshot (dashboard_id);
create index IX_NOP_DATAV_FILTER_STATE_DASHBOARD on nop_datav_filter_state (dashboard_id);
create index IX_NOP_DATAV_SHARE_DASHBOARD on nop_datav_share (dashboard_id);
create index IX_NOP_DATAV_EXPORT_TASK_OWNER on nop_datav_export_task (created_by);
create index IX_NOP_DATAV_EXPORT_TASK_STATUS on nop_datav_export_task (status);
create index IX_NOP_DATAV_SCREEN_WIDGET_SCREEN on nop_datav_screen_widget (screen_id);
create index IX_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN on nop_datav_screen_snapshot (screen_id);
create index IX_NOP_DATAV_REPORT_TASK_DASHBOARD on nop_datav_report_task (dashboard_id);
create index IX_NOP_DATAV_REPORT_TASK_OWNER on nop_datav_report_task (created_by);
create index IX_NOP_DATAV_REPORT_TASK_STATUS on nop_datav_report_task (status);
create index IX_NOP_DATAV_REPORT_DELIVERY_TASK on nop_datav_report_delivery (report_task_id);
create index IX_NOP_DATAV_REPORT_DELIVERY_STATUS on nop_datav_report_delivery (status);
create index IX_NOP_DATAV_ALERT_RULE_PANEL on nop_datav_alert_rule (panel_id);
create index IX_NOP_DATAV_ALERT_RULE_OWNER on nop_datav_alert_rule (created_by);
create index IX_NOP_DATAV_ALERT_RULE_STATUS on nop_datav_alert_rule (status);
create index IX_NOP_DATAV_ALERT_STATE_STATE on nop_datav_alert_state (state);
create index IX_NOP_DATAV_CHAT_SESSION_USER on nop_datav_chat_session (user_name);
create index IX_NOP_DATAV_CHAT_MSG_SESSION on nop_datav_chat_message (session_id);
