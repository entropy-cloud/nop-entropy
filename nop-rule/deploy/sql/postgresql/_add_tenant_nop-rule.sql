
    alter table nop_rule_definition add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_log add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_node add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_role add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;


