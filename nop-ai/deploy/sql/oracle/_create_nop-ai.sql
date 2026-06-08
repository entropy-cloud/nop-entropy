
CREATE TABLE nop_ai_project(
  ID VARCHAR2(36) NOT NULL ,
  LANGUAGE VARCHAR2(4) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  PROTOTYPE_ID VARCHAR2(36)  ,
  PROJECT_DIR VARCHAR2(400)  ,
  RUNTIME_METADATA CLOB  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_project primary key (ID)
);

CREATE TABLE nop_ai_knowledge(
  ID VARCHAR2(36) NOT NULL ,
  TITLE VARCHAR2(200) NOT NULL ,
  CONTENT VARCHAR2(4000)  ,
  FORMAT VARCHAR2(4) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_knowledge primary key (ID)
);

CREATE TABLE nop_ai_model(
  ID VARCHAR2(36) NOT NULL ,
  PROVIDER VARCHAR2(4) NOT NULL ,
  MODEL_NAME VARCHAR2(50) NOT NULL ,
  BASE_URL VARCHAR2(200)  ,
  API_KEY VARCHAR2(100)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_model primary key (ID)
);

CREATE TABLE nop_ai_prompt_template(
  ID VARCHAR2(36) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  CONTENT VARCHAR2(4000) NOT NULL ,
  CATEGORY VARCHAR2(50)  ,
  INPUTS VARCHAR2(1000)  ,
  OUTPUTS VARCHAR2(1000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_prompt_template primary key (ID)
);

CREATE TABLE nop_ai_project_config(
  ID VARCHAR2(36) NOT NULL ,
  PROJECT_ID VARCHAR2(36) NOT NULL ,
  CONFIG_NAME VARCHAR2(50) NOT NULL ,
  CONFIG_VALUE VARCHAR2(200) NOT NULL ,
  CONFIG_TYPE VARCHAR2(4) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_project_config primary key (ID)
);

CREATE TABLE nop_ai_requirement(
  ID VARCHAR2(36) NOT NULL ,
  PROJECT_ID VARCHAR2(36) NOT NULL ,
  REQ_NUMBER VARCHAR2(20) NOT NULL ,
  TITLE VARCHAR2(200) NOT NULL ,
  CONTENT VARCHAR2(4000)  ,
  VERSION CLOB NOT NULL ,
  PARENT_ID VARCHAR2(36)  ,
  TYPE VARCHAR2(4) NOT NULL ,
  AI_SUMMARY VARCHAR2(1000)  ,
  STATUS VARCHAR2(4) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_requirement primary key (ID)
);

CREATE TABLE nop_ai_session(
  ID VARCHAR2(36) NOT NULL ,
  PROJECT_ID VARCHAR2(36) NOT NULL ,
  PARENT_SESSION_ID VARCHAR2(36)  ,
  AGENT_NAME VARCHAR2(100) NOT NULL ,
  MODEL_PROVIDER VARCHAR2(4)  ,
  MODEL_NAME VARCHAR2(50)  ,
  SLUG VARCHAR2(100)  ,
  TITLE VARCHAR2(200)  ,
  DIRECTORY VARCHAR2(400)  ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  COST NUMBER(10,6)  ,
  TOKENS_INPUT INTEGER  ,
  TOKENS_OUTPUT INTEGER  ,
  TOKENS_REASONING INTEGER  ,
  TOKENS_CACHE_READ INTEGER  ,
  TOKENS_CACHE_WRITE INTEGER  ,
  TOTAL_BYTES NUMBER(20)  ,
  CONTEXT_METADATA CLOB  ,
  METADATA CLOB  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  COMPACTED_AT TIMESTAMP  ,
  ARCHIVED_AT TIMESTAMP  ,
  constraint PK_nop_ai_session primary key (ID)
);

CREATE TABLE nop_ai_project_rule(
  ID VARCHAR2(36) NOT NULL ,
  PROJECT_ID VARCHAR2(36) NOT NULL ,
  KNOWLEDGE_ID VARCHAR2(36)  ,
  RULE_NAME VARCHAR2(100) NOT NULL ,
  RULE_CONTENT VARCHAR2(4000) NOT NULL ,
  RULE_TYPE VARCHAR2(4)  ,
  IS_ACTIVE CHAR(1) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_project_rule primary key (ID)
);

CREATE TABLE nop_ai_prompt_template_history(
  ID VARCHAR2(36) NOT NULL ,
  TEMPLATE_ID VARCHAR2(36) NOT NULL ,
  VERSION VARCHAR2(10) NOT NULL ,
  CONTENT VARCHAR2(4000) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_prompt_template_history primary key (ID)
);

CREATE TABLE nop_ai_chat_request(
  ID VARCHAR2(36) NOT NULL ,
  TEMPLATE_ID VARCHAR2(36)  ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  SYSTEM_PROMPT CLOB  ,
  USER_PROMPT CLOB NOT NULL ,
  MESSAGE_TYPE INTEGER NOT NULL ,
  REQUEST_TIMESTAMP TIMESTAMP NOT NULL ,
  HASH VARCHAR2(64) NOT NULL ,
  METADATA VARCHAR2(2000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_chat_request primary key (ID)
);

CREATE TABLE nop_ai_requirement_history(
  ID VARCHAR2(36) NOT NULL ,
  REQUIREMENT_ID VARCHAR2(36) NOT NULL ,
  VERSION VARCHAR2(10) NOT NULL ,
  CONTENT VARCHAR2(4000) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_requirement_history primary key (ID)
);

CREATE TABLE nop_ai_session_message(
  ID VARCHAR2(36) NOT NULL ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  ROLE INTEGER NOT NULL ,
  SEQ NUMBER(20) NOT NULL ,
  CONTENT CLOB  ,
  TOOL_DETAILS CLOB  ,
  REASONING CLOB  ,
  METADATA CLOB  ,
  PARENT_ID VARCHAR2(36)  ,
  FINISH_REASON VARCHAR2(20)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_session_message primary key (ID)
);

CREATE TABLE nop_ai_session_input(
  ID VARCHAR2(36) NOT NULL ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  PROMPT CLOB  ,
  DELIVERY INTEGER NOT NULL ,
  ADMITTED_SEQ NUMBER(20)  ,
  PROMOTED_SEQ NUMBER(20)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_session_input primary key (ID)
);

CREATE TABLE nop_ai_session_context(
  ID VARCHAR2(36) NOT NULL ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  BASELINE CLOB  ,
  SNAPSHOT CLOB  ,
  BASELINE_SEQ NUMBER(20)  ,
  REPLACEMENT_SEQ NUMBER(20)  ,
  REVISION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_session_context primary key (ID)
);

CREATE TABLE nop_ai_todo(
  ID VARCHAR2(36) NOT NULL ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  PLAN_ID VARCHAR2(36)  ,
  CONTENT VARCHAR2(1000) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  PRIORITY INTEGER NOT NULL ,
  POSITION INTEGER NOT NULL ,
  DEPENDS_ON CLOB  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_todo primary key (ID)
);

CREATE TABLE nop_ai_event(
  ID VARCHAR2(36) NOT NULL ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  SEQ NUMBER(20) NOT NULL ,
  EVENT_TYPE INTEGER NOT NULL ,
  DATA CLOB  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_event primary key (ID)
);

CREATE TABLE nop_ai_chat_response(
  ID VARCHAR2(36) NOT NULL ,
  REQUEST_ID VARCHAR2(36) NOT NULL ,
  SESSION_ID VARCHAR2(36) NOT NULL ,
  MODEL_ID VARCHAR2(36) NOT NULL ,
  AI_PROVIDER VARCHAR2(4) NOT NULL ,
  AI_MODEL VARCHAR2(50) NOT NULL ,
  RESPONSE_CONTENT CLOB NOT NULL ,
  RESPONSE_TIMESTAMP TIMESTAMP NOT NULL ,
  PROMPT_TOKENS INTEGER  ,
  COMPLETION_TOKENS INTEGER  ,
  RESPONSE_DURATION_MS INTEGER  ,
  CORRECTNESS_SCORE NUMBER(5,2)  ,
  PERFORMANCE_SCORE NUMBER(5,2)  ,
  READABILITY_SCORE NUMBER(5,2)  ,
  COMPLIANCE_SCORE NUMBER(5,2)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_chat_response primary key (ID)
);

CREATE TABLE nop_ai_gen_file(
  ID VARCHAR2(36) NOT NULL ,
  PROJECT_ID VARCHAR2(36) NOT NULL ,
  REQUIREMENT_ID VARCHAR2(36)  ,
  MODULE_TYPE VARCHAR2(4) NOT NULL ,
  CONTENT CLOB NOT NULL ,
  FILE_PATH VARCHAR2(200) NOT NULL ,
  CHAT_RESPONSE_ID VARCHAR2(36)  ,
  STATUS VARCHAR2(4) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_gen_file primary key (ID)
);

CREATE TABLE nop_ai_gen_file_history(
  ID VARCHAR2(36) NOT NULL ,
  GEN_FILE_ID VARCHAR2(36) NOT NULL ,
  VERSION VARCHAR2(10) NOT NULL ,
  CONTENT CLOB NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_gen_file_history primary key (ID)
);

CREATE TABLE nop_ai_test_case(
  ID VARCHAR2(36) NOT NULL ,
  REQUIREMENT_ID VARCHAR2(36) NOT NULL ,
  TEST_CONTENT VARCHAR2(2000) NOT NULL ,
  TEST_DATA VARCHAR2(1000)  ,
  GEN_FILE_ID VARCHAR2(36)  ,
  CHAT_RESPONSE_ID VARCHAR2(36)  ,
  STATUS VARCHAR2(4) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_test_case primary key (ID)
);

CREATE TABLE nop_ai_test_result(
  ID VARCHAR2(36) NOT NULL ,
  TEST_CASE_ID VARCHAR2(36) NOT NULL ,
  EXECUTION_TIME TIMESTAMP NOT NULL ,
  SUCCESS CHAR(1) NOT NULL ,
  ERROR_LOG VARCHAR2(2000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_test_result primary key (ID)
);


      COMMENT ON TABLE nop_ai_project IS 'AI项目';
                
      COMMENT ON COLUMN nop_ai_project.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_project.LANGUAGE IS '项目语言';
                    
      COMMENT ON COLUMN nop_ai_project.NAME IS '项目名称';
                    
      COMMENT ON COLUMN nop_ai_project.PROTOTYPE_ID IS '模板项目ID';
                    
      COMMENT ON COLUMN nop_ai_project.PROJECT_DIR IS '项目目录';
                    
      COMMENT ON COLUMN nop_ai_project.RUNTIME_METADATA IS '运行时元数据';
                    
      COMMENT ON COLUMN nop_ai_project.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_project.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_project.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_project.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_project.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_knowledge IS '知识库';
                
      COMMENT ON COLUMN nop_ai_knowledge.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_knowledge.TITLE IS '标题';
                    
      COMMENT ON COLUMN nop_ai_knowledge.CONTENT IS '内容';
                    
      COMMENT ON COLUMN nop_ai_knowledge.FORMAT IS '格式类型';
                    
      COMMENT ON COLUMN nop_ai_knowledge.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_knowledge.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_knowledge.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_knowledge.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_knowledge.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_model IS 'AI模型';
                
      COMMENT ON COLUMN nop_ai_model.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_model.PROVIDER IS '供应商';
                    
      COMMENT ON COLUMN nop_ai_model.MODEL_NAME IS '模型名称';
                    
      COMMENT ON COLUMN nop_ai_model.BASE_URL IS 'API地址';
                    
      COMMENT ON COLUMN nop_ai_model.API_KEY IS 'API密钥';
                    
      COMMENT ON COLUMN nop_ai_model.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_model.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_model.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_model.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_model.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_prompt_template IS '提示词模板';
                
      COMMENT ON COLUMN nop_ai_prompt_template.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.NAME IS '模板名称';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.CONTENT IS '模板内容';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.CATEGORY IS '分类';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.INPUTS IS '输入规范';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.OUTPUTS IS '输出规范';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_project_config IS '项目配置';
                
      COMMENT ON COLUMN nop_ai_project_config.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_project_config.PROJECT_ID IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_project_config.CONFIG_NAME IS '配置名称';
                    
      COMMENT ON COLUMN nop_ai_project_config.CONFIG_VALUE IS '配置值';
                    
      COMMENT ON COLUMN nop_ai_project_config.CONFIG_TYPE IS '配置类型';
                    
      COMMENT ON COLUMN nop_ai_project_config.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_project_config.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_project_config.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_project_config.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_project_config.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_requirement IS '需求条目';
                
      COMMENT ON COLUMN nop_ai_requirement.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_requirement.PROJECT_ID IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_requirement.REQ_NUMBER IS '需求编号';
                    
      COMMENT ON COLUMN nop_ai_requirement.TITLE IS '需求标题';
                    
      COMMENT ON COLUMN nop_ai_requirement.CONTENT IS '需求内容';
                    
      COMMENT ON COLUMN nop_ai_requirement.VERSION IS '当前版本';
                    
      COMMENT ON COLUMN nop_ai_requirement.PARENT_ID IS '父需求ID';
                    
      COMMENT ON COLUMN nop_ai_requirement.TYPE IS '需求类型';
                    
      COMMENT ON COLUMN nop_ai_requirement.AI_SUMMARY IS 'AI摘要';
                    
      COMMENT ON COLUMN nop_ai_requirement.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_ai_requirement.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_requirement.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_requirement.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_requirement.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session IS 'Agent会话';
                
      COMMENT ON COLUMN nop_ai_session.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session.PROJECT_ID IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_session.PARENT_SESSION_ID IS '父会话ID';
                    
      COMMENT ON COLUMN nop_ai_session.AGENT_NAME IS 'Agent名称';
                    
      COMMENT ON COLUMN nop_ai_session.MODEL_PROVIDER IS '模型供应商';
                    
      COMMENT ON COLUMN nop_ai_session.MODEL_NAME IS '模型名称';
                    
      COMMENT ON COLUMN nop_ai_session.SLUG IS '会话Slug';
                    
      COMMENT ON COLUMN nop_ai_session.TITLE IS '会话标题';
                    
      COMMENT ON COLUMN nop_ai_session.DIRECTORY IS '工作目录';
                    
      COMMENT ON COLUMN nop_ai_session.STATUS IS '会话状态';
                    
      COMMENT ON COLUMN nop_ai_session.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_session.COST IS '费用';
                    
      COMMENT ON COLUMN nop_ai_session.TOKENS_INPUT IS '输入Token数';
                    
      COMMENT ON COLUMN nop_ai_session.TOKENS_OUTPUT IS '输出Token数';
                    
      COMMENT ON COLUMN nop_ai_session.TOKENS_REASONING IS '推理Token数';
                    
      COMMENT ON COLUMN nop_ai_session.TOKENS_CACHE_READ IS '缓存读取Token数';
                    
      COMMENT ON COLUMN nop_ai_session.TOKENS_CACHE_WRITE IS '缓存写入Token数';
                    
      COMMENT ON COLUMN nop_ai_session.TOTAL_BYTES IS '总字节数';
                    
      COMMENT ON COLUMN nop_ai_session.CONTEXT_METADATA IS '上下文元数据';
                    
      COMMENT ON COLUMN nop_ai_session.METADATA IS '元数据';
                    
      COMMENT ON COLUMN nop_ai_session.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_ai_session.COMPACTED_AT IS '压缩时间';
                    
      COMMENT ON COLUMN nop_ai_session.ARCHIVED_AT IS '归档时间';
                    
      COMMENT ON TABLE nop_ai_project_rule IS '项目规则';
                
      COMMENT ON COLUMN nop_ai_project_rule.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_project_rule.PROJECT_ID IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_project_rule.KNOWLEDGE_ID IS '知识库ID';
                    
      COMMENT ON COLUMN nop_ai_project_rule.RULE_NAME IS '规则名称';
                    
      COMMENT ON COLUMN nop_ai_project_rule.RULE_CONTENT IS '规则内容';
                    
      COMMENT ON COLUMN nop_ai_project_rule.RULE_TYPE IS '规则类型';
                    
      COMMENT ON COLUMN nop_ai_project_rule.IS_ACTIVE IS '是否启用';
                    
      COMMENT ON COLUMN nop_ai_project_rule.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_project_rule.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_project_rule.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_project_rule.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_project_rule.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_prompt_template_history IS '模板历史';
                
      COMMENT ON COLUMN nop_ai_prompt_template_history.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.TEMPLATE_ID IS '模板ID';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.VERSION IS '版本号';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.CONTENT IS '模板内容';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_chat_request IS '对话请求';
                
      COMMENT ON COLUMN nop_ai_chat_request.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_chat_request.TEMPLATE_ID IS '模板ID';
                    
      COMMENT ON COLUMN nop_ai_chat_request.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_chat_request.SYSTEM_PROMPT IS '系统提示词';
                    
      COMMENT ON COLUMN nop_ai_chat_request.USER_PROMPT IS '用户提示词';
                    
      COMMENT ON COLUMN nop_ai_chat_request.MESSAGE_TYPE IS '消息类型';
                    
      COMMENT ON COLUMN nop_ai_chat_request.REQUEST_TIMESTAMP IS '请求时间戳';
                    
      COMMENT ON COLUMN nop_ai_chat_request.HASH IS '内容哈希';
                    
      COMMENT ON COLUMN nop_ai_chat_request.METADATA IS '元数据';
                    
      COMMENT ON COLUMN nop_ai_chat_request.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_chat_request.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_chat_request.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_chat_request.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_chat_request.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_requirement_history IS '需求历史';
                
      COMMENT ON COLUMN nop_ai_requirement_history.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.REQUIREMENT_ID IS '需求ID';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.VERSION IS '版本号';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.CONTENT IS '需求内容';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session_message IS '会话消息';
                
      COMMENT ON COLUMN nop_ai_session_message.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session_message.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_session_message.ROLE IS '消息角色';
                    
      COMMENT ON COLUMN nop_ai_session_message.SEQ IS '序号';
                    
      COMMENT ON COLUMN nop_ai_session_message.CONTENT IS '消息内容';
                    
      COMMENT ON COLUMN nop_ai_session_message.TOOL_DETAILS IS '工具详情';
                    
      COMMENT ON COLUMN nop_ai_session_message.REASONING IS '推理内容';
                    
      COMMENT ON COLUMN nop_ai_session_message.METADATA IS '元数据';
                    
      COMMENT ON COLUMN nop_ai_session_message.PARENT_ID IS '父消息ID';
                    
      COMMENT ON COLUMN nop_ai_session_message.FINISH_REASON IS '停止原因';
                    
      COMMENT ON COLUMN nop_ai_session_message.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_session_message.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session_message.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session_message.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session_message.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session_input IS '会话输入';
                
      COMMENT ON COLUMN nop_ai_session_input.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session_input.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_session_input.PROMPT IS '输入提示';
                    
      COMMENT ON COLUMN nop_ai_session_input.DELIVERY IS '投递方式';
                    
      COMMENT ON COLUMN nop_ai_session_input.ADMITTED_SEQ IS '接纳序号';
                    
      COMMENT ON COLUMN nop_ai_session_input.PROMOTED_SEQ IS '提升序号';
                    
      COMMENT ON COLUMN nop_ai_session_input.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_session_input.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session_input.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session_input.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session_input.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session_context IS '上下文快照';
                
      COMMENT ON COLUMN nop_ai_session_context.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session_context.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_session_context.BASELINE IS '基线内容';
                    
      COMMENT ON COLUMN nop_ai_session_context.SNAPSHOT IS '上下文快照';
                    
      COMMENT ON COLUMN nop_ai_session_context.BASELINE_SEQ IS '基线序号';
                    
      COMMENT ON COLUMN nop_ai_session_context.REPLACEMENT_SEQ IS '替换序号';
                    
      COMMENT ON COLUMN nop_ai_session_context.REVISION IS '版本';
                    
      COMMENT ON COLUMN nop_ai_session_context.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session_context.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session_context.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session_context.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_todo IS 'Agent待办';
                
      COMMENT ON COLUMN nop_ai_todo.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_todo.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_todo.PLAN_ID IS '计划ID';
                    
      COMMENT ON COLUMN nop_ai_todo.CONTENT IS '待办内容';
                    
      COMMENT ON COLUMN nop_ai_todo.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_ai_todo.PRIORITY IS '优先级';
                    
      COMMENT ON COLUMN nop_ai_todo.POSITION IS '位置';
                    
      COMMENT ON COLUMN nop_ai_todo.DEPENDS_ON IS '依赖项';
                    
      COMMENT ON COLUMN nop_ai_todo.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_todo.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_todo.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_todo.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_todo.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_event IS 'Agent事件';
                
      COMMENT ON COLUMN nop_ai_event.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_event.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_event.SEQ IS '序号';
                    
      COMMENT ON COLUMN nop_ai_event.EVENT_TYPE IS '事件类型';
                    
      COMMENT ON COLUMN nop_ai_event.DATA IS '事件数据';
                    
      COMMENT ON COLUMN nop_ai_event.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_event.CREATE_TIME IS '创建时间';
                    
      COMMENT ON TABLE nop_ai_chat_response IS '响应结果';
                
      COMMENT ON COLUMN nop_ai_chat_response.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_chat_response.REQUEST_ID IS '请求ID';
                    
      COMMENT ON COLUMN nop_ai_chat_response.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_chat_response.MODEL_ID IS '模型ID';
                    
      COMMENT ON COLUMN nop_ai_chat_response.AI_PROVIDER IS '供应商';
                    
      COMMENT ON COLUMN nop_ai_chat_response.AI_MODEL IS '模型名称';
                    
      COMMENT ON COLUMN nop_ai_chat_response.RESPONSE_CONTENT IS '响应内容';
                    
      COMMENT ON COLUMN nop_ai_chat_response.RESPONSE_TIMESTAMP IS '响应时间戳';
                    
      COMMENT ON COLUMN nop_ai_chat_response.PROMPT_TOKENS IS '请求Token数';
                    
      COMMENT ON COLUMN nop_ai_chat_response.COMPLETION_TOKENS IS '响应Token数';
                    
      COMMENT ON COLUMN nop_ai_chat_response.RESPONSE_DURATION_MS IS '响应耗时(毫秒)';
                    
      COMMENT ON COLUMN nop_ai_chat_response.CORRECTNESS_SCORE IS '正确性分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.PERFORMANCE_SCORE IS '性能分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.READABILITY_SCORE IS '可读性分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.COMPLIANCE_SCORE IS '合规性分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_chat_response.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_chat_response.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_chat_response.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_chat_response.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_gen_file IS '生成文件';
                
      COMMENT ON COLUMN nop_ai_gen_file.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_gen_file.PROJECT_ID IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file.REQUIREMENT_ID IS '需求ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file.MODULE_TYPE IS '模块类型';
                    
      COMMENT ON COLUMN nop_ai_gen_file.CONTENT IS '文件内容';
                    
      COMMENT ON COLUMN nop_ai_gen_file.FILE_PATH IS '文件路径';
                    
      COMMENT ON COLUMN nop_ai_gen_file.CHAT_RESPONSE_ID IS '响应ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_ai_gen_file.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_gen_file.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_gen_file.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_gen_file.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_gen_file.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_gen_file_history IS '文件历史';
                
      COMMENT ON COLUMN nop_ai_gen_file_history.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.GEN_FILE_ID IS '文件ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.VERSION IS '版本号';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.CONTENT IS '文件内容';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_test_case IS '测试用例';
                
      COMMENT ON COLUMN nop_ai_test_case.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_test_case.REQUIREMENT_ID IS '需求ID';
                    
      COMMENT ON COLUMN nop_ai_test_case.TEST_CONTENT IS '测试内容';
                    
      COMMENT ON COLUMN nop_ai_test_case.TEST_DATA IS '测试数据';
                    
      COMMENT ON COLUMN nop_ai_test_case.GEN_FILE_ID IS '关联文件ID';
                    
      COMMENT ON COLUMN nop_ai_test_case.CHAT_RESPONSE_ID IS '响应ID';
                    
      COMMENT ON COLUMN nop_ai_test_case.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_ai_test_case.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_test_case.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_test_case.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_test_case.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_test_case.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_test_result IS '测试结果';
                
      COMMENT ON COLUMN nop_ai_test_result.ID IS '主键';
                    
      COMMENT ON COLUMN nop_ai_test_result.TEST_CASE_ID IS '测试用例ID';
                    
      COMMENT ON COLUMN nop_ai_test_result.EXECUTION_TIME IS '执行时间';
                    
      COMMENT ON COLUMN nop_ai_test_result.SUCCESS IS '是否成功';
                    
      COMMENT ON COLUMN nop_ai_test_result.ERROR_LOG IS '错误日志';
                    
      COMMENT ON COLUMN nop_ai_test_result.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_test_result.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_test_result.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_test_result.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_test_result.UPDATE_TIME IS '修改时间';
                    
