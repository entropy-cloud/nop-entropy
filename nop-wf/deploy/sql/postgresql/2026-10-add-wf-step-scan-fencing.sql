-- plan 2514 (R2-11 1.5-C7-001 / MA-23) WfTaskScanner fencing 抢占列.
-- 裁定⑥（ai-dev/design/concurrency/wf-task-scanner-fencing-and-task-wf-concurrency.md §3.6）:
-- 以条件 UPDATE 抢占（holderId + 租约）为多节点/重叠扫描提供 fencing。
-- 幂等方言：PostgreSQL ADD COLUMN IF NOT EXISTS，脚本可重复执行；
-- 其他方言部署由 DBA 按语义等价转写（平台只维护 postgresql 基准脚本）。
-- 注意：两列只经直发 UPDATE 语句读写（条件抢占与放弃/租约清理），不经 ORM 实体逐字段落盘。

ALTER TABLE nop_wf_step_instance ADD COLUMN IF NOT EXISTS scan_holder_id VARCHAR(64) NULL;

ALTER TABLE nop_wf_step_instance ADD COLUMN IF NOT EXISTS scan_lease_time TIMESTAMP NULL;
