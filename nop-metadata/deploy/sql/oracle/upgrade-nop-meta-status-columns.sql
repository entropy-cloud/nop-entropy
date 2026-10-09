-- Upgrade: narrow 6 status/severity columns from CLOB to VARCHAR2(20)
-- (P1-2.5-001 / plan 2373). Oracle requires a two-step temp-column move for
-- CLOB -> VARCHAR2; run per table. Pre-check: length > 20 fails loudly.
ALTER TABLE nop_meta_module ADD (STATUS_TMP VARCHAR2(20));
UPDATE nop_meta_module SET STATUS_TMP = DBMS_LOB.SUBSTR(STATUS, 20, 1);
ALTER TABLE nop_meta_module DROP COLUMN STATUS;
ALTER TABLE nop_meta_module RENAME COLUMN STATUS_TMP TO STATUS;

ALTER TABLE nop_meta_data_source ADD (STATUS_TMP VARCHAR2(20));
UPDATE nop_meta_data_source SET STATUS_TMP = DBMS_LOB.SUBSTR(STATUS, 20, 1);
ALTER TABLE nop_meta_data_source DROP COLUMN STATUS;
ALTER TABLE nop_meta_data_source RENAME COLUMN STATUS_TMP TO STATUS;

ALTER TABLE nop_meta_quality_check_result ADD (SEVERITY_TMP VARCHAR2(20));
UPDATE nop_meta_quality_check_result SET SEVERITY_TMP = DBMS_LOB.SUBSTR(SEVERITY, 20, 1);
ALTER TABLE nop_meta_quality_check_result DROP COLUMN SEVERITY;
ALTER TABLE nop_meta_quality_check_result RENAME COLUMN SEVERITY_TMP TO SEVERITY;

ALTER TABLE nop_meta_quality_check_log ADD (STATUS_TMP VARCHAR2(20));
UPDATE nop_meta_quality_check_log SET STATUS_TMP = DBMS_LOB.SUBSTR(STATUS, 20, 1);
ALTER TABLE nop_meta_quality_check_log DROP COLUMN STATUS;
ALTER TABLE nop_meta_quality_check_log RENAME COLUMN STATUS_TMP TO STATUS;

ALTER TABLE nop_meta_quality_check_task ADD (STATUS_TMP VARCHAR2(20));
UPDATE nop_meta_quality_check_task SET STATUS_TMP = DBMS_LOB.SUBSTR(STATUS, 20, 1);
ALTER TABLE nop_meta_quality_check_task DROP COLUMN STATUS;
ALTER TABLE nop_meta_quality_check_task RENAME COLUMN STATUS_TMP TO STATUS;

ALTER TABLE nop_meta_quality_dataset ADD (STATUS_TMP VARCHAR2(20));
UPDATE nop_meta_quality_dataset SET STATUS_TMP = DBMS_LOB.SUBSTR(STATUS, 20, 1);
ALTER TABLE nop_meta_quality_dataset DROP COLUMN STATUS;
ALTER TABLE nop_meta_quality_dataset RENAME COLUMN STATUS_TMP TO STATUS;
