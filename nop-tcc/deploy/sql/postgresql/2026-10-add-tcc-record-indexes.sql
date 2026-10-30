-- plan 2515 (R2-12 1.6-C7-005) nop_tcc_record 恢复扫描/清理谓词索引.
-- 裁定⑧（ai-dev/design/concurrency/retry-tcc-cas-compensation-and-ledger-hygiene.md §3.8）:
-- 恢复扫描查询（expire_time < ? AND status < 11 AND retry_times < ? ORDER BY begin_time）与
-- 耗尽行终态化/有界清理谓词此前全表零索引可用，账本随事务量线性增长致恢复扫描 RTO 单调劣化。
-- 幂等方言：PostgreSQL CREATE INDEX IF NOT EXISTS，脚本可重复执行；
-- 其他方言部署由 DBA 按语义等价转写（平台只维护 postgresql 基准脚本）。
-- 纯新增索引，零表结构变更、零实体字段变更；orm 源模型 nop-tcc/model/nop-tcc.orm.xml
-- 已同步申报 <indexes> 段（新装环境由生成链建索引，本脚本服务存量库升级）。

CREATE INDEX IF NOT EXISTS idx_tcc_record_expire ON nop_tcc_record (expire_time, status, retry_times);

CREATE INDEX IF NOT EXISTS idx_tcc_record_begin_time ON nop_tcc_record (begin_time);
