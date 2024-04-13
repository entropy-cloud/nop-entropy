
CREATE TABLE nop_task_definition(
  TASK_DEF_ID VARCHAR2(32) NOT NULL ,
  TASK_NAME VARCHAR2(500) NOT NULL ,
  TASK_VERSION NUMBER(20) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  DESCRIPTION VARCHAR2(1000)  ,
  MODEL_TEXT CLOB NOT NULL ,
  STATUS INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_task_definition primary key (TASK_DEF_ID)
);

CREATE TABLE nop_task_instance(
  TASK_ID VARCHAR2(32) NOT NULL ,
  TASK_NAME VARCHAR2(500) NOT NULL ,
  TASK_VERSION NUMBER(20) NOT NULL ,
  TASK_INPUTS VARCHAR2(4000)  ,
  TASK_GROUP VARCHAR2(100) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  START_TIME TIMESTAMP  ,
  END_TIME TIMESTAMP  ,
  DUE_TIME TIMESTAMP  ,
  BIZ_KEY VARCHAR2(200)  ,
  BIZ_OBJ_NAME VARCHAR2(200)  ,
  BIZ_OBJ_ID VARCHAR2(200)  ,
  PARENT_TASK_NAME VARCHAR2(500)  ,
  PARENT_TASK_VERSION NUMBER(20)  ,
  PARENT_TASK_ID VARCHAR2(32)  ,
  PARENT_STEP_ID VARCHAR2(200)  ,
  STARTER_ID VARCHAR2(50)  ,
  STARTER_NAME VARCHAR2(50)  ,
  STARTER_DEPT_ID VARCHAR2(50)  ,
  MANAGER_TYPE VARCHAR2(50)  ,
  MANAGER_DEPT_ID VARCHAR2(50)  ,
  MANAGER_NAME VARCHAR2(50)  ,
  MANAGER_ID VARCHAR2(50)  ,
  PRIORITY INTEGER NOT NULL ,
  SIGNAL_TEXT VARCHAR2(1000)  ,
  TAG_TEXT VARCHAR2(200)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_task_instance primary key (TASK_ID)
);

CREATE TABLE nop_task_definition_auth(
  SID VARCHAR2(32) NOT NULL ,
  TASK_DEF_ID VARCHAR2(32) NOT NULL ,
  ACTOR_TYPE VARCHAR2(10) NOT NULL ,
  ACTOR_ID VARCHAR2(100) NOT NULL ,
  ACTOR_DEPT_ID VARCHAR2(50)  ,
  ACTOR_NAME VARCHAR2(100) NOT NULL ,
  ALLOW_EDIT CHAR(1) NOT NULL ,
  ALLOW_MANAGE CHAR(1) NOT NULL ,
  ALLOW_START CHAR(1) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_task_definition_auth primary key (SID)
);

CREATE TABLE nop_task_step_instance(
  STEP_ID VARCHAR2(32) NOT NULL ,
  TASK_ID VARCHAR2(32) NOT NULL ,
  STEP_TYPE VARCHAR2(20) NOT NULL ,
  STEP_NAME VARCHAR2(200) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  SUB_TASK_ID VARCHAR2(32)  ,
  SUB_TASK_NAME VARCHAR2(200)  ,
  SUB_TASK_VERSION NUMBER(20)  ,
  START_TIME TIMESTAMP  ,
  FINISH_TIME TIMESTAMP  ,
  DUE_TIME TIMESTAMP  ,
  NEXT_RETRY_TIME TIMESTAMP  ,
  RETRY_COUNT INTEGER  ,
  ERR_CODE VARCHAR2(200)  ,
  ERR_MSG VARCHAR2(4000)  ,
  PRIORITY INTEGER NOT NULL ,
  TAG_TEXT VARCHAR2(200)  ,
  PARENT_STEP_ID VARCHAR2(32)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_task_step_instance primary key (STEP_ID)
);


      COMMENT ON TABLE nop_task_definition IS '逻辑流模型定义';
                
      COMMENT ON COLUMN nop_task_definition.TASK_DEF_ID IS '主键';
                    
      COMMENT ON COLUMN nop_task_definition.TASK_NAME IS '逻辑流名称';
                    
      COMMENT ON COLUMN nop_task_definition.TASK_VERSION IS '逻辑流版本';
                    
      COMMENT ON COLUMN nop_task_definition.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_task_definition.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_task_definition.MODEL_TEXT IS '模型文本';
                    
      COMMENT ON COLUMN nop_task_definition.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_task_definition.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_definition.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_task_definition.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_definition.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_task_definition.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_definition.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_task_instance IS '逻辑流实例';
                
      COMMENT ON COLUMN nop_task_instance.TASK_ID IS '主键';
                    
      COMMENT ON COLUMN nop_task_instance.TASK_NAME IS '逻辑流名称';
                    
      COMMENT ON COLUMN nop_task_instance.TASK_VERSION IS '逻辑流版本';
                    
      COMMENT ON COLUMN nop_task_instance.TASK_INPUTS IS '逻辑流参数';
                    
      COMMENT ON COLUMN nop_task_instance.TASK_GROUP IS '逻辑流分组';
                    
      COMMENT ON COLUMN nop_task_instance.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_task_instance.START_TIME IS '启动时间';
                    
      COMMENT ON COLUMN nop_task_instance.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_task_instance.DUE_TIME IS '完成时限';
                    
      COMMENT ON COLUMN nop_task_instance.BIZ_KEY IS '业务唯一键';
                    
      COMMENT ON COLUMN nop_task_instance.BIZ_OBJ_NAME IS '业务对象名';
                    
      COMMENT ON COLUMN nop_task_instance.BIZ_OBJ_ID IS '业务对象ID';
                    
      COMMENT ON COLUMN nop_task_instance.PARENT_TASK_NAME IS '父流程名称';
                    
      COMMENT ON COLUMN nop_task_instance.PARENT_TASK_VERSION IS '父流程版本';
                    
      COMMENT ON COLUMN nop_task_instance.PARENT_TASK_ID IS '父流程ID';
                    
      COMMENT ON COLUMN nop_task_instance.PARENT_STEP_ID IS '父流程步骤ID';
                    
      COMMENT ON COLUMN nop_task_instance.STARTER_ID IS '启动人ID';
                    
      COMMENT ON COLUMN nop_task_instance.STARTER_NAME IS '启动人';
                    
      COMMENT ON COLUMN nop_task_instance.STARTER_DEPT_ID IS '启动人单位ID';
                    
      COMMENT ON COLUMN nop_task_instance.MANAGER_TYPE IS '管理者类型';
                    
      COMMENT ON COLUMN nop_task_instance.MANAGER_DEPT_ID IS '管理者单位ID';
                    
      COMMENT ON COLUMN nop_task_instance.MANAGER_NAME IS '管理者';
                    
      COMMENT ON COLUMN nop_task_instance.MANAGER_ID IS '管理者ID';
                    
      COMMENT ON COLUMN nop_task_instance.PRIORITY IS '优先级';
                    
      COMMENT ON COLUMN nop_task_instance.SIGNAL_TEXT IS '信号集合';
                    
      COMMENT ON COLUMN nop_task_instance.TAG_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_task_instance.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_instance.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_task_instance.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_instance.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_task_instance.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_instance.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_task_definition_auth IS '逻辑流定义权限';
                
      COMMENT ON COLUMN nop_task_definition_auth.SID IS '主键';
                    
      COMMENT ON COLUMN nop_task_definition_auth.TASK_DEF_ID IS '工作流定义ID';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ACTOR_TYPE IS '参与者类型';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ACTOR_ID IS '参与者ID';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ACTOR_DEPT_ID IS '参与者部门ID';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ACTOR_NAME IS '参与者名称';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ALLOW_EDIT IS '允许编辑';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ALLOW_MANAGE IS '允许管理';
                    
      COMMENT ON COLUMN nop_task_definition_auth.ALLOW_START IS '允许启动';
                    
      COMMENT ON COLUMN nop_task_definition_auth.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_definition_auth.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_task_definition_auth.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_definition_auth.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_task_definition_auth.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_definition_auth.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_task_step_instance IS '逻辑流步骤实例';
                
      COMMENT ON COLUMN nop_task_step_instance.STEP_ID IS '步骤ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.TASK_ID IS '逻辑流实例ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.STEP_TYPE IS '步骤类型';
                    
      COMMENT ON COLUMN nop_task_step_instance.STEP_NAME IS '步骤名称';
                    
      COMMENT ON COLUMN nop_task_step_instance.DISPLAY_NAME IS '步骤显示名称';
                    
      COMMENT ON COLUMN nop_task_step_instance.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_task_step_instance.SUB_TASK_ID IS '子流程ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.SUB_TASK_NAME IS '子流程名称';
                    
      COMMENT ON COLUMN nop_task_step_instance.SUB_TASK_VERSION IS '子流程版本';
                    
      COMMENT ON COLUMN nop_task_step_instance.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.FINISH_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.DUE_TIME IS '到期时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.NEXT_RETRY_TIME IS '下次重试时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.RETRY_COUNT IS '已重试次数';
                    
      COMMENT ON COLUMN nop_task_step_instance.ERR_CODE IS '错误码';
                    
      COMMENT ON COLUMN nop_task_step_instance.ERR_MSG IS '错误消息';
                    
      COMMENT ON COLUMN nop_task_step_instance.PRIORITY IS '优先级';
                    
      COMMENT ON COLUMN nop_task_step_instance.TAG_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_task_step_instance.PARENT_STEP_ID IS '父步骤ID';
                    
      COMMENT ON COLUMN nop_task_step_instance.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_task_step_instance.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_task_step_instance.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_task_step_instance.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_task_step_instance.REMARK IS '备注';
                    
