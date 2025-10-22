
    alter table nop_rule_definition add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_node add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_role add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_log add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_rule_definition drop constraint PK_nop_rule_definition;
alter table nop_rule_definition add constraint PK_nop_rule_definition primary key (NOP_TENANT_ID, RULE_ID);

alter table nop_rule_node drop constraint PK_nop_rule_node;
alter table nop_rule_node add constraint PK_nop_rule_node primary key (NOP_TENANT_ID, SID);

alter table nop_rule_role drop constraint PK_nop_rule_role;
alter table nop_rule_role add constraint PK_nop_rule_role primary key (NOP_TENANT_ID, SID);

alter table nop_rule_log drop constraint PK_nop_rule_log;
alter table nop_rule_log add constraint PK_nop_rule_log primary key (NOP_TENANT_ID, SID);


