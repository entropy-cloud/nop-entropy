
    alter table nop_rule_definition add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_log add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_node add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_role add column NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_definition drop primary key;
alter table nop_rule_definition add primary key (NOP_TENANT_ID, RULE_ID);

alter table nop_rule_log drop primary key;
alter table nop_rule_log add primary key (NOP_TENANT_ID, SID);

alter table nop_rule_node drop primary key;
alter table nop_rule_node add primary key (NOP_TENANT_ID, SID);

alter table nop_rule_role drop primary key;
alter table nop_rule_role add primary key (NOP_TENANT_ID, SID);


