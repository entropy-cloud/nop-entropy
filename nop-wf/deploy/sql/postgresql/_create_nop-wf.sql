
CREATE TABLE nop_wf_definition(
  wf_def_id VARCHAR(32) NOT NULL ,
  wf_name VARCHAR(500) NOT NULL ,
  wf_version INT8 NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  description VARCHAR(1000)  ,
  model_text TEXT  ,
  form_path VARCHAR(200)  ,
  status INT4 NOT NULL ,
  published_by VARCHAR(50)  ,
  publish_time TIMESTAMP  ,
  archived_by VARCHAR(50)  ,
  archive_time TIMESTAMP  ,
  is_deprecated BOOLEAN NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_wf_definition primary key (wf_def_id)
);

CREATE TABLE nop_wf_instance(
  wf_id VARCHAR(32) NOT NULL ,
  wf_name VARCHAR(500) NOT NULL ,
  wf_version INT8 NOT NULL ,
  wf_params VARCHAR(4000)  ,
  wf_group VARCHAR(100) NOT NULL ,
  work_scope VARCHAR(100)  ,
  title VARCHAR(200) NOT NULL ,
  status INT4 NOT NULL ,
  app_state VARCHAR(100)  ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  due_time TIMESTAMP  ,
  biz_key VARCHAR(200)  ,
  biz_obj_name VARCHAR(200)  ,
  biz_obj_id VARCHAR(200)  ,
  parent_wf_name VARCHAR(500)  ,
  parent_wf_version INT8  ,
  parent_wf_id VARCHAR(32)  ,
  parent_step_id VARCHAR(200)  ,
  starter_id VARCHAR(50)  ,
  starter_name VARCHAR(50)  ,
  starter_dept_id VARCHAR(50)  ,
  last_operator_id VARCHAR(50)  ,
  last_operator_name VARCHAR(50)  ,
  last_operator_dept_id VARCHAR(50)  ,
  last_operate_time TIMESTAMP  ,
  manager_type VARCHAR(50)  ,
  manager_dept_id VARCHAR(50)  ,
  manager_name VARCHAR(50)  ,
  manager_id VARCHAR(50)  ,
  priority INT4 NOT NULL ,
  signal_text VARCHAR(1000)  ,
  tag_text VARCHAR(200)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_wf_instance primary key (wf_id)
);

CREATE TABLE nop_wf_definition_auth(
  sid VARCHAR(32) NOT NULL ,
  wf_def_id VARCHAR(32) NOT NULL ,
  actor_type VARCHAR(10) NOT NULL ,
  actor_id VARCHAR(100) NOT NULL ,
  actor_dept_id VARCHAR(50)  ,
  actor_name VARCHAR(100) NOT NULL ,
  allow_edit BOOLEAN NOT NULL ,
  allow_manage BOOLEAN NOT NULL ,
  allow_start BOOLEAN NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_wf_definition_auth primary key (sid)
);

CREATE TABLE nop_wf_status_history(
  sid VARCHAR(32) NOT NULL ,
  wf_id VARCHAR(32) NOT NULL ,
  from_status INT4 NOT NULL ,
  to_status INT4 NOT NULL ,
  to_app_state VARCHAR(100)  ,
  change_time TIMESTAMP NOT NULL ,
  operator_id VARCHAR(50)  ,
  operator_name VARCHAR(50)  ,
  operator_dept_id VARCHAR(50)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_status_history primary key (sid)
);

CREATE TABLE nop_wf_step_instance(
  step_id VARCHAR(32) NOT NULL ,
  wf_id VARCHAR(32) NOT NULL ,
  step_type VARCHAR(10) NOT NULL ,
  step_name VARCHAR(200) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  status INT4 NOT NULL ,
  app_state VARCHAR(100)  ,
  sub_wf_id VARCHAR(32)  ,
  sub_wf_name VARCHAR(200)  ,
  sub_wf_version INT8  ,
  sub_wf_result_status INT4  ,
  is_read BOOLEAN default false  NOT NULL ,
  actor_model_id VARCHAR(100)  ,
  actor_type VARCHAR(10)  ,
  actor_id VARCHAR(100)  ,
  actor_dept_id VARCHAR(50)  ,
  actor_name VARCHAR(100)  ,
  owner_id VARCHAR(50)  ,
  owner_name VARCHAR(50)  ,
  owner_dept_id VARCHAR(50)  ,
  assigner_id VARCHAR(50)  ,
  assigner_name VARCHAR(50)  ,
  caller_id VARCHAR(50)  ,
  caller_name VARCHAR(50)  ,
  canceller_id VARCHAR(50)  ,
  canceller_name VARCHAR(50)  ,
  from_action VARCHAR(200)  ,
  last_action VARCHAR(200)  ,
  start_time TIMESTAMP  ,
  finish_time TIMESTAMP  ,
  due_time TIMESTAMP  ,
  read_time TIMESTAMP  ,
  remind_time TIMESTAMP  ,
  remind_count INT4  ,
  next_retry_time TIMESTAMP  ,
  retry_count INT4  ,
  err_code VARCHAR(200)  ,
  err_msg VARCHAR(4000)  ,
  priority INT4 NOT NULL ,
  join_group VARCHAR(100)  ,
  tag_text VARCHAR(200)  ,
  next_step_id VARCHAR(32)  ,
  exec_group VARCHAR(32)  ,
  exec_order INT4 NOT NULL ,
  exec_count INT4  ,
  vote_weight INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_wf_step_instance primary key (step_id)
);

CREATE TABLE nop_wf_output(
  wf_id VARCHAR(32) NOT NULL ,
  field_name VARCHAR(100) NOT NULL ,
  field_type INT4 NOT NULL ,
  string_value VARCHAR(4000)  ,
  decimal_value NUMERIC(30,6)  ,
  long_value INT8  ,
  date_value DATE  ,
  timestamp_value TIMESTAMP  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_output primary key (wf_id,field_name)
);

CREATE TABLE nop_wf_var(
  wf_id VARCHAR(32) NOT NULL ,
  field_name VARCHAR(100) NOT NULL ,
  field_type INT4 NOT NULL ,
  string_value VARCHAR(4000)  ,
  decimal_value NUMERIC(30,6)  ,
  long_value INT8  ,
  date_value DATE  ,
  timestamp_value TIMESTAMP  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_var primary key (wf_id,field_name)
);

CREATE TABLE nop_wf_step_instance_link(
  wf_id VARCHAR(32) NOT NULL ,
  step_id VARCHAR(32) NOT NULL ,
  next_step_id VARCHAR(32) NOT NULL ,
  exec_action VARCHAR(200) NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_step_instance_link primary key (wf_id,step_id,next_step_id)
);

CREATE TABLE nop_wf_action(
  sid VARCHAR(32) NOT NULL ,
  wf_id VARCHAR(32) NOT NULL ,
  step_id VARCHAR(32) NOT NULL ,
  action_name VARCHAR(200) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  exec_time TIMESTAMP NOT NULL ,
  caller_id VARCHAR(50)  ,
  caller_name VARCHAR(50)  ,
  opinion VARCHAR(4000)  ,
  err_code VARCHAR(200)  ,
  err_msg VARCHAR(4000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_action primary key (sid)
);

CREATE TABLE nop_wf_work(
  work_id VARCHAR(32) NOT NULL ,
  wf_id VARCHAR(32)  ,
  step_id VARCHAR(32)  ,
  work_type VARCHAR(10) NOT NULL ,
  title VARCHAR(2000) NOT NULL ,
  link_url VARCHAR(2000) NOT NULL ,
  status INT4 NOT NULL ,
  owner_id VARCHAR(50)  ,
  owner_name VARCHAR(50)  ,
  caller_id VARCHAR(50)  ,
  caller_name VARCHAR(50)  ,
  read_time TIMESTAMP  ,
  finish_time TIMESTAMP  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_wf_work primary key (work_id)
);

CREATE TABLE nop_wf_log(
  sid VARCHAR(32) NOT NULL ,
  wf_id VARCHAR(32) NOT NULL ,
  step_id VARCHAR(32)  ,
  action_id VARCHAR(32)  ,
  log_level INT4 NOT NULL ,
  log_msg VARCHAR(4000)  ,
  err_code VARCHAR(200)  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_log primary key (sid)
);


      COMMENT ON TABLE nop_wf_definition IS '工作流模型定义';
                
      COMMENT ON COLUMN nop_wf_definition.wf_def_id IS '主键';
                    
      COMMENT ON COLUMN nop_wf_definition.wf_name IS '工作流名称';
                    
      COMMENT ON COLUMN nop_wf_definition.wf_version IS '工作流版本';
                    
      COMMENT ON COLUMN nop_wf_definition.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_wf_definition.description IS '描述';
                    
      COMMENT ON COLUMN nop_wf_definition.model_text IS '模型文本';
                    
      COMMENT ON COLUMN nop_wf_definition.form_path IS '关联表单路径';
                    
      COMMENT ON COLUMN nop_wf_definition.status IS '状态';
                    
      COMMENT ON COLUMN nop_wf_definition.published_by IS '发布人';
                    
      COMMENT ON COLUMN nop_wf_definition.publish_time IS '发布时间';
                    
      COMMENT ON COLUMN nop_wf_definition.archived_by IS '归档人';
                    
      COMMENT ON COLUMN nop_wf_definition.archive_time IS '归档时间';
                    
      COMMENT ON COLUMN nop_wf_definition.is_deprecated IS '是否已废弃';
                    
      COMMENT ON COLUMN nop_wf_definition.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_definition.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_definition.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_definition.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_definition.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_definition.remark IS '备注';
                    
      COMMENT ON TABLE nop_wf_instance IS '工作流实例';
                
      COMMENT ON COLUMN nop_wf_instance.wf_id IS '主键';
                    
      COMMENT ON COLUMN nop_wf_instance.wf_name IS '工作流名称';
                    
      COMMENT ON COLUMN nop_wf_instance.wf_version IS '工作流版本';
                    
      COMMENT ON COLUMN nop_wf_instance.wf_params IS '工作流参数';
                    
      COMMENT ON COLUMN nop_wf_instance.wf_group IS '工作流分组';
                    
      COMMENT ON COLUMN nop_wf_instance.work_scope IS '工作分类';
                    
      COMMENT ON COLUMN nop_wf_instance.title IS '实例标题';
                    
      COMMENT ON COLUMN nop_wf_instance.status IS '状态';
                    
      COMMENT ON COLUMN nop_wf_instance.app_state IS '应用状态';
                    
      COMMENT ON COLUMN nop_wf_instance.start_time IS '启动时间';
                    
      COMMENT ON COLUMN nop_wf_instance.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_wf_instance.due_time IS '完成时限';
                    
      COMMENT ON COLUMN nop_wf_instance.biz_key IS '业务唯一键';
                    
      COMMENT ON COLUMN nop_wf_instance.biz_obj_name IS '业务对象名';
                    
      COMMENT ON COLUMN nop_wf_instance.biz_obj_id IS '业务对象ID';
                    
      COMMENT ON COLUMN nop_wf_instance.parent_wf_name IS '父工作流名称';
                    
      COMMENT ON COLUMN nop_wf_instance.parent_wf_version IS '父流程版本';
                    
      COMMENT ON COLUMN nop_wf_instance.parent_wf_id IS '父流程ID';
                    
      COMMENT ON COLUMN nop_wf_instance.parent_step_id IS '父流程步骤ID';
                    
      COMMENT ON COLUMN nop_wf_instance.starter_id IS '启动人ID';
                    
      COMMENT ON COLUMN nop_wf_instance.starter_name IS '启动人';
                    
      COMMENT ON COLUMN nop_wf_instance.starter_dept_id IS '启动人单位ID';
                    
      COMMENT ON COLUMN nop_wf_instance.last_operator_id IS '上次操作者ID';
                    
      COMMENT ON COLUMN nop_wf_instance.last_operator_name IS '上次操作者';
                    
      COMMENT ON COLUMN nop_wf_instance.last_operator_dept_id IS '上次操作者单位ID';
                    
      COMMENT ON COLUMN nop_wf_instance.last_operate_time IS '上次操作时间';
                    
      COMMENT ON COLUMN nop_wf_instance.manager_type IS '管理者类型';
                    
      COMMENT ON COLUMN nop_wf_instance.manager_dept_id IS '管理者单位ID';
                    
      COMMENT ON COLUMN nop_wf_instance.manager_name IS '管理者';
                    
      COMMENT ON COLUMN nop_wf_instance.manager_id IS '管理者ID';
                    
      COMMENT ON COLUMN nop_wf_instance.priority IS '优先级';
                    
      COMMENT ON COLUMN nop_wf_instance.signal_text IS '信号集合';
                    
      COMMENT ON COLUMN nop_wf_instance.tag_text IS '标签';
                    
      COMMENT ON COLUMN nop_wf_instance.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_instance.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_instance.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_instance.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_instance.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_instance.remark IS '备注';
                    
      COMMENT ON TABLE nop_wf_definition_auth IS '工作流定义权限';
                
      COMMENT ON COLUMN nop_wf_definition_auth.sid IS '主键';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.wf_def_id IS '工作流定义ID';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.actor_type IS '参与者类型';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.actor_id IS '参与者ID';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.actor_dept_id IS '参与者部门ID';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.actor_name IS '参与者名称';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.allow_edit IS '允许编辑';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.allow_manage IS '允许管理';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.allow_start IS '允许启动';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_definition_auth.remark IS '备注';
                    
      COMMENT ON TABLE nop_wf_status_history IS '工作流状态变迁历史';
                
      COMMENT ON COLUMN nop_wf_status_history.sid IS '主键';
                    
      COMMENT ON COLUMN nop_wf_status_history.wf_id IS '主键';
                    
      COMMENT ON COLUMN nop_wf_status_history.from_status IS '源状态';
                    
      COMMENT ON COLUMN nop_wf_status_history.to_status IS '目标状态';
                    
      COMMENT ON COLUMN nop_wf_status_history.to_app_state IS '目标应用状态';
                    
      COMMENT ON COLUMN nop_wf_status_history.change_time IS '状态变动时间';
                    
      COMMENT ON COLUMN nop_wf_status_history.operator_id IS '操作者ID';
                    
      COMMENT ON COLUMN nop_wf_status_history.operator_name IS '操作者';
                    
      COMMENT ON COLUMN nop_wf_status_history.operator_dept_id IS '操作者部门ID';
                    
      COMMENT ON COLUMN nop_wf_status_history.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_status_history.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_status_history.create_time IS '创建时间';
                    
      COMMENT ON TABLE nop_wf_step_instance IS '工作流步骤实例';
                
      COMMENT ON COLUMN nop_wf_step_instance.step_id IS '步骤ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.step_type IS '步骤类型';
                    
      COMMENT ON COLUMN nop_wf_step_instance.step_name IS '步骤名称';
                    
      COMMENT ON COLUMN nop_wf_step_instance.display_name IS '步骤显示名称';
                    
      COMMENT ON COLUMN nop_wf_step_instance.status IS '状态';
                    
      COMMENT ON COLUMN nop_wf_step_instance.app_state IS '应用状态';
                    
      COMMENT ON COLUMN nop_wf_step_instance.sub_wf_id IS '子工作流ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.sub_wf_name IS '子工作流名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.sub_wf_version IS '子流程版本';
                    
      COMMENT ON COLUMN nop_wf_step_instance.sub_wf_result_status IS '子流程结果状态';
                    
      COMMENT ON COLUMN nop_wf_step_instance.is_read IS '是否已读';
                    
      COMMENT ON COLUMN nop_wf_step_instance.actor_model_id IS '参与者模型ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.actor_type IS '参与者类型';
                    
      COMMENT ON COLUMN nop_wf_step_instance.actor_id IS '参与者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.actor_dept_id IS '参与者部门ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.actor_name IS '参与者名称';
                    
      COMMENT ON COLUMN nop_wf_step_instance.owner_id IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.owner_name IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.owner_dept_id IS '拥有者部门ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.assigner_id IS '分配者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.assigner_name IS '分配者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.caller_id IS '调用者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.caller_name IS '调用者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.canceller_id IS '取消人ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.canceller_name IS '取消人姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.from_action IS '来源操作';
                    
      COMMENT ON COLUMN nop_wf_step_instance.last_action IS '最后一次操作';
                    
      COMMENT ON COLUMN nop_wf_step_instance.start_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.finish_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.due_time IS '到期时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.read_time IS '读取时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.remind_time IS '提醒时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.remind_count IS '提醒次数';
                    
      COMMENT ON COLUMN nop_wf_step_instance.next_retry_time IS '下次重试时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.retry_count IS '已重试次数';
                    
      COMMENT ON COLUMN nop_wf_step_instance.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_wf_step_instance.err_msg IS '错误消息';
                    
      COMMENT ON COLUMN nop_wf_step_instance.priority IS '优先级';
                    
      COMMENT ON COLUMN nop_wf_step_instance.join_group IS '汇聚分组';
                    
      COMMENT ON COLUMN nop_wf_step_instance.tag_text IS '标签';
                    
      COMMENT ON COLUMN nop_wf_step_instance.next_step_id IS '下一步骤ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.exec_group IS '执行分组';
                    
      COMMENT ON COLUMN nop_wf_step_instance.exec_order IS '执行顺序';
                    
      COMMENT ON COLUMN nop_wf_step_instance.exec_count IS '执行次数';
                    
      COMMENT ON COLUMN nop_wf_step_instance.vote_weight IS '投票权重';
                    
      COMMENT ON COLUMN nop_wf_step_instance.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_step_instance.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_step_instance.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_step_instance.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.remark IS '备注';
                    
      COMMENT ON TABLE nop_wf_output IS '工作流输出变量';
                
      COMMENT ON COLUMN nop_wf_output.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_output.field_name IS '变量名';
                    
      COMMENT ON COLUMN nop_wf_output.field_type IS '变量类型';
                    
      COMMENT ON COLUMN nop_wf_output.string_value IS '字符串值';
                    
      COMMENT ON COLUMN nop_wf_output.decimal_value IS '浮点值';
                    
      COMMENT ON COLUMN nop_wf_output.long_value IS '整数型';
                    
      COMMENT ON COLUMN nop_wf_output.date_value IS '日期值';
                    
      COMMENT ON COLUMN nop_wf_output.timestamp_value IS '时间点值';
                    
      COMMENT ON COLUMN nop_wf_output.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_output.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_output.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_output.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_output.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_wf_var IS '工作流状态变量';
                
      COMMENT ON COLUMN nop_wf_var.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_var.field_name IS '变量名';
                    
      COMMENT ON COLUMN nop_wf_var.field_type IS '变量类型';
                    
      COMMENT ON COLUMN nop_wf_var.string_value IS '字符串值';
                    
      COMMENT ON COLUMN nop_wf_var.decimal_value IS '浮点值';
                    
      COMMENT ON COLUMN nop_wf_var.long_value IS '整数型';
                    
      COMMENT ON COLUMN nop_wf_var.date_value IS '日期值';
                    
      COMMENT ON COLUMN nop_wf_var.timestamp_value IS '时间点值';
                    
      COMMENT ON COLUMN nop_wf_var.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_var.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_var.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_var.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_var.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_wf_step_instance_link IS '工作流步骤关联';
                
      COMMENT ON COLUMN nop_wf_step_instance_link.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.step_id IS '步骤ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.next_step_id IS '下一步骤 ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.exec_action IS '执行动作';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.create_time IS '创建时间';
                    
      COMMENT ON TABLE nop_wf_action IS '工作流动作';
                
      COMMENT ON COLUMN nop_wf_action.sid IS '主键';
                    
      COMMENT ON COLUMN nop_wf_action.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_action.step_id IS '工作流步骤ID';
                    
      COMMENT ON COLUMN nop_wf_action.action_name IS '动作名称';
                    
      COMMENT ON COLUMN nop_wf_action.display_name IS '动作显示名称';
                    
      COMMENT ON COLUMN nop_wf_action.exec_time IS '执行时刻';
                    
      COMMENT ON COLUMN nop_wf_action.caller_id IS '调用者ID';
                    
      COMMENT ON COLUMN nop_wf_action.caller_name IS '调用者姓名';
                    
      COMMENT ON COLUMN nop_wf_action.opinion IS '意见';
                    
      COMMENT ON COLUMN nop_wf_action.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_wf_action.err_msg IS '错误消息';
                    
      COMMENT ON COLUMN nop_wf_action.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_action.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_action.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_action.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_action.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_wf_work IS '代办工作';
                
      COMMENT ON COLUMN nop_wf_work.work_id IS '工作ID';
                    
      COMMENT ON COLUMN nop_wf_work.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_work.step_id IS '工作流步骤ID';
                    
      COMMENT ON COLUMN nop_wf_work.work_type IS '工作类型';
                    
      COMMENT ON COLUMN nop_wf_work.title IS '工作标题';
                    
      COMMENT ON COLUMN nop_wf_work.link_url IS '工作链接';
                    
      COMMENT ON COLUMN nop_wf_work.status IS '状态';
                    
      COMMENT ON COLUMN nop_wf_work.owner_id IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_wf_work.owner_name IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_wf_work.caller_id IS '调用者ID';
                    
      COMMENT ON COLUMN nop_wf_work.caller_name IS '调用者姓名';
                    
      COMMENT ON COLUMN nop_wf_work.read_time IS '读取时间';
                    
      COMMENT ON COLUMN nop_wf_work.finish_time IS '完成时间';
                    
      COMMENT ON COLUMN nop_wf_work.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_work.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_work.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_work.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_work.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_work.remark IS '备注';
                    
      COMMENT ON TABLE nop_wf_log IS '工作流日志';
                
      COMMENT ON COLUMN nop_wf_log.sid IS '日志ID';
                    
      COMMENT ON COLUMN nop_wf_log.wf_id IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_log.step_id IS '工作流步骤ID';
                    
      COMMENT ON COLUMN nop_wf_log.action_id IS '动作ID';
                    
      COMMENT ON COLUMN nop_wf_log.log_level IS '日志级别';
                    
      COMMENT ON COLUMN nop_wf_log.log_msg IS '日志消息';
                    
      COMMENT ON COLUMN nop_wf_log.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_wf_log.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_log.create_time IS '创建时间';
                    
