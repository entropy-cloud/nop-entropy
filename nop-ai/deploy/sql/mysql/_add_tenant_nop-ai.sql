
    alter table nop_ai_project add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_knowledge add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_model add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_prompt_template add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_project_config add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_requirement add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_session add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_project_rule add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_prompt_template_history add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_chat_request add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_requirement_history add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_session_message add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_session_input add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_session_context add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_todo add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_event add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_chat_response add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_gen_file add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_gen_file_history add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_test_case add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_test_result add NOP_TENANT_ID VARCHAR(32) DEFAULT '0' NOT NULL;

alter table nop_ai_project drop primary key;
alter table nop_ai_project add primary key (NOP_TENANT_ID, id);

alter table nop_ai_knowledge drop primary key;
alter table nop_ai_knowledge add primary key (NOP_TENANT_ID, id);

alter table nop_ai_model drop primary key;
alter table nop_ai_model add primary key (NOP_TENANT_ID, id);

alter table nop_ai_prompt_template drop primary key;
alter table nop_ai_prompt_template add primary key (NOP_TENANT_ID, id);

alter table nop_ai_project_config drop primary key;
alter table nop_ai_project_config add primary key (NOP_TENANT_ID, id);

alter table nop_ai_requirement drop primary key;
alter table nop_ai_requirement add primary key (NOP_TENANT_ID, id);

alter table nop_ai_session drop primary key;
alter table nop_ai_session add primary key (NOP_TENANT_ID, id);

alter table nop_ai_project_rule drop primary key;
alter table nop_ai_project_rule add primary key (NOP_TENANT_ID, id);

alter table nop_ai_prompt_template_history drop primary key;
alter table nop_ai_prompt_template_history add primary key (NOP_TENANT_ID, id);

alter table nop_ai_chat_request drop primary key;
alter table nop_ai_chat_request add primary key (NOP_TENANT_ID, id);

alter table nop_ai_requirement_history drop primary key;
alter table nop_ai_requirement_history add primary key (NOP_TENANT_ID, id);

alter table nop_ai_session_message drop primary key;
alter table nop_ai_session_message add primary key (NOP_TENANT_ID, id);

alter table nop_ai_session_input drop primary key;
alter table nop_ai_session_input add primary key (NOP_TENANT_ID, id);

alter table nop_ai_session_context drop primary key;
alter table nop_ai_session_context add primary key (NOP_TENANT_ID, id);

alter table nop_ai_todo drop primary key;
alter table nop_ai_todo add primary key (NOP_TENANT_ID, id);

alter table nop_ai_event drop primary key;
alter table nop_ai_event add primary key (NOP_TENANT_ID, id);

alter table nop_ai_chat_response drop primary key;
alter table nop_ai_chat_response add primary key (NOP_TENANT_ID, id);

alter table nop_ai_gen_file drop primary key;
alter table nop_ai_gen_file add primary key (NOP_TENANT_ID, id);

alter table nop_ai_gen_file_history drop primary key;
alter table nop_ai_gen_file_history add primary key (NOP_TENANT_ID, id);

alter table nop_ai_test_case drop primary key;
alter table nop_ai_test_case add primary key (NOP_TENANT_ID, id);

alter table nop_ai_test_result drop primary key;
alter table nop_ai_test_result add primary key (NOP_TENANT_ID, id);


