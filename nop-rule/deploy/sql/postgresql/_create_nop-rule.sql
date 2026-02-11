
CREATE TABLE nop_rule_definition(
  rule_id VARCHAR(32) NOT NULL ,
  rule_name VARCHAR(500) NOT NULL ,
  rule_version INT8 NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  rule_group VARCHAR(200) NOT NULL ,
  rule_type VARCHAR(10) NOT NULL ,
  description VARCHAR(1000)  ,
  model_text TEXT  ,
  status INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_rule_definition primary key (rule_id)
);

CREATE TABLE nop_rule_node(
  sid VARCHAR(32) NOT NULL ,
  rule_id VARCHAR(32) NOT NULL ,
  label VARCHAR(200) NOT NULL ,
  sort_no INT4 NOT NULL ,
  predicate VARCHAR(4000) NOT NULL ,
  outputs VARCHAR(50)  ,
  parent_id VARCHAR(32)  ,
  is_leaf BOOLEAN NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_rule_node primary key (sid)
);

CREATE TABLE nop_rule_role(
  sid VARCHAR(32) NOT NULL ,
  rule_id VARCHAR(32) NOT NULL ,
  role_id VARCHAR(100) NOT NULL ,
  is_admin INT4 default 0  NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_rule_role primary key (sid)
);

CREATE TABLE nop_rule_log(
  sid VARCHAR(32) NOT NULL ,
  rule_id VARCHAR(32) NOT NULL ,
  log_level INT4 NOT NULL ,
  log_msg VARCHAR(4000)  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  constraint PK_nop_rule_log primary key (sid)
);


      COMMENT ON TABLE nop_rule_definition IS '规则模型定义';
                
      COMMENT ON COLUMN nop_rule_definition.rule_id IS '主键';
                    
      COMMENT ON COLUMN nop_rule_definition.rule_name IS '规则名称';
                    
      COMMENT ON COLUMN nop_rule_definition.rule_version IS '规则版本';
                    
      COMMENT ON COLUMN nop_rule_definition.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_rule_definition.rule_group IS '规则分组';
                    
      COMMENT ON COLUMN nop_rule_definition.rule_type IS '规则类型';
                    
      COMMENT ON COLUMN nop_rule_definition.description IS '描述';
                    
      COMMENT ON COLUMN nop_rule_definition.model_text IS '模型文本';
                    
      COMMENT ON COLUMN nop_rule_definition.status IS '状态';
                    
      COMMENT ON COLUMN nop_rule_definition.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_rule_definition.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_definition.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_rule_definition.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_rule_definition.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_rule_definition.remark IS '备注';
                    
      COMMENT ON TABLE nop_rule_node IS '规则节点';
                
      COMMENT ON COLUMN nop_rule_node.sid IS 'SID';
                    
      COMMENT ON COLUMN nop_rule_node.rule_id IS '规则ID';
                    
      COMMENT ON COLUMN nop_rule_node.label IS '显示标签';
                    
      COMMENT ON COLUMN nop_rule_node.sort_no IS '排序序号';
                    
      COMMENT ON COLUMN nop_rule_node.predicate IS '判断条件';
                    
      COMMENT ON COLUMN nop_rule_node.outputs IS '输出结果';
                    
      COMMENT ON COLUMN nop_rule_node.parent_id IS '父ID';
                    
      COMMENT ON COLUMN nop_rule_node.is_leaf IS '是否叶子节点';
                    
      COMMENT ON COLUMN nop_rule_node.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_rule_node.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_node.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_rule_node.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_rule_node.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_rule_node.remark IS '备注';
                    
      COMMENT ON TABLE nop_rule_role IS '规则角色';
                
      COMMENT ON COLUMN nop_rule_role.sid IS '主键';
                    
      COMMENT ON COLUMN nop_rule_role.rule_id IS 'Rule ID';
                    
      COMMENT ON COLUMN nop_rule_role.role_id IS 'Role ID';
                    
      COMMENT ON COLUMN nop_rule_role.is_admin IS '是否管理者';
                    
      COMMENT ON COLUMN nop_rule_role.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_rule_role.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_role.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_rule_role.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_rule_role.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_rule_role.remark IS '备注';
                    
      COMMENT ON TABLE nop_rule_log IS '规则执行日志';
                
      COMMENT ON COLUMN nop_rule_log.sid IS '日志ID';
                    
      COMMENT ON COLUMN nop_rule_log.rule_id IS '规则ID';
                    
      COMMENT ON COLUMN nop_rule_log.log_level IS '日志级别';
                    
      COMMENT ON COLUMN nop_rule_log.log_msg IS '日志消息';
                    
      COMMENT ON COLUMN nop_rule_log.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_log.create_time IS '创建时间';
                    
