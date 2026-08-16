-- Materialize unique keys declared in nop-datav.orm.xml (P0-03, plan 2026-08-15-2146-3).
--
-- Applies to EXISTING PostgreSQL databases created before UK materialization.
-- New installs are covered by _create_nop-datav.sql (regenerated); tenant deployments
-- are covered by _add_tenant_nop-datav.sql (regenerated, rebuilds UKs with NOP_TENANT_ID
-- prefix via ddl.xlib AddTenantIdToUniqueKey).
--
-- PRECONDITION (fail-fast by design, no auto-dedup): UK was never enforced before, so
-- duplicates may exist. Audit them first; ADD CONSTRAINT fails on duplicates and reports
-- the offending value. Deduplicate manually, then re-run:
--
--   select dashboard_name, count(*) from nop_datav_dashboard group by dashboard_name having count(*) > 1;
--   select dashboard_id, snapshot_version, count(*) from nop_datav_snapshot group by dashboard_id, snapshot_version having count(*) > 1;
--   select user_name, dashboard_id, count(*) from nop_datav_filter_state group by user_name, dashboard_id having count(*) > 1;
--   select share_token, count(*) from nop_datav_share group by share_token having count(*) > 1;
--   select screen_name, count(*) from nop_datav_screen group by screen_name having count(*) > 1;
--   select screen_id, snapshot_version, count(*) from nop_datav_screen_snapshot group by screen_id, snapshot_version having count(*) > 1;
--   select alert_rule_id, count(*) from nop_datav_alert_state group by alert_rule_id having count(*) > 1;
--   select session_id, seq, count(*) from nop_datav_chat_message group by session_id, seq having count(*) > 1;
--
-- Resulting UK shape must match the live model (see ai-dev/design/nop-datav/model-design.md
-- "唯一键与索引物化契约").

alter table nop_datav_dashboard add constraint UK_NOP_DATAV_DASHBOARD_NAME unique (dashboard_name);
alter table nop_datav_snapshot add constraint UK_NOP_DATAV_SNAPSHOT_DASH_VER unique (dashboard_id,snapshot_version);
alter table nop_datav_filter_state add constraint UK_NOP_DATAV_FILTER_STATE_USER_DASH unique (user_name,dashboard_id);
alter table nop_datav_share add constraint UK_NOP_DATAV_SHARE_TOKEN unique (share_token);
alter table nop_datav_screen add constraint UK_NOP_DATAV_SCREEN_NAME unique (screen_name);
alter table nop_datav_screen_snapshot add constraint UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER unique (screen_id,snapshot_version);
alter table nop_datav_alert_state add constraint UK_NOP_DATAV_ALERT_STATE_RULE unique (alert_rule_id);
alter table nop_datav_chat_message add constraint UK_NOP_DATAV_CHAT_MSG_SESSION_SEQ unique (session_id,seq);
