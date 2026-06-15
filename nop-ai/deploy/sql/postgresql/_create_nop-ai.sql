
CREATE TABLE nop_ai_project(
  id VARCHAR(36) NOT NULL ,
  language VARCHAR(4) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  prototype_id VARCHAR(36)  ,
  project_dir VARCHAR(400)  ,
  runtime_metadata TEXT  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_project primary key (id)
);

CREATE TABLE nop_ai_knowledge(
  id VARCHAR(36) NOT NULL ,
  title VARCHAR(200) NOT NULL ,
  content VARCHAR(4000)  ,
  format VARCHAR(4) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_knowledge primary key (id)
);

CREATE TABLE nop_ai_model(
  id VARCHAR(36) NOT NULL ,
  provider VARCHAR(4) NOT NULL ,
  model_name VARCHAR(50) NOT NULL ,
  base_url VARCHAR(200)  ,
  api_key VARCHAR(100)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  input_price_per_1m NUMERIC(10,4)  ,
  output_price_per_1m NUMERIC(10,4)  ,
  reasoning_price_per_1m NUMERIC(10,4)  ,
  cache_read_price_per_1m NUMERIC(10,4)  ,
  cache_write_price_per_1m NUMERIC(10,4)  ,
  currency VARCHAR(3)  ,
  constraint PK_nop_ai_model primary key (id)
);

CREATE TABLE nop_ai_prompt_template(
  id VARCHAR(36) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  content VARCHAR(4000) NOT NULL ,
  category VARCHAR(50)  ,
  inputs VARCHAR(1000)  ,
  outputs VARCHAR(1000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_prompt_template primary key (id)
);

CREATE TABLE nop_ai_project_config(
  id VARCHAR(36) NOT NULL ,
  project_id VARCHAR(36) NOT NULL ,
  config_name VARCHAR(50) NOT NULL ,
  config_value VARCHAR(200) NOT NULL ,
  config_type VARCHAR(4) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_project_config primary key (id)
);

CREATE TABLE nop_ai_requirement(
  id VARCHAR(36) NOT NULL ,
  project_id VARCHAR(36) NOT NULL ,
  req_number VARCHAR(20) NOT NULL ,
  title VARCHAR(200) NOT NULL ,
  content VARCHAR(4000)  ,
  version TEXT NOT NULL ,
  parent_id VARCHAR(36)  ,
  type VARCHAR(4) NOT NULL ,
  ai_summary VARCHAR(1000)  ,
  status VARCHAR(4) NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_requirement primary key (id)
);

CREATE TABLE nop_ai_session(
  id VARCHAR(36) NOT NULL ,
  project_id VARCHAR(36) NOT NULL ,
  parent_session_id VARCHAR(36)  ,
  agent_name VARCHAR(100) NOT NULL ,
  model_provider VARCHAR(4)  ,
  model_name VARCHAR(50)  ,
  slug VARCHAR(100)  ,
  title VARCHAR(200)  ,
  directory VARCHAR(400)  ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  cost NUMERIC(10,6)  ,
  tokens_input INT4  ,
  tokens_output INT4  ,
  tokens_reasoning INT4  ,
  tokens_cache_read INT4  ,
  tokens_cache_write INT4  ,
  total_bytes INT8  ,
  context_metadata TEXT  ,
  metadata TEXT  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  compacted_at TIMESTAMP  ,
  archived_at TIMESTAMP  ,
  constraint PK_nop_ai_session primary key (id)
);

CREATE TABLE nop_ai_project_rule(
  id VARCHAR(36) NOT NULL ,
  project_id VARCHAR(36) NOT NULL ,
  knowledge_id VARCHAR(36)  ,
  rule_name VARCHAR(100) NOT NULL ,
  rule_content VARCHAR(4000) NOT NULL ,
  rule_type VARCHAR(4)  ,
  is_active BOOLEAN NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_project_rule primary key (id)
);

CREATE TABLE nop_ai_prompt_template_history(
  id VARCHAR(36) NOT NULL ,
  template_id VARCHAR(36) NOT NULL ,
  version VARCHAR(10) NOT NULL ,
  content VARCHAR(4000) NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_prompt_template_history primary key (id)
);

CREATE TABLE nop_ai_chat_request(
  id VARCHAR(36) NOT NULL ,
  template_id VARCHAR(36)  ,
  session_id VARCHAR(36) NOT NULL ,
  system_prompt TEXT  ,
  user_prompt TEXT NOT NULL ,
  message_type INT4 NOT NULL ,
  request_timestamp TIMESTAMP NOT NULL ,
  hash VARCHAR(64) NOT NULL ,
  metadata VARCHAR(2000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_chat_request primary key (id)
);

CREATE TABLE nop_ai_requirement_history(
  id VARCHAR(36) NOT NULL ,
  requirement_id VARCHAR(36) NOT NULL ,
  version VARCHAR(10) NOT NULL ,
  content VARCHAR(4000) NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_requirement_history primary key (id)
);

CREATE TABLE nop_ai_session_message(
  id VARCHAR(36) NOT NULL ,
  session_id VARCHAR(36) NOT NULL ,
  role INT4 NOT NULL ,
  seq INT8 NOT NULL ,
  content TEXT  ,
  tool_details TEXT  ,
  reasoning TEXT  ,
  metadata TEXT  ,
  parent_id VARCHAR(36)  ,
  finish_reason VARCHAR(20)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_session_message primary key (id)
);

CREATE TABLE nop_ai_session_input(
  id VARCHAR(36) NOT NULL ,
  session_id VARCHAR(36) NOT NULL ,
  prompt TEXT  ,
  delivery INT4 NOT NULL ,
  admitted_seq INT8  ,
  promoted_seq INT8  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_session_input primary key (id)
);

CREATE TABLE nop_ai_session_context(
  id VARCHAR(36) NOT NULL ,
  session_id VARCHAR(36) NOT NULL ,
  baseline TEXT  ,
  snapshot TEXT  ,
  baseline_seq INT8  ,
  replacement_seq INT8  ,
  revision INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_session_context primary key (id)
);

CREATE TABLE nop_ai_todo(
  id VARCHAR(36) NOT NULL ,
  session_id VARCHAR(36) NOT NULL ,
  plan_id VARCHAR(36)  ,
  content VARCHAR(1000) NOT NULL ,
  status INT4 NOT NULL ,
  priority INT4 NOT NULL ,
  position INT4 NOT NULL ,
  depends_on TEXT  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_todo primary key (id)
);

CREATE TABLE nop_ai_event(
  id VARCHAR(36) NOT NULL ,
  session_id VARCHAR(36) NOT NULL ,
  seq INT8 NOT NULL ,
  event_type INT4 NOT NULL ,
  data TEXT  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_event primary key (id)
);

CREATE TABLE nop_ai_chat_response(
  id VARCHAR(36) NOT NULL ,
  request_id VARCHAR(36) NOT NULL ,
  session_id VARCHAR(36) NOT NULL ,
  model_id VARCHAR(36) NOT NULL ,
  ai_provider VARCHAR(4) NOT NULL ,
  ai_model VARCHAR(50) NOT NULL ,
  response_content TEXT NOT NULL ,
  response_timestamp TIMESTAMP NOT NULL ,
  prompt_tokens INT4  ,
  completion_tokens INT4  ,
  response_duration_ms INT4  ,
  correctness_score NUMERIC(5,2)  ,
  performance_score NUMERIC(5,2)  ,
  readability_score NUMERIC(5,2)  ,
  compliance_score NUMERIC(5,2)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_chat_response primary key (id)
);

CREATE TABLE nop_ai_gen_file(
  id VARCHAR(36) NOT NULL ,
  project_id VARCHAR(36) NOT NULL ,
  requirement_id VARCHAR(36)  ,
  module_type VARCHAR(4) NOT NULL ,
  content TEXT NOT NULL ,
  file_path VARCHAR(200) NOT NULL ,
  chat_response_id VARCHAR(36)  ,
  status VARCHAR(4) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_gen_file primary key (id)
);

CREATE TABLE nop_ai_gen_file_history(
  id VARCHAR(36) NOT NULL ,
  gen_file_id VARCHAR(36) NOT NULL ,
  version VARCHAR(10) NOT NULL ,
  content TEXT NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_gen_file_history primary key (id)
);

CREATE TABLE nop_ai_test_case(
  id VARCHAR(36) NOT NULL ,
  requirement_id VARCHAR(36) NOT NULL ,
  test_content VARCHAR(2000) NOT NULL ,
  test_data VARCHAR(1000)  ,
  gen_file_id VARCHAR(36)  ,
  chat_response_id VARCHAR(36)  ,
  status VARCHAR(4) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_test_case primary key (id)
);

CREATE TABLE nop_ai_test_result(
  id VARCHAR(36) NOT NULL ,
  test_case_id VARCHAR(36) NOT NULL ,
  execution_time TIMESTAMP NOT NULL ,
  success BOOLEAN NOT NULL ,
  error_log VARCHAR(2000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_ai_test_result primary key (id)
);


      COMMENT ON TABLE nop_ai_project IS 'AI项目';
                
      COMMENT ON COLUMN nop_ai_project.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_project.language IS '项目语言';
                    
      COMMENT ON COLUMN nop_ai_project.name IS '项目名称';
                    
      COMMENT ON COLUMN nop_ai_project.prototype_id IS '模板项目ID';
                    
      COMMENT ON COLUMN nop_ai_project.project_dir IS '项目目录';
                    
      COMMENT ON COLUMN nop_ai_project.runtime_metadata IS '运行时元数据';
                    
      COMMENT ON COLUMN nop_ai_project.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_project.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_project.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_project.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_project.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_knowledge IS '知识库';
                
      COMMENT ON COLUMN nop_ai_knowledge.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_knowledge.title IS '标题';
                    
      COMMENT ON COLUMN nop_ai_knowledge.content IS '内容';
                    
      COMMENT ON COLUMN nop_ai_knowledge.format IS '格式类型';
                    
      COMMENT ON COLUMN nop_ai_knowledge.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_knowledge.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_knowledge.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_knowledge.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_knowledge.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_model IS 'AI模型';
                
      COMMENT ON COLUMN nop_ai_model.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_model.provider IS '供应商';
                    
      COMMENT ON COLUMN nop_ai_model.model_name IS '模型名称';
                    
      COMMENT ON COLUMN nop_ai_model.base_url IS 'API地址';
                    
      COMMENT ON COLUMN nop_ai_model.api_key IS 'API密钥';
                    
      COMMENT ON COLUMN nop_ai_model.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_model.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_model.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_model.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_model.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_ai_model.input_price_per_1m IS '输入单价';
                    
      COMMENT ON COLUMN nop_ai_model.output_price_per_1m IS '输出单价';
                    
      COMMENT ON COLUMN nop_ai_model.reasoning_price_per_1m IS '推理单价';
                    
      COMMENT ON COLUMN nop_ai_model.cache_read_price_per_1m IS '缓存读单价';
                    
      COMMENT ON COLUMN nop_ai_model.cache_write_price_per_1m IS '缓存写单价';
                    
      COMMENT ON COLUMN nop_ai_model.currency IS '币种';
                    
      COMMENT ON TABLE nop_ai_prompt_template IS '提示词模板';
                
      COMMENT ON COLUMN nop_ai_prompt_template.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.name IS '模板名称';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.content IS '模板内容';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.category IS '分类';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.inputs IS '输入规范';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.outputs IS '输出规范';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_project_config IS '项目配置';
                
      COMMENT ON COLUMN nop_ai_project_config.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_project_config.project_id IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_project_config.config_name IS '配置名称';
                    
      COMMENT ON COLUMN nop_ai_project_config.config_value IS '配置值';
                    
      COMMENT ON COLUMN nop_ai_project_config.config_type IS '配置类型';
                    
      COMMENT ON COLUMN nop_ai_project_config.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_project_config.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_project_config.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_project_config.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_project_config.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_requirement IS '需求条目';
                
      COMMENT ON COLUMN nop_ai_requirement.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_requirement.project_id IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_requirement.req_number IS '需求编号';
                    
      COMMENT ON COLUMN nop_ai_requirement.title IS '需求标题';
                    
      COMMENT ON COLUMN nop_ai_requirement.content IS '需求内容';
                    
      COMMENT ON COLUMN nop_ai_requirement.version IS '当前版本';
                    
      COMMENT ON COLUMN nop_ai_requirement.parent_id IS '父需求ID';
                    
      COMMENT ON COLUMN nop_ai_requirement.type IS '需求类型';
                    
      COMMENT ON COLUMN nop_ai_requirement.ai_summary IS 'AI摘要';
                    
      COMMENT ON COLUMN nop_ai_requirement.status IS '状态';
                    
      COMMENT ON COLUMN nop_ai_requirement.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_requirement.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_requirement.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_requirement.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session IS 'Agent会话';
                
      COMMENT ON COLUMN nop_ai_session.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session.project_id IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_session.parent_session_id IS '父会话ID';
                    
      COMMENT ON COLUMN nop_ai_session.agent_name IS 'Agent名称';
                    
      COMMENT ON COLUMN nop_ai_session.model_provider IS '模型供应商';
                    
      COMMENT ON COLUMN nop_ai_session.model_name IS '模型名称';
                    
      COMMENT ON COLUMN nop_ai_session.slug IS '会话Slug';
                    
      COMMENT ON COLUMN nop_ai_session.title IS '会话标题';
                    
      COMMENT ON COLUMN nop_ai_session.directory IS '工作目录';
                    
      COMMENT ON COLUMN nop_ai_session.status IS '会话状态';
                    
      COMMENT ON COLUMN nop_ai_session.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_session.cost IS '费用';
                    
      COMMENT ON COLUMN nop_ai_session.tokens_input IS '输入Token数';
                    
      COMMENT ON COLUMN nop_ai_session.tokens_output IS '输出Token数';
                    
      COMMENT ON COLUMN nop_ai_session.tokens_reasoning IS '推理Token数';
                    
      COMMENT ON COLUMN nop_ai_session.tokens_cache_read IS '缓存读取Token数';
                    
      COMMENT ON COLUMN nop_ai_session.tokens_cache_write IS '缓存写入Token数';
                    
      COMMENT ON COLUMN nop_ai_session.total_bytes IS '总字节数';
                    
      COMMENT ON COLUMN nop_ai_session.context_metadata IS '上下文元数据';
                    
      COMMENT ON COLUMN nop_ai_session.metadata IS '元数据';
                    
      COMMENT ON COLUMN nop_ai_session.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_ai_session.compacted_at IS '压缩时间';
                    
      COMMENT ON COLUMN nop_ai_session.archived_at IS '归档时间';
                    
      COMMENT ON TABLE nop_ai_project_rule IS '项目规则';
                
      COMMENT ON COLUMN nop_ai_project_rule.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_project_rule.project_id IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_project_rule.knowledge_id IS '知识库ID';
                    
      COMMENT ON COLUMN nop_ai_project_rule.rule_name IS '规则名称';
                    
      COMMENT ON COLUMN nop_ai_project_rule.rule_content IS '规则内容';
                    
      COMMENT ON COLUMN nop_ai_project_rule.rule_type IS '规则类型';
                    
      COMMENT ON COLUMN nop_ai_project_rule.is_active IS '是否启用';
                    
      COMMENT ON COLUMN nop_ai_project_rule.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_project_rule.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_project_rule.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_project_rule.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_project_rule.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_prompt_template_history IS '模板历史';
                
      COMMENT ON COLUMN nop_ai_prompt_template_history.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.template_id IS '模板ID';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.version IS '版本号';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.content IS '模板内容';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_prompt_template_history.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_chat_request IS '对话请求';
                
      COMMENT ON COLUMN nop_ai_chat_request.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_chat_request.template_id IS '模板ID';
                    
      COMMENT ON COLUMN nop_ai_chat_request.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_chat_request.system_prompt IS '系统提示词';
                    
      COMMENT ON COLUMN nop_ai_chat_request.user_prompt IS '用户提示词';
                    
      COMMENT ON COLUMN nop_ai_chat_request.message_type IS '消息类型';
                    
      COMMENT ON COLUMN nop_ai_chat_request.request_timestamp IS '请求时间戳';
                    
      COMMENT ON COLUMN nop_ai_chat_request.hash IS '内容哈希';
                    
      COMMENT ON COLUMN nop_ai_chat_request.metadata IS '元数据';
                    
      COMMENT ON COLUMN nop_ai_chat_request.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_chat_request.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_chat_request.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_chat_request.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_chat_request.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_requirement_history IS '需求历史';
                
      COMMENT ON COLUMN nop_ai_requirement_history.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.requirement_id IS '需求ID';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.version IS '版本号';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.content IS '需求内容';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_requirement_history.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session_message IS '会话消息';
                
      COMMENT ON COLUMN nop_ai_session_message.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session_message.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_session_message.role IS '消息角色';
                    
      COMMENT ON COLUMN nop_ai_session_message.seq IS '序号';
                    
      COMMENT ON COLUMN nop_ai_session_message.content IS '消息内容';
                    
      COMMENT ON COLUMN nop_ai_session_message.tool_details IS '工具详情';
                    
      COMMENT ON COLUMN nop_ai_session_message.reasoning IS '推理内容';
                    
      COMMENT ON COLUMN nop_ai_session_message.metadata IS '元数据';
                    
      COMMENT ON COLUMN nop_ai_session_message.parent_id IS '父消息ID';
                    
      COMMENT ON COLUMN nop_ai_session_message.finish_reason IS '停止原因';
                    
      COMMENT ON COLUMN nop_ai_session_message.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_session_message.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session_message.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session_message.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session_message.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session_input IS '会话输入';
                
      COMMENT ON COLUMN nop_ai_session_input.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session_input.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_session_input.prompt IS '输入提示';
                    
      COMMENT ON COLUMN nop_ai_session_input.delivery IS '投递方式';
                    
      COMMENT ON COLUMN nop_ai_session_input.admitted_seq IS '接纳序号';
                    
      COMMENT ON COLUMN nop_ai_session_input.promoted_seq IS '提升序号';
                    
      COMMENT ON COLUMN nop_ai_session_input.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_session_input.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session_input.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session_input.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session_input.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_session_context IS '上下文快照';
                
      COMMENT ON COLUMN nop_ai_session_context.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_session_context.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_session_context.baseline IS '基线内容';
                    
      COMMENT ON COLUMN nop_ai_session_context.snapshot IS '上下文快照';
                    
      COMMENT ON COLUMN nop_ai_session_context.baseline_seq IS '基线序号';
                    
      COMMENT ON COLUMN nop_ai_session_context.replacement_seq IS '替换序号';
                    
      COMMENT ON COLUMN nop_ai_session_context.revision IS '版本';
                    
      COMMENT ON COLUMN nop_ai_session_context.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_session_context.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_session_context.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_session_context.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_todo IS 'Agent待办';
                
      COMMENT ON COLUMN nop_ai_todo.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_todo.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_todo.plan_id IS '计划ID';
                    
      COMMENT ON COLUMN nop_ai_todo.content IS '待办内容';
                    
      COMMENT ON COLUMN nop_ai_todo.status IS '状态';
                    
      COMMENT ON COLUMN nop_ai_todo.priority IS '优先级';
                    
      COMMENT ON COLUMN nop_ai_todo.position IS '位置';
                    
      COMMENT ON COLUMN nop_ai_todo.depends_on IS '依赖项';
                    
      COMMENT ON COLUMN nop_ai_todo.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_todo.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_todo.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_todo.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_todo.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_event IS 'Agent事件';
                
      COMMENT ON COLUMN nop_ai_event.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_event.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_event.seq IS '序号';
                    
      COMMENT ON COLUMN nop_ai_event.event_type IS '事件类型';
                    
      COMMENT ON COLUMN nop_ai_event.data IS '事件数据';
                    
      COMMENT ON COLUMN nop_ai_event.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_event.create_time IS '创建时间';
                    
      COMMENT ON TABLE nop_ai_chat_response IS '响应结果';
                
      COMMENT ON COLUMN nop_ai_chat_response.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_chat_response.request_id IS '请求ID';
                    
      COMMENT ON COLUMN nop_ai_chat_response.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_ai_chat_response.model_id IS '模型ID';
                    
      COMMENT ON COLUMN nop_ai_chat_response.ai_provider IS '供应商';
                    
      COMMENT ON COLUMN nop_ai_chat_response.ai_model IS '模型名称';
                    
      COMMENT ON COLUMN nop_ai_chat_response.response_content IS '响应内容';
                    
      COMMENT ON COLUMN nop_ai_chat_response.response_timestamp IS '响应时间戳';
                    
      COMMENT ON COLUMN nop_ai_chat_response.prompt_tokens IS '请求Token数';
                    
      COMMENT ON COLUMN nop_ai_chat_response.completion_tokens IS '响应Token数';
                    
      COMMENT ON COLUMN nop_ai_chat_response.response_duration_ms IS '响应耗时(毫秒)';
                    
      COMMENT ON COLUMN nop_ai_chat_response.correctness_score IS '正确性分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.performance_score IS '性能分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.readability_score IS '可读性分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.compliance_score IS '合规性分';
                    
      COMMENT ON COLUMN nop_ai_chat_response.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_chat_response.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_chat_response.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_chat_response.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_chat_response.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_gen_file IS '生成文件';
                
      COMMENT ON COLUMN nop_ai_gen_file.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_gen_file.project_id IS '项目ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file.requirement_id IS '需求ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file.module_type IS '模块类型';
                    
      COMMENT ON COLUMN nop_ai_gen_file.content IS '文件内容';
                    
      COMMENT ON COLUMN nop_ai_gen_file.file_path IS '文件路径';
                    
      COMMENT ON COLUMN nop_ai_gen_file.chat_response_id IS '响应ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file.status IS '状态';
                    
      COMMENT ON COLUMN nop_ai_gen_file.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_gen_file.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_gen_file.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_gen_file.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_gen_file.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_gen_file_history IS '文件历史';
                
      COMMENT ON COLUMN nop_ai_gen_file_history.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.gen_file_id IS '文件ID';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.version IS '版本号';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.content IS '文件内容';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_gen_file_history.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_test_case IS '测试用例';
                
      COMMENT ON COLUMN nop_ai_test_case.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_test_case.requirement_id IS '需求ID';
                    
      COMMENT ON COLUMN nop_ai_test_case.test_content IS '测试内容';
                    
      COMMENT ON COLUMN nop_ai_test_case.test_data IS '测试数据';
                    
      COMMENT ON COLUMN nop_ai_test_case.gen_file_id IS '关联文件ID';
                    
      COMMENT ON COLUMN nop_ai_test_case.chat_response_id IS '响应ID';
                    
      COMMENT ON COLUMN nop_ai_test_case.status IS '状态';
                    
      COMMENT ON COLUMN nop_ai_test_case.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_test_case.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_test_case.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_test_case.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_test_case.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_ai_test_result IS '测试结果';
                
      COMMENT ON COLUMN nop_ai_test_result.id IS '主键';
                    
      COMMENT ON COLUMN nop_ai_test_result.test_case_id IS '测试用例ID';
                    
      COMMENT ON COLUMN nop_ai_test_result.execution_time IS '执行时间';
                    
      COMMENT ON COLUMN nop_ai_test_result.success IS '是否成功';
                    
      COMMENT ON COLUMN nop_ai_test_result.error_log IS '错误日志';
                    
      COMMENT ON COLUMN nop_ai_test_result.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_ai_test_result.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_ai_test_result.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_ai_test_result.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_ai_test_result.update_time IS '修改时间';
                    
