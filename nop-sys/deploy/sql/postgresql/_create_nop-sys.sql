
CREATE TABLE nop_sys_sequence(
  seq_name VARCHAR(150) NOT NULL ,
  seq_type VARCHAR(10)  ,
  is_uuid INT4 default 0  NOT NULL ,
  next_value INT8 NOT NULL ,
  step_size INT4 NOT NULL ,
  cache_size INT4  ,
  max_value INT8  ,
  reset_type INT4  ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_sequence primary key (seq_name)
);

CREATE TABLE nop_sys_dict(
  sid VARCHAR(32) NOT NULL ,
  dict_name VARCHAR(150) NOT NULL ,
  display_name VARCHAR(50) NOT NULL ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_dict primary key (sid)
);

CREATE TABLE nop_sys_i18n(
  i18n_key VARCHAR(200) NOT NULL ,
  i18n_locale VARCHAR(20) NOT NULL ,
  value VARCHAR(300) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_i18n primary key (i18n_key,i18n_locale)
);

CREATE TABLE nop_sys_checker_record(
  sid VARCHAR(32) NOT NULL ,
  biz_obj_name VARCHAR(200) NOT NULL ,
  biz_obj_id VARCHAR(100)  ,
  maker_id VARCHAR(50) NOT NULL ,
  maker_name VARCHAR(150) NOT NULL ,
  request_action VARCHAR(100) NOT NULL ,
  request_data TEXT  ,
  request_time TIMESTAMP NOT NULL ,
  checker_id VARCHAR(50)  ,
  checker_name VARCHAR(150)  ,
  check_time TIMESTAMP  ,
  try_result TEXT  ,
  input_page VARCHAR(1000)  ,
  status INT4 NOT NULL ,
  cancel_action VARCHAR(200)  ,
  cb_err_code VARCHAR(150)  ,
  ce_err_msg VARCHAR(1000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_checker_record primary key (sid)
);

CREATE TABLE nop_sys_code_rule(
  sid VARCHAR(32) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  code_pattern VARCHAR(200) NOT NULL ,
  seq_name VARCHAR(100)  ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_code_rule primary key (sid)
);

CREATE TABLE nop_sys_notice_template(
  sid VARCHAR(32) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  tpl_type VARCHAR(10) NOT NULL ,
  content VARCHAR(4000) NOT NULL ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_notice_template primary key (sid)
);

CREATE TABLE nop_sys_user_variable(
  user_id VARCHAR(32) NOT NULL ,
  var_name VARCHAR(32) NOT NULL ,
  var_value VARCHAR(4000)  ,
  std_domain VARCHAR(100)  ,
  var_type VARCHAR(100)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_user_variable primary key (user_id,var_name)
);

CREATE TABLE nop_sys_variable(
  var_name VARCHAR(32) NOT NULL ,
  var_value VARCHAR(4000)  ,
  std_domain VARCHAR(100)  ,
  var_type VARCHAR(100)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_variable primary key (var_name)
);

CREATE TABLE nop_sys_ext_field(
  entity_name VARCHAR(200) NOT NULL ,
  entity_id VARCHAR(100) NOT NULL ,
  field_name VARCHAR(100) NOT NULL ,
  field_type INT4 NOT NULL ,
  decimal_scale INT4  ,
  decimal_value NUMERIC(24,8)  ,
  date_value DATE  ,
  timestamp_value TIMESTAMP  ,
  string_value VARCHAR(4000)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_ext_field primary key (entity_name,entity_id,field_name)
);

CREATE TABLE nop_sys_lock(
  lock_name VARCHAR(200) NOT NULL ,
  lock_group VARCHAR(200) NOT NULL ,
  lock_time TIMESTAMP NOT NULL ,
  expire_at TIMESTAMP NOT NULL ,
  lock_reason VARCHAR(200)  ,
  holder_id VARCHAR(100) NOT NULL ,
  holder_adder VARCHAR(100) NOT NULL ,
  app_id VARCHAR(100) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_lock primary key (lock_name,lock_group)
);

CREATE TABLE nop_sys_cluster_leader(
  cluster_id VARCHAR(200) NOT NULL ,
  leader_id VARCHAR(100) NOT NULL ,
  leader_adder VARCHAR(100) NOT NULL ,
  leader_epoch INT8 NOT NULL ,
  elect_time TIMESTAMP NOT NULL ,
  expire_at TIMESTAMP NOT NULL ,
  refresh_time TIMESTAMP NOT NULL ,
  version INT4 NOT NULL ,
  app_name VARCHAR(100) NOT NULL ,
  constraint PK_nop_sys_cluster_leader primary key (cluster_id)
);

CREATE TABLE nop_sys_event(
  event_id INT8 NOT NULL ,
  event_topic VARCHAR(100) NOT NULL ,
  event_name VARCHAR(100) NOT NULL ,
  event_headers JSON NOT NULL ,
  event_data JSON NOT NULL ,
  selection VARCHAR(1000)  ,
  event_time TIMESTAMP NOT NULL ,
  event_status INT4 NOT NULL ,
  process_time TIMESTAMP NOT NULL ,
  schedule_time TIMESTAMP NOT NULL ,
  is_broadcast BOOLEAN NOT NULL ,
  biz_obj_name VARCHAR(100)  ,
  biz_key VARCHAR(50)  ,
  biz_date DATE NOT NULL ,
  partition_index INT4 NOT NULL ,
  retry_times INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_event primary key (event_id)
);

CREATE TABLE nop_sys_service_instance(
  instance_id VARCHAR(50) NOT NULL ,
  service_name VARCHAR(100) NOT NULL ,
  cluster_name VARCHAR(100) NOT NULL ,
  group_name VARCHAR(100) NOT NULL ,
  tags_text VARCHAR(100) NOT NULL ,
  server_addr VARCHAR(20) NOT NULL ,
  server_port INT4 NOT NULL ,
  weight INT4 NOT NULL ,
  meta_data VARCHAR(1000)  ,
  is_healthy BOOLEAN NOT NULL ,
  is_enabled BOOLEAN NOT NULL ,
  is_ephemeral BOOLEAN NOT NULL ,
  version INT4 NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_service_instance primary key (instance_id)
);

CREATE TABLE nop_sys_change_log(
  sid VARCHAR(32) NOT NULL ,
  biz_obj_name VARCHAR(100) NOT NULL ,
  obj_id VARCHAR(100) NOT NULL ,
  biz_key VARCHAR(100)  ,
  operation_name VARCHAR(150) NOT NULL ,
  prop_name VARCHAR(100) NOT NULL ,
  old_value VARCHAR(4000)  ,
  new_value VARCHAR(4000)  ,
  change_time TIMESTAMP NOT NULL ,
  app_id VARCHAR(100)  ,
  operator_id VARCHAR(50) NOT NULL ,
  approver_id VARCHAR(50)  ,
  constraint PK_nop_sys_change_log primary key (sid)
);

CREATE TABLE nop_sys_tag(
  sid INT8 NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  description VARCHAR(500)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_tag primary key (sid)
);

CREATE TABLE nop_sys_dict_option(
  sid VARCHAR(32) NOT NULL ,
  dict_id VARCHAR(32) NOT NULL ,
  label VARCHAR(150) NOT NULL ,
  value VARCHAR(150) NOT NULL ,
  code_value VARCHAR(100)  ,
  group_name VARCHAR(50)  ,
  is_internal INT4 default 0  NOT NULL ,
  is_deprecated INT4 default 0  NOT NULL ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_sys_dict_option primary key (sid)
);

CREATE TABLE nop_sys_obj_tag(
  biz_obj_id VARCHAR(50) NOT NULL ,
  biz_obj_name VARCHAR(100) NOT NULL ,
  tag_id INT8 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_obj_tag primary key (biz_obj_id,biz_obj_name,tag_id)
);


      COMMENT ON TABLE nop_sys_sequence IS '序列号';
                
      COMMENT ON COLUMN nop_sys_sequence.seq_name IS '名称';
                    
      COMMENT ON COLUMN nop_sys_sequence.seq_type IS '类型';
                    
      COMMENT ON COLUMN nop_sys_sequence.is_uuid IS '是否UUID';
                    
      COMMENT ON COLUMN nop_sys_sequence.next_value IS '下一个值';
                    
      COMMENT ON COLUMN nop_sys_sequence.step_size IS '步长';
                    
      COMMENT ON COLUMN nop_sys_sequence.cache_size IS '缓存个数';
                    
      COMMENT ON COLUMN nop_sys_sequence.max_value IS '最大值';
                    
      COMMENT ON COLUMN nop_sys_sequence.reset_type IS '重置方式';
                    
      COMMENT ON COLUMN nop_sys_sequence.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_sequence.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_sequence.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_sequence.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_sequence.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_sequence.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_sequence.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_dict IS '字典表';
                
      COMMENT ON COLUMN nop_sys_dict.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_dict.dict_name IS '字典名';
                    
      COMMENT ON COLUMN nop_sys_dict.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_sys_dict.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_dict.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_dict.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_dict.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_dict.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_dict.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_dict.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_i18n IS '多语言消息';
                
      COMMENT ON COLUMN nop_sys_i18n.i18n_key IS '字符串Key';
                    
      COMMENT ON COLUMN nop_sys_i18n.i18n_locale IS '语言';
                    
      COMMENT ON COLUMN nop_sys_i18n.value IS '值';
                    
      COMMENT ON COLUMN nop_sys_i18n.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_i18n.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_i18n.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_i18n.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_i18n.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_i18n.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_checker_record IS 'MakerChecker审批记录';
                
      COMMENT ON COLUMN nop_sys_checker_record.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_checker_record.biz_obj_name IS '业务对象名';
                    
      COMMENT ON COLUMN nop_sys_checker_record.biz_obj_id IS '业务对象ID';
                    
      COMMENT ON COLUMN nop_sys_checker_record.maker_id IS '请求发起人ID';
                    
      COMMENT ON COLUMN nop_sys_checker_record.maker_name IS '请求发起人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.request_action IS '请求操作';
                    
      COMMENT ON COLUMN nop_sys_checker_record.request_data IS '请求数据';
                    
      COMMENT ON COLUMN nop_sys_checker_record.request_time IS '请求时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.checker_id IS '审批人ID';
                    
      COMMENT ON COLUMN nop_sys_checker_record.checker_name IS '审批人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.check_time IS '审批时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.try_result IS '请求结果';
                    
      COMMENT ON COLUMN nop_sys_checker_record.input_page IS '输入页面';
                    
      COMMENT ON COLUMN nop_sys_checker_record.status IS '审批状态';
                    
      COMMENT ON COLUMN nop_sys_checker_record.cancel_action IS '取消方法';
                    
      COMMENT ON COLUMN nop_sys_checker_record.cb_err_code IS '回调错误码';
                    
      COMMENT ON COLUMN nop_sys_checker_record.ce_err_msg IS '回调错误消息';
                    
      COMMENT ON COLUMN nop_sys_checker_record.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_checker_record.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_code_rule IS '编码规则';
                
      COMMENT ON COLUMN nop_sys_code_rule.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_code_rule.name IS '名称';
                    
      COMMENT ON COLUMN nop_sys_code_rule.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_sys_code_rule.code_pattern IS '编码模式';
                    
      COMMENT ON COLUMN nop_sys_code_rule.seq_name IS '序列号名称';
                    
      COMMENT ON COLUMN nop_sys_code_rule.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_code_rule.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_code_rule.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_code_rule.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_code_rule.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_code_rule.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_code_rule.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_notice_template IS '通知模板';
                
      COMMENT ON COLUMN nop_sys_notice_template.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_notice_template.name IS '名称';
                    
      COMMENT ON COLUMN nop_sys_notice_template.tpl_type IS '模板类型';
                    
      COMMENT ON COLUMN nop_sys_notice_template.content IS '模板内容';
                    
      COMMENT ON COLUMN nop_sys_notice_template.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_notice_template.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_notice_template.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_notice_template.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_notice_template.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_notice_template.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_notice_template.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_user_variable IS '用户变量';
                
      COMMENT ON COLUMN nop_sys_user_variable.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_sys_user_variable.var_name IS '变量名';
                    
      COMMENT ON COLUMN nop_sys_user_variable.var_value IS '变量值';
                    
      COMMENT ON COLUMN nop_sys_user_variable.std_domain IS '变量域';
                    
      COMMENT ON COLUMN nop_sys_user_variable.var_type IS '变量类型';
                    
      COMMENT ON COLUMN nop_sys_user_variable.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_user_variable.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_user_variable.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_user_variable.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_user_variable.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_user_variable.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_variable IS '系统变量';
                
      COMMENT ON COLUMN nop_sys_variable.var_name IS '变量名';
                    
      COMMENT ON COLUMN nop_sys_variable.var_value IS '变量值';
                    
      COMMENT ON COLUMN nop_sys_variable.std_domain IS '变量域';
                    
      COMMENT ON COLUMN nop_sys_variable.var_type IS '变量类型';
                    
      COMMENT ON COLUMN nop_sys_variable.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_variable.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_variable.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_variable.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_variable.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_variable.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_ext_field IS '扩展字段';
                
      COMMENT ON COLUMN nop_sys_ext_field.entity_name IS '实体名';
                    
      COMMENT ON COLUMN nop_sys_ext_field.entity_id IS '实体ID';
                    
      COMMENT ON COLUMN nop_sys_ext_field.field_name IS '字段名';
                    
      COMMENT ON COLUMN nop_sys_ext_field.field_type IS '字段类型';
                    
      COMMENT ON COLUMN nop_sys_ext_field.decimal_scale IS '浮点精度';
                    
      COMMENT ON COLUMN nop_sys_ext_field.decimal_value IS '浮点值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.date_value IS '日期值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.timestamp_value IS '时间点值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.string_value IS '字符串值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_ext_field.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_ext_field.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_ext_field.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_ext_field.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_ext_field.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_lock IS '资源锁';
                
      COMMENT ON COLUMN nop_sys_lock.lock_name IS '锁名称';
                    
      COMMENT ON COLUMN nop_sys_lock.lock_group IS '分组';
                    
      COMMENT ON COLUMN nop_sys_lock.lock_time IS '锁定时间';
                    
      COMMENT ON COLUMN nop_sys_lock.expire_at IS '过期时间';
                    
      COMMENT ON COLUMN nop_sys_lock.lock_reason IS '锁定原因';
                    
      COMMENT ON COLUMN nop_sys_lock.holder_id IS '锁的持有者';
                    
      COMMENT ON COLUMN nop_sys_lock.holder_adder IS '持有者地址';
                    
      COMMENT ON COLUMN nop_sys_lock.app_id IS '应用ID';
                    
      COMMENT ON COLUMN nop_sys_lock.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_lock.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_lock.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_lock.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_lock.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_cluster_leader IS '集群选举';
                
      COMMENT ON COLUMN nop_sys_cluster_leader.cluster_id IS '集群ID';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.leader_id IS '主服务器ID';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.leader_adder IS '主服务器地址';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.leader_epoch IS '选举世代';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.elect_time IS '选举时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.expire_at IS '过期时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.refresh_time IS '刷新时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.version IS '修改版本';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.app_name IS '应用名';
                    
      COMMENT ON TABLE nop_sys_event IS '事件队列';
                
      COMMENT ON COLUMN nop_sys_event.event_id IS '事件ID';
                    
      COMMENT ON COLUMN nop_sys_event.event_topic IS '事件主题';
                    
      COMMENT ON COLUMN nop_sys_event.event_name IS '事件名称';
                    
      COMMENT ON COLUMN nop_sys_event.event_headers IS '事件元数据';
                    
      COMMENT ON COLUMN nop_sys_event.event_data IS '数据';
                    
      COMMENT ON COLUMN nop_sys_event.selection IS '字段选择';
                    
      COMMENT ON COLUMN nop_sys_event.event_time IS '事件时间';
                    
      COMMENT ON COLUMN nop_sys_event.event_status IS '事件状态';
                    
      COMMENT ON COLUMN nop_sys_event.process_time IS '处理时间';
                    
      COMMENT ON COLUMN nop_sys_event.schedule_time IS '调度时间';
                    
      COMMENT ON COLUMN nop_sys_event.is_broadcast IS '是否广播';
                    
      COMMENT ON COLUMN nop_sys_event.biz_obj_name IS '业务对象名';
                    
      COMMENT ON COLUMN nop_sys_event.biz_key IS '业务标识';
                    
      COMMENT ON COLUMN nop_sys_event.biz_date IS '业务日期';
                    
      COMMENT ON COLUMN nop_sys_event.partition_index IS '数据分区';
                    
      COMMENT ON COLUMN nop_sys_event.retry_times IS '重试次数';
                    
      COMMENT ON COLUMN nop_sys_event.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_event.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_event.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_event.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_event.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_service_instance IS '服务实例';
                
      COMMENT ON COLUMN nop_sys_service_instance.instance_id IS '服务实例ID';
                    
      COMMENT ON COLUMN nop_sys_service_instance.service_name IS '服务名';
                    
      COMMENT ON COLUMN nop_sys_service_instance.cluster_name IS '集群名';
                    
      COMMENT ON COLUMN nop_sys_service_instance.group_name IS '分组名';
                    
      COMMENT ON COLUMN nop_sys_service_instance.tags_text IS '标签';
                    
      COMMENT ON COLUMN nop_sys_service_instance.server_addr IS '服务地址';
                    
      COMMENT ON COLUMN nop_sys_service_instance.server_port IS '服务端口';
                    
      COMMENT ON COLUMN nop_sys_service_instance.weight IS '权重';
                    
      COMMENT ON COLUMN nop_sys_service_instance.meta_data IS '扩展数据';
                    
      COMMENT ON COLUMN nop_sys_service_instance.is_healthy IS '是否健康';
                    
      COMMENT ON COLUMN nop_sys_service_instance.is_enabled IS '是否启用';
                    
      COMMENT ON COLUMN nop_sys_service_instance.is_ephemeral IS '是否临时';
                    
      COMMENT ON COLUMN nop_sys_service_instance.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_service_instance.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_service_instance.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_change_log IS '变更跟踪日志';
                
      COMMENT ON COLUMN nop_sys_change_log.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_change_log.biz_obj_name IS '业务对象';
                    
      COMMENT ON COLUMN nop_sys_change_log.obj_id IS '对象ID';
                    
      COMMENT ON COLUMN nop_sys_change_log.biz_key IS '业务键';
                    
      COMMENT ON COLUMN nop_sys_change_log.operation_name IS '业务操作';
                    
      COMMENT ON COLUMN nop_sys_change_log.prop_name IS '属性名';
                    
      COMMENT ON COLUMN nop_sys_change_log.old_value IS '旧值';
                    
      COMMENT ON COLUMN nop_sys_change_log.new_value IS '新值';
                    
      COMMENT ON COLUMN nop_sys_change_log.change_time IS '变更时间';
                    
      COMMENT ON COLUMN nop_sys_change_log.app_id IS '应用ID';
                    
      COMMENT ON COLUMN nop_sys_change_log.operator_id IS '操作人';
                    
      COMMENT ON COLUMN nop_sys_change_log.approver_id IS '审核人';
                    
      COMMENT ON TABLE nop_sys_tag IS '标签';
                
      COMMENT ON COLUMN nop_sys_tag.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_tag.name IS '名称';
                    
      COMMENT ON COLUMN nop_sys_tag.description IS '描述';
                    
      COMMENT ON COLUMN nop_sys_tag.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_tag.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_tag.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_tag.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_tag.update_time IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_dict_option IS '字典明细';
                
      COMMENT ON COLUMN nop_sys_dict_option.sid IS '主键';
                    
      COMMENT ON COLUMN nop_sys_dict_option.dict_id IS '字典ID';
                    
      COMMENT ON COLUMN nop_sys_dict_option.label IS '显示名';
                    
      COMMENT ON COLUMN nop_sys_dict_option.value IS '值';
                    
      COMMENT ON COLUMN nop_sys_dict_option.code_value IS '内部编码';
                    
      COMMENT ON COLUMN nop_sys_dict_option.group_name IS '分组名';
                    
      COMMENT ON COLUMN nop_sys_dict_option.is_internal IS '是否内部';
                    
      COMMENT ON COLUMN nop_sys_dict_option.is_deprecated IS '是否已废弃';
                    
      COMMENT ON COLUMN nop_sys_dict_option.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_dict_option.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_dict_option.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_dict_option.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_dict_option.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_dict_option.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_dict_option.remark IS '备注';
                    
      COMMENT ON TABLE nop_sys_obj_tag IS '对象标签';
                
      COMMENT ON COLUMN nop_sys_obj_tag.biz_obj_id IS '对象ID';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.biz_obj_name IS '对象名';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.tag_id IS '标签ID';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_obj_tag.update_time IS '修改时间';
                    
