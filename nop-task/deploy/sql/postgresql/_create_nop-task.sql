
CREATE TABLE nop_task_definition(
  task_def_id VARCHAR(32) NOT NULL ,
  task_name VARCHAR(500) NOT NULL ,
  task_version INT8 NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  description VARCHAR(1000)  ,
  model_text TEXT NOT NULL ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_task_definition primary key (task_def_id)
);

CREATE TABLE nop_task_instance(
  task_instance_id VARCHAR(32) NOT NULL ,
  task_name VARCHAR(500) NOT NULL ,
  task_version INT8 NOT NULL ,
  task_inputs VARCHAR(4000)  ,
  task_group VARCHAR(100) NOT NULL ,
  status INT4 NOT NULL ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  due_time TIMESTAMP  ,
  biz_key VARCHAR(200)  ,
  biz_obj_name VARCHAR(200)  ,
  biz_obj_id VARCHAR(200)  ,
  parent_task_name VARCHAR(500)  ,
  parent_task_version INT8  ,
  parent_task_id VARCHAR(32)  ,
  parent_step_id VARCHAR(200)  ,
  starter_id VARCHAR(50)  ,
  starter_name VARCHAR(50)  ,
  starter_dept_id VARCHAR(50)  ,
  manager_type VARCHAR(50)  ,
  manager_dept_id VARCHAR(50)  ,
  manager_name VARCHAR(50)  ,
  manager_id VARCHAR(50)  ,
  priority INT4 NOT NULL ,
  signal_text VARCHAR(1000)  ,
  tag_text VARCHAR(200)  ,
  job_instance_id VARCHAR(32)  ,
  err_code VARCHAR(200)  ,
  err_msg VARCHAR(500)  ,
  worker_id VARCHAR(50)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_task_instance primary key (task_instance_id)
);

CREATE TABLE nop_task_definition_auth(
  sid VARCHAR(32) NOT NULL ,
  task_def_id VARCHAR(32) NOT NULL ,
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
  constraint PK_nop_task_definition_auth primary key (sid)
);

CREATE TABLE nop_task_step_instance(
  step_instance_id VARCHAR(32) NOT NULL ,
  task_instance_id VARCHAR(32) NOT NULL ,
  step_type VARCHAR(20) NOT NULL ,
  step_name VARCHAR(200) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  step_status INT4 NOT NULL ,
  sub_task_id VARCHAR(32)  ,
  sub_task_name VARCHAR(200)  ,
  sub_task_version INT8  ,
  start_time TIMESTAMP  ,
  finish_time TIMESTAMP  ,
  due_time TIMESTAMP  ,
  next_retry_time TIMESTAMP  ,
  retry_count INT4  ,
  internal BOOLEAN  ,
  err_code VARCHAR(200)  ,
  err_msg VARCHAR(4000)  ,
  priority INT4 NOT NULL ,
  tag_text VARCHAR(200)  ,
  parent_step_id VARCHAR(32)  ,
  worker_id VARCHAR(50)  ,
  step_path VARCHAR(2000)  ,
  run_id INT4 NOT NULL ,
  body_step_index INT4 NOT NULL ,
  state_bean_data VARCHAR(4000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_task_step_instance primary key (step_instance_id)
);


      COMMENT ON TABLE nop_task_definition IS '逻辑流模型定义';
                
      COMMENT ON COLUMN nop_task_definition.task_def_id IS '主键';
                    
      COMMENT ON COLUMN nop_task_definition.task_name IS '逻辑流名称';
                    
      COMMENT ON COLUMN nop_task_definition.task_version IS '逻辑流版本';
                    
      COMMENT ON COLUMN nop_task_definition.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_task_definition.description IS '描述';
                    
      COMMENT ON COLUMN nop_task_definition.model_text IS '模型文本';
                    
      COMMENT ON COLUMN nop_task_definition.status IS '状态';
                    
      COMMENT ON COLUMN nop_task_definition.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_definition.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_task_definition.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_definition.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_task_definition.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_definition.remark IS '备注';
                    
      COMMENT ON TABLE nop_task_instance IS '逻辑流实例';
                
      COMMENT ON COLUMN nop_task_instance.task_instance_id IS '主键';
                    
      COMMENT ON COLUMN nop_task_instance.task_name IS '逻辑流名称';
                    
      COMMENT ON COLUMN nop_task_instance.task_version IS '逻辑流版本';
                    
      COMMENT ON COLUMN nop_task_instance.task_inputs IS '逻辑流参数';
                    
      COMMENT ON COLUMN nop_task_instance.task_group IS '逻辑流分组';
                    
      COMMENT ON COLUMN nop_task_instance.status IS '状态';
                    
      COMMENT ON COLUMN nop_task_instance.start_time IS '启动时间';
                    
      COMMENT ON COLUMN nop_task_instance.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_task_instance.due_time IS '完成时限';
                    
      COMMENT ON COLUMN nop_task_instance.biz_key IS '业务唯一键';
                    
      COMMENT ON COLUMN nop_task_instance.biz_obj_name IS '业务对象名';
                    
      COMMENT ON COLUMN nop_task_instance.biz_obj_id IS '业务对象ID';
                    
      COMMENT ON COLUMN nop_task_instance.parent_task_name IS '父流程名称';
                    
      COMMENT ON COLUMN nop_task_instance.parent_task_version IS '父流程版本';
                    
      COMMENT ON COLUMN nop_task_instance.parent_task_id IS '父流程ID';
                    
      COMMENT ON COLUMN nop_task_instance.parent_step_id IS '父流程步骤ID';
                    
      COMMENT ON COLUMN nop_task_instance.starter_id IS '启动人ID';
                    
      COMMENT ON COLUMN nop_task_instance.starter_name IS '启动人';
                    
      COMMENT ON COLUMN nop_task_instance.starter_dept_id IS '启动人单位ID';
                    
      COMMENT ON COLUMN nop_task_instance.manager_type IS '管理者类型';
                    
      COMMENT ON COLUMN nop_task_instance.manager_dept_id IS '管理者单位ID';
                    
      COMMENT ON COLUMN nop_task_instance.manager_name IS '管理者';
                    
      COMMENT ON COLUMN nop_task_instance.manager_id IS '管理者ID';
                    
      COMMENT ON COLUMN nop_task_instance.priority IS '优先级';
                    
      COMMENT ON COLUMN nop_task_instance.signal_text IS '信号集合';
                    
      COMMENT ON COLUMN nop_task_instance.tag_text IS '标签';
                    
      COMMENT ON COLUMN nop_task_instance.job_instance_id IS 'Job ID';
                    
      COMMENT ON COLUMN nop_task_instance.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_task_instance.err_msg IS '错误消息';
                    
      COMMENT ON COLUMN nop_task_instance.worker_id IS 'Worker ID';
                    
      COMMENT ON COLUMN nop_task_instance.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_instance.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_task_instance.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_instance.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_task_instance.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_instance.remark IS '备注';
                    
      COMMENT ON TABLE nop_task_definition_auth IS '逻辑流定义权限';
                
      COMMENT ON COLUMN nop_task_definition_auth.sid IS '主键';
                    
      COMMENT ON COLUMN nop_task_definition_auth.task_def_id IS '工作流定义ID';
                    
      COMMENT ON COLUMN nop_task_definition_auth.actor_type IS '参与者类型';
                    
      COMMENT ON COLUMN nop_task_definition_auth.actor_id IS '参与者ID';
                    
      COMMENT ON COLUMN nop_task_definition_auth.actor_dept_id IS '参与者部门ID';
                    
      COMMENT ON COLUMN nop_task_definition_auth.actor_name IS '参与者名称';
                    
      COMMENT ON COLUMN nop_task_definition_auth.allow_edit IS '允许编辑';
                    
      COMMENT ON COLUMN nop_task_definition_auth.allow_manage IS '允许管理';
                    
      COMMENT ON COLUMN nop_task_definition_auth.allow_start IS '允许启动';
                    
      COMMENT ON COLUMN nop_task_definition_auth.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_definition_auth.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_task_definition_auth.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_definition_auth.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_task_definition_auth.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_definition_auth.remark IS '备注';
                    
      COMMENT ON TABLE nop_task_step_instance IS '逻辑流步骤实例';
                
      COMMENT ON COLUMN nop_task_step_instance.step_instance_id IS '步骤ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.task_instance_id IS '逻辑流实例ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.step_type IS '步骤类型';
                    
      COMMENT ON COLUMN nop_task_step_instance.step_name IS '步骤名称';
                    
      COMMENT ON COLUMN nop_task_step_instance.display_name IS '步骤显示名称';
                    
      COMMENT ON COLUMN nop_task_step_instance.step_status IS '状态';
                    
      COMMENT ON COLUMN nop_task_step_instance.sub_task_id IS '子流程ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.sub_task_name IS '子流程名称';
                    
      COMMENT ON COLUMN nop_task_step_instance.sub_task_version IS '子流程版本';
                    
      COMMENT ON COLUMN nop_task_step_instance.start_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.finish_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.due_time IS '到期时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.next_retry_time IS '下次重试时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.retry_count IS '已重试次数';
                    
      COMMENT ON COLUMN nop_task_step_instance.internal IS '是否内部';
                    
      COMMENT ON COLUMN nop_task_step_instance.err_code IS '错误码';
                    
      COMMENT ON COLUMN nop_task_step_instance.err_msg IS '错误消息';
                    
      COMMENT ON COLUMN nop_task_step_instance.priority IS '优先级';
                    
      COMMENT ON COLUMN nop_task_step_instance.tag_text IS '标签';
                    
      COMMENT ON COLUMN nop_task_step_instance.parent_step_id IS '父步骤ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.worker_id IS '工作者ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.step_path IS '步骤路径';
                    
      COMMENT ON COLUMN nop_task_step_instance.run_id IS '运行ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.body_step_index IS '步骤下标';
                    
      COMMENT ON COLUMN nop_task_step_instance.state_bean_data IS '状态数据';
                    
      COMMENT ON COLUMN nop_task_step_instance.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_step_instance.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_task_step_instance.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_task_step_instance.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.remark IS '备注';
                    
