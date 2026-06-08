
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

alter table nop_ai_project drop constraint PK_nop_ai_project;
alter table nop_ai_project add constraint PK_nop_ai_project primary key (NOP_TENANT_ID, id);

alter table nop_ai_knowledge drop constraint PK_nop_ai_knowledge;
alter table nop_ai_knowledge add constraint PK_nop_ai_knowledge primary key (NOP_TENANT_ID, id);

alter table nop_ai_model drop constraint PK_nop_ai_model;
alter table nop_ai_model add constraint PK_nop_ai_model primary key (NOP_TENANT_ID, id);

alter table nop_ai_prompt_template drop constraint PK_nop_ai_prompt_template;
alter table nop_ai_prompt_template add constraint PK_nop_ai_prompt_template primary key (NOP_TENANT_ID, id);

alter table nop_ai_project_config drop constraint PK_nop_ai_project_config;
alter table nop_ai_project_config add constraint PK_nop_ai_project_config primary key (NOP_TENANT_ID, id);

alter table nop_ai_requirement drop constraint PK_nop_ai_requirement;
alter table nop_ai_requirement add constraint PK_nop_ai_requirement primary key (NOP_TENANT_ID, id);

alter table nop_ai_session drop constraint PK_nop_ai_session;
alter table nop_ai_session add constraint PK_nop_ai_session primary key (NOP_TENANT_ID, id);

alter table nop_ai_project_rule drop constraint PK_nop_ai_project_rule;
alter table nop_ai_project_rule add constraint PK_nop_ai_project_rule primary key (NOP_TENANT_ID, id);

alter table nop_ai_prompt_template_history drop constraint PK_nop_ai_prompt_template_history;
alter table nop_ai_prompt_template_history add constraint PK_nop_ai_prompt_template_history primary key (NOP_TENANT_ID, id);

alter table nop_ai_chat_request drop constraint PK_nop_ai_chat_request;
alter table nop_ai_chat_request add constraint PK_nop_ai_chat_request primary key (NOP_TENANT_ID, id);

alter table nop_ai_requirement_history drop constraint PK_nop_ai_requirement_history;
alter table nop_ai_requirement_history add constraint PK_nop_ai_requirement_history primary key (NOP_TENANT_ID, id);

alter table nop_ai_session_message drop constraint PK_nop_ai_session_message;
alter table nop_ai_session_message add constraint PK_nop_ai_session_message primary key (NOP_TENANT_ID, id);

alter table nop_ai_session_input drop constraint PK_nop_ai_session_input;
alter table nop_ai_session_input add constraint PK_nop_ai_session_input primary key (NOP_TENANT_ID, id);

alter table nop_ai_session_context drop constraint PK_nop_ai_session_context;
alter table nop_ai_session_context add constraint PK_nop_ai_session_context primary key (NOP_TENANT_ID, id);

alter table nop_ai_todo drop constraint PK_nop_ai_todo;
alter table nop_ai_todo add constraint PK_nop_ai_todo primary key (NOP_TENANT_ID, id);

alter table nop_ai_event drop constraint PK_nop_ai_event;
alter table nop_ai_event add constraint PK_nop_ai_event primary key (NOP_TENANT_ID, id);

alter table nop_ai_chat_response drop constraint PK_nop_ai_chat_response;
alter table nop_ai_chat_response add constraint PK_nop_ai_chat_response primary key (NOP_TENANT_ID, id);

alter table nop_ai_gen_file drop constraint PK_nop_ai_gen_file;
alter table nop_ai_gen_file add constraint PK_nop_ai_gen_file primary key (NOP_TENANT_ID, id);

alter table nop_ai_gen_file_history drop constraint PK_nop_ai_gen_file_history;
alter table nop_ai_gen_file_history add constraint PK_nop_ai_gen_file_history primary key (NOP_TENANT_ID, id);

alter table nop_ai_test_case drop constraint PK_nop_ai_test_case;
alter table nop_ai_test_case add constraint PK_nop_ai_test_case primary key (NOP_TENANT_ID, id);

alter table nop_ai_test_result drop constraint PK_nop_ai_test_result;
alter table nop_ai_test_result add constraint PK_nop_ai_test_result primary key (NOP_TENANT_ID, id);


