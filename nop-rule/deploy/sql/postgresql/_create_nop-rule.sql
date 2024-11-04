
CREATE TABLE nop_rule_definition(
  RULE_ID VARCHAR(32) NOT NULL ,
  RULE_NAME VARCHAR(500) NOT NULL ,
  RULE_VERSION INT8 NOT NULL ,
  DISPLAY_NAME VARCHAR(200) NOT NULL ,
  RULE_GROUP VARCHAR(200) NOT NULL ,
  RULE_TYPE VARCHAR(10) NOT NULL ,
  DESCRIPTION VARCHAR(1000)  ,
  MODEL_TEXT TEXT  ,
  STATUS INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_rule_definition primary key (RULE_ID)
);

CREATE TABLE nop_rule_node(
  SID VARCHAR(32) NOT NULL ,
  RULE_ID VARCHAR(32) NOT NULL ,
  LABEL VARCHAR(200) NOT NULL ,
  SORT_NO INT4 NOT NULL ,
  PREDICATE VARCHAR(4000) NOT NULL ,
  OUTPUTS VARCHAR(50)  ,
  PARENT_ID VARCHAR(32)  ,
  IS_LEAF BOOLEAN NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_rule_node primary key (SID)
);

CREATE TABLE nop_rule_role(
  SID VARCHAR(32) NOT NULL ,
  RULE_ID VARCHAR(32) NOT NULL ,
  ROLE_ID VARCHAR(100) NOT NULL ,
  IS_ADMIN INT4 default 0  NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_rule_role primary key (SID)
);

CREATE TABLE nop_rule_log(
  SID VARCHAR(32) NOT NULL ,
  RULE_ID VARCHAR(32) NOT NULL ,
  LOG_LEVEL INT4 NOT NULL ,
  LOG_MSG VARCHAR(4000)  ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_rule_log primary key (SID)
);


      COMMENT ON TABLE nop_rule_definition IS '规则模型定义';
                
      COMMENT ON COLUMN nop_rule_definition.RULE_ID IS '主键';
                    
      COMMENT ON COLUMN nop_rule_definition.RULE_NAME IS '规则名称';
                    
      COMMENT ON COLUMN nop_rule_definition.RULE_VERSION IS '规则版本';
                    
      COMMENT ON COLUMN nop_rule_definition.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_rule_definition.RULE_GROUP IS '规则分组';
                    
      COMMENT ON COLUMN nop_rule_definition.RULE_TYPE IS '规则类型';
                    
      COMMENT ON COLUMN nop_rule_definition.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_rule_definition.MODEL_TEXT IS '模型文本';
                    
      COMMENT ON COLUMN nop_rule_definition.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_rule_definition.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_rule_definition.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_definition.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_rule_definition.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_rule_definition.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_rule_definition.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_rule_node IS '规则节点';
                
      COMMENT ON COLUMN nop_rule_node.SID IS 'SID';
                    
      COMMENT ON COLUMN nop_rule_node.RULE_ID IS '规则ID';
                    
      COMMENT ON COLUMN nop_rule_node.LABEL IS '显示标签';
                    
      COMMENT ON COLUMN nop_rule_node.SORT_NO IS '排序序号';
                    
      COMMENT ON COLUMN nop_rule_node.PREDICATE IS '判断条件';
                    
      COMMENT ON COLUMN nop_rule_node.OUTPUTS IS '输出结果';
                    
      COMMENT ON COLUMN nop_rule_node.PARENT_ID IS '父ID';
                    
      COMMENT ON COLUMN nop_rule_node.IS_LEAF IS '是否叶子节点';
                    
      COMMENT ON COLUMN nop_rule_node.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_rule_node.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_node.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_rule_node.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_rule_node.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_rule_node.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_rule_role IS '规则角色';
                
      COMMENT ON COLUMN nop_rule_role.SID IS '主键';
                    
      COMMENT ON COLUMN nop_rule_role.RULE_ID IS 'Rule ID';
                    
      COMMENT ON COLUMN nop_rule_role.ROLE_ID IS 'Role ID';
                    
      COMMENT ON COLUMN nop_rule_role.IS_ADMIN IS '是否管理者';
                    
      COMMENT ON COLUMN nop_rule_role.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_rule_role.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_role.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_rule_role.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_rule_role.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_rule_role.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_rule_log IS '规则执行日志';
                
      COMMENT ON COLUMN nop_rule_log.SID IS '日志ID';
                    
      COMMENT ON COLUMN nop_rule_log.RULE_ID IS '规则ID';
                    
      COMMENT ON COLUMN nop_rule_log.LOG_LEVEL IS '日志级别';
                    
      COMMENT ON COLUMN nop_rule_log.LOG_MSG IS '日志消息';
                    
      COMMENT ON COLUMN nop_rule_log.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_rule_log.CREATE_TIME IS '创建时间';
                    
