-- W11: 凭证归属统一（scope/ownerId，存量部署增量脚本，新建部署由 _create_ 覆盖）
-- 表结构经 model-first codegen（nop-credential.orm.xml），本脚本为手写增量（循 _add_tenant_/_add_oauth_state_ 先例）
-- scope 不加 DDL 默认值：存量行 NULL 由校验/过滤侧视同 system（语义等价裁定，见 plan W11-impl Phase 1）
-- 归属不可变：scope/ownerId 创建后固定；scope=user 时 ownerId 必填、scope=system 时恒空

ALTER TABLE nop_credential ADD COLUMN scope VARCHAR(20);
ALTER TABLE nop_credential ADD COLUMN owner_id VARCHAR(50);

COMMENT ON COLUMN nop_credential.scope IS '凭证归属：system|user；存量 NULL 视同 system；创建后不可变更';
COMMENT ON COLUMN nop_credential.owner_id IS 'user 级凭证归属用户 userId（system 级恒空）；创建后不可变更，换人=删旧建新';
