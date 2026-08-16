-- Materialize unique keys declared in nop-datav.orm.xml (P0-03, plan 2026-08-15-2146-3).
--
-- Applies to EXISTING MySQL databases created before UK materialization.
-- New installs are covered by _create_nop-datav.sql (regenerated); tenant deployments
-- are covered by _add_tenant_nop-datav.sql (regenerated, rebuilds UKs with NOP_TENANT_ID
-- prefix via ddl.xlib AddTenantIdToUniqueKey).
--
-- PRECONDITION (fail-fast by design, no auto-dedup): UK was never enforced before, so
-- duplicates may exist. Audit them first; ADD CONSTRAINT fails on duplicates and reports
-- the offending value. Deduplicate manually, then re-run:
--
--   select DASHBOARD_NAME, count(*) from nop_datav_dashboard group by DASHBOARD_NAME having count(*) > 1;
--   select DASHBOARD_ID, SNAPSHOT_VERSION, count(*) from nop_datav_snapshot group by DASHBOARD_ID, SNAPSHOT_VERSION having count(*) > 1;
--   select USER_NAME, DASHBOARD_ID, count(*) from nop_datav_filter_state group by USER_NAME, DASHBOARD_ID having count(*) > 1;
--   select SHARE_TOKEN, count(*) from nop_datav_share group by SHARE_TOKEN having count(*) > 1;
--   select SCREEN_NAME, count(*) from nop_datav_screen group by SCREEN_NAME having count(*) > 1;
--   select SCREEN_ID, SNAPSHOT_VERSION, count(*) from nop_datav_screen_snapshot group by SCREEN_ID, SNAPSHOT_VERSION having count(*) > 1;
--   select ALERT_RULE_ID, count(*) from nop_datav_alert_state group by ALERT_RULE_ID having count(*) > 1;
--   select SESSION_ID, SEQ, count(*) from nop_datav_chat_message group by SESSION_ID, SEQ having count(*) > 1;
--
-- Resulting UK shape must match the live model (see ai-dev/design/nop-datav/model-design.md
-- "唯一键与索引物化契约").

alter table nop_datav_dashboard add constraint UK_NOP_DATAV_DASHBOARD_NAME unique (DASHBOARD_NAME);
alter table nop_datav_snapshot add constraint UK_NOP_DATAV_SNAPSHOT_DASH_VER unique (DASHBOARD_ID,SNAPSHOT_VERSION);
alter table nop_datav_filter_state add constraint UK_NOP_DATAV_FILTER_STATE_USER_DASH unique (USER_NAME,DASHBOARD_ID);
alter table nop_datav_share add constraint UK_NOP_DATAV_SHARE_TOKEN unique (SHARE_TOKEN);
alter table nop_datav_screen add constraint UK_NOP_DATAV_SCREEN_NAME unique (SCREEN_NAME);
alter table nop_datav_screen_snapshot add constraint UK_NOP_DATAV_SCREEN_SNAPSHOT_SCREEN_VER unique (SCREEN_ID,SNAPSHOT_VERSION);
alter table nop_datav_alert_state add constraint UK_NOP_DATAV_ALERT_STATE_RULE unique (ALERT_RULE_ID);
alter table nop_datav_chat_message add constraint UK_NOP_DATAV_CHAT_MSG_SESSION_SEQ unique (SESSION_ID,SEQ);
