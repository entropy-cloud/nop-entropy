
CREATE TABLE nop_wf_definition(
  WF_DEF_ID VARCHAR(32) NOT NULL ,
  WF_NAME VARCHAR(500) NOT NULL ,
  WF_VERSION INT8 NOT NULL ,
  DISPLAY_NAME VARCHAR(200) NOT NULL ,
  DESCRIPTION VARCHAR(1000)  ,
  MODEL_TEXT TEXT NOT NULL ,
  STATUS INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_wf_definition primary key (WF_DEF_ID)
);

CREATE TABLE nop_wf_step_instance_link(
  WF_ID VARCHAR(32) NOT NULL ,
  STEP_ID VARCHAR(32) NOT NULL ,
  PREV_STEP_ID VARCHAR(32) NOT NULL ,
  EXEC_ACTION VARCHAR(200) NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_step_instance_link primary key (WF_ID,STEP_ID,PREV_STEP_ID)
);

CREATE TABLE nop_wf_action(
  SID VARCHAR(32) NOT NULL ,
  WF_ID VARCHAR(32) NOT NULL ,
  STEP_ID VARCHAR(32) NOT NULL ,
  ACTION_NAME VARCHAR(200) NOT NULL ,
  EXEC_TIME TIMESTAMP NOT NULL ,
  CALLER_ID VARCHAR(50)  ,
  CALLER_NAME VARCHAR(50)  ,
  OPINION VARCHAR(4000)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_action primary key (SID)
);

CREATE TABLE nop_wf_output(
  WF_ID VARCHAR(32) NOT NULL ,
  FIELD_NAME VARCHAR(100) NOT NULL ,
  FIELD_TYPE INT4 NOT NULL ,
  STRING_VALUE VARCHAR(4000)  ,
  DECIMAL_VALUE NUMERIC(30,6)  ,
  LONG_VALUE INT8  ,
  DATE_VALUE DATE  ,
  TIMESTAMP_VALUE TIMESTAMP  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_output primary key (WF_ID,FIELD_NAME)
);

CREATE TABLE nop_wf_var(
  WF_ID VARCHAR(32) NOT NULL ,
  FIELD_NAME VARCHAR(100) NOT NULL ,
  FIELD_TYPE INT4 NOT NULL ,
  STRING_VALUE VARCHAR(4000)  ,
  DECIMAL_VALUE NUMERIC(30,6)  ,
  LONG_VALUE INT8  ,
  DATE_VALUE DATE  ,
  TIMESTAMP_VALUE TIMESTAMP  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_var primary key (WF_ID,FIELD_NAME)
);

CREATE TABLE nop_wf_log(
  SID VARCHAR(32) NOT NULL ,
  WF_ID VARCHAR(32) NOT NULL ,
  LOG_LEVEL INT4 NOT NULL ,
  LOG_MSG VARCHAR(4000)  ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_wf_log primary key (SID)
);

CREATE TABLE nop_wf_work(
  WORK_ID VARCHAR(32) NOT NULL ,
  WORK_TYPE VARCHAR(10) NOT NULL ,
  TITLE VARCHAR(2000) NOT NULL ,
  LINK_URL VARCHAR(2000) NOT NULL ,
  STATUS INT4 NOT NULL ,
  OWNER_ID VARCHAR(50)  ,
  OWNER_NAME VARCHAR(50)  ,
  CALLER_ID VARCHAR(50)  ,
  CALLER_NAME VARCHAR(50)  ,
  READ_TIME TIMESTAMP  ,
  FINISH_TIME TIMESTAMP  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_wf_work primary key (WORK_ID)
);

CREATE TABLE nop_wf_step_instance(
  STEP_ID VARCHAR(32) NOT NULL ,
  WF_ID VARCHAR(32) NOT NULL ,
  STEP_TYPE VARCHAR(10) NOT NULL ,
  STEP_NAME VARCHAR(200) NOT NULL ,
  STATUS INT4 NOT NULL ,
  APP_STATE VARCHAR(100)  ,
  SUB_WF_ID VARCHAR(32)  ,
  SUB_WF_NAME VARCHAR(200)  ,
  SUB_WF_VERSION INT8  ,
  IS_READ BOOLEAN  ,
  ACTOR_TYPE VARCHAR(10)  ,
  ACTOR_ID VARCHAR(100)  ,
  ACTOR_DEPT_ID VARCHAR(50)  ,
  ACTOR_NAME VARCHAR(100)  ,
  OWNER_ID VARCHAR(50)  ,
  OWNER_NAME VARCHAR(50)  ,
  ASSIGNER_ID VARCHAR(50)  ,
  ASSIGNER_NAME VARCHAR(50)  ,
  CALLER_ID VARCHAR(50)  ,
  CALLER_NAME VARCHAR(50)  ,
  CANCELLER_ID VARCHAR(50)  ,
  CANCELLER_NAME VARCHAR(50)  ,
  FROM_ACTION VARCHAR(200)  ,
  LAST_ACTION VARCHAR(200)  ,
  START_TIME TIMESTAMP  ,
  FINISH_TIME TIMESTAMP  ,
  DUE_TIME TIMESTAMP  ,
  READ_TIME TIMESTAMP  ,
  PRIORITY INT4 NOT NULL ,
  JOIN_TRACE VARCHAR(500)  ,
  TAG_SET VARCHAR(200)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_wf_step_instance primary key (STEP_ID)
);

CREATE TABLE nop_wf_instance(
  WF_ID VARCHAR(32) NOT NULL ,
  WF_NAME VARCHAR(500) NOT NULL ,
  WF_VERSION INT8 NOT NULL ,
  WF_PARAMS VARCHAR(4000)  ,
  TITLE VARCHAR(200) NOT NULL ,
  STATUS INT4 NOT NULL ,
  APP_STATE VARCHAR(100)  ,
  START_TIME TIMESTAMP  ,
  END_TIME TIMESTAMP  ,
  SUSPEND_TIME TIMESTAMP  ,
  DUE_TIME TIMESTAMP  ,
  BIZ_KEY VARCHAR(200)  ,
  BIZ_OBJ_NAME VARCHAR(200)  ,
  BIZ_OBJ_ID VARCHAR(200)  ,
  PARENT_WF_NAME VARCHAR(500)  ,
  PARENT_WF_VERSION INT8  ,
  PARENT_WF_ID VARCHAR(32)  ,
  PARENT_STEP_ID VARCHAR(200)  ,
  STARTER_ID VARCHAR(50)  ,
  STARTER_NAME VARCHAR(50)  ,
  STARTER_DEPT_ID VARCHAR(50)  ,
  CANCELLER_ID VARCHAR(50)  ,
  CANCELLER_NAME VARCHAR(50)  ,
  SUSPENDER_ID VARCHAR(50)  ,
  SUSPENDER_NAME VARCHAR(50)  ,
  MANAGER_TYPE VARCHAR(50)  ,
  MANAGER_DEPT_ID VARCHAR(50)  ,
  MANAGER_NAME VARCHAR(50)  ,
  MANAGER_ID VARCHAR(50)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_wf_instance primary key (WF_ID)
);


      COMMENT ON TABLE nop_wf_definition IS '工作流模型定义';
                
      COMMENT ON COLUMN nop_wf_definition.WF_DEF_ID IS '主键';
                    
      COMMENT ON COLUMN nop_wf_definition.WF_NAME IS '工作流名称';
                    
      COMMENT ON COLUMN nop_wf_definition.WF_VERSION IS '工作流版本';
                    
      COMMENT ON COLUMN nop_wf_definition.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_wf_definition.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_wf_definition.MODEL_TEXT IS '模型文本';
                    
      COMMENT ON COLUMN nop_wf_definition.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_wf_definition.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_definition.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_definition.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_definition.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_definition.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_definition.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_wf_step_instance_link IS '工作流步骤关联';
                
      COMMENT ON COLUMN nop_wf_step_instance_link.WF_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.STEP_ID IS '步骤ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.PREV_STEP_ID IS '迁移步骤 ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.EXEC_ACTION IS '执行动作';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_step_instance_link.CREATE_TIME IS '创建时间';
                    
      COMMENT ON TABLE nop_wf_action IS '工作流动作';
                
      COMMENT ON COLUMN nop_wf_action.SID IS '主键';
                    
      COMMENT ON COLUMN nop_wf_action.WF_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_action.STEP_ID IS '工作流步骤ID';
                    
      COMMENT ON COLUMN nop_wf_action.ACTION_NAME IS '动作ID';
                    
      COMMENT ON COLUMN nop_wf_action.EXEC_TIME IS '执行时刻';
                    
      COMMENT ON COLUMN nop_wf_action.CALLER_ID IS '调用者ID';
                    
      COMMENT ON COLUMN nop_wf_action.CALLER_NAME IS '调用者姓名';
                    
      COMMENT ON COLUMN nop_wf_action.OPINION IS '意见';
                    
      COMMENT ON COLUMN nop_wf_action.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_action.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_action.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_action.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_action.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_wf_output IS '工作流输出变量';
                
      COMMENT ON COLUMN nop_wf_output.WF_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_output.FIELD_NAME IS '变量名';
                    
      COMMENT ON COLUMN nop_wf_output.FIELD_TYPE IS '变量类型';
                    
      COMMENT ON COLUMN nop_wf_output.STRING_VALUE IS '字符串值';
                    
      COMMENT ON COLUMN nop_wf_output.DECIMAL_VALUE IS '浮点值';
                    
      COMMENT ON COLUMN nop_wf_output.LONG_VALUE IS '整数型';
                    
      COMMENT ON COLUMN nop_wf_output.DATE_VALUE IS '日期值';
                    
      COMMENT ON COLUMN nop_wf_output.TIMESTAMP_VALUE IS '时间点值';
                    
      COMMENT ON COLUMN nop_wf_output.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_output.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_output.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_output.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_output.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_wf_var IS '工作流状态变量';
                
      COMMENT ON COLUMN nop_wf_var.WF_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_var.FIELD_NAME IS '变量名';
                    
      COMMENT ON COLUMN nop_wf_var.FIELD_TYPE IS '变量类型';
                    
      COMMENT ON COLUMN nop_wf_var.STRING_VALUE IS '字符串值';
                    
      COMMENT ON COLUMN nop_wf_var.DECIMAL_VALUE IS '浮点值';
                    
      COMMENT ON COLUMN nop_wf_var.LONG_VALUE IS '整数型';
                    
      COMMENT ON COLUMN nop_wf_var.DATE_VALUE IS '日期值';
                    
      COMMENT ON COLUMN nop_wf_var.TIMESTAMP_VALUE IS '时间点值';
                    
      COMMENT ON COLUMN nop_wf_var.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_var.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_var.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_var.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_var.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_wf_log IS '工作流日志';
                
      COMMENT ON COLUMN nop_wf_log.SID IS '日志ID';
                    
      COMMENT ON COLUMN nop_wf_log.WF_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_log.LOG_LEVEL IS '日志级别';
                    
      COMMENT ON COLUMN nop_wf_log.LOG_MSG IS '日志消息';
                    
      COMMENT ON COLUMN nop_wf_log.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_log.CREATE_TIME IS '创建时间';
                    
      COMMENT ON TABLE nop_wf_work IS '代办工作';
                
      COMMENT ON COLUMN nop_wf_work.WORK_ID IS '工作ID';
                    
      COMMENT ON COLUMN nop_wf_work.WORK_TYPE IS '工作类型';
                    
      COMMENT ON COLUMN nop_wf_work.TITLE IS '工作标题';
                    
      COMMENT ON COLUMN nop_wf_work.LINK_URL IS '工作链接';
                    
      COMMENT ON COLUMN nop_wf_work.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_wf_work.OWNER_ID IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_wf_work.OWNER_NAME IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_wf_work.CALLER_ID IS '调用者ID';
                    
      COMMENT ON COLUMN nop_wf_work.CALLER_NAME IS '调用者姓名';
                    
      COMMENT ON COLUMN nop_wf_work.READ_TIME IS '读取时间';
                    
      COMMENT ON COLUMN nop_wf_work.FINISH_TIME IS '完成时间';
                    
      COMMENT ON COLUMN nop_wf_work.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_work.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_work.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_work.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_work.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_work.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_wf_step_instance IS '工作流步骤实例';
                
      COMMENT ON COLUMN nop_wf_step_instance.STEP_ID IS '步骤ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.WF_ID IS '工作流实例ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.STEP_TYPE IS '步骤类型';
                    
      COMMENT ON COLUMN nop_wf_step_instance.STEP_NAME IS '步骤名称';
                    
      COMMENT ON COLUMN nop_wf_step_instance.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_wf_step_instance.APP_STATE IS '应用状态';
                    
      COMMENT ON COLUMN nop_wf_step_instance.SUB_WF_ID IS '子工作流ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.SUB_WF_NAME IS '子工作流名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.SUB_WF_VERSION IS '子流程版本';
                    
      COMMENT ON COLUMN nop_wf_step_instance.IS_READ IS '是否已读';
                    
      COMMENT ON COLUMN nop_wf_step_instance.ACTOR_TYPE IS '参与者类型';
                    
      COMMENT ON COLUMN nop_wf_step_instance.ACTOR_ID IS '参与者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.ACTOR_DEPT_ID IS '参与者部门ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.ACTOR_NAME IS '参与者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.OWNER_ID IS '拥有者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.OWNER_NAME IS '拥有者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.ASSIGNER_ID IS '分配者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.ASSIGNER_NAME IS '分配者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.CALLER_ID IS '调用者ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.CALLER_NAME IS '调用者姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.CANCELLER_ID IS '取消人ID';
                    
      COMMENT ON COLUMN nop_wf_step_instance.CANCELLER_NAME IS '取消人姓名';
                    
      COMMENT ON COLUMN nop_wf_step_instance.FROM_ACTION IS '来源操作';
                    
      COMMENT ON COLUMN nop_wf_step_instance.LAST_ACTION IS '最后一次操作';
                    
      COMMENT ON COLUMN nop_wf_step_instance.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.FINISH_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.DUE_TIME IS '到期时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.READ_TIME IS '读取时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.PRIORITY IS '优先级';
                    
      COMMENT ON COLUMN nop_wf_step_instance.JOIN_TRACE IS '汇聚路径';
                    
      COMMENT ON COLUMN nop_wf_step_instance.TAG_SET IS '标签';
                    
      COMMENT ON COLUMN nop_wf_step_instance.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_step_instance.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_step_instance.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_step_instance.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_step_instance.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_wf_instance IS '工作流模型定义';
                
      COMMENT ON COLUMN nop_wf_instance.WF_ID IS '主键';
                    
      COMMENT ON COLUMN nop_wf_instance.WF_NAME IS '工作流名称';
                    
      COMMENT ON COLUMN nop_wf_instance.WF_VERSION IS '工作流版本';
                    
      COMMENT ON COLUMN nop_wf_instance.WF_PARAMS IS '工作流参数';
                    
      COMMENT ON COLUMN nop_wf_instance.TITLE IS '实例标题';
                    
      COMMENT ON COLUMN nop_wf_instance.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_wf_instance.APP_STATE IS '应用状态';
                    
      COMMENT ON COLUMN nop_wf_instance.START_TIME IS '启动时间';
                    
      COMMENT ON COLUMN nop_wf_instance.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_wf_instance.SUSPEND_TIME IS '暂停时间';
                    
      COMMENT ON COLUMN nop_wf_instance.DUE_TIME IS '完成时限';
                    
      COMMENT ON COLUMN nop_wf_instance.BIZ_KEY IS '业务唯一键';
                    
      COMMENT ON COLUMN nop_wf_instance.BIZ_OBJ_NAME IS '业务对象名';
                    
      COMMENT ON COLUMN nop_wf_instance.BIZ_OBJ_ID IS '业务对象ID';
                    
      COMMENT ON COLUMN nop_wf_instance.PARENT_WF_NAME IS '父工作流名称';
                    
      COMMENT ON COLUMN nop_wf_instance.PARENT_WF_VERSION IS '父流程版本';
                    
      COMMENT ON COLUMN nop_wf_instance.PARENT_WF_ID IS '父流程ID';
                    
      COMMENT ON COLUMN nop_wf_instance.PARENT_STEP_ID IS '父流程步骤ID';
                    
      COMMENT ON COLUMN nop_wf_instance.STARTER_ID IS '启动人ID';
                    
      COMMENT ON COLUMN nop_wf_instance.STARTER_NAME IS '启动人';
                    
      COMMENT ON COLUMN nop_wf_instance.STARTER_DEPT_ID IS '启动人单位ID';
                    
      COMMENT ON COLUMN nop_wf_instance.CANCELLER_ID IS '取消人ID';
                    
      COMMENT ON COLUMN nop_wf_instance.CANCELLER_NAME IS '取消人';
                    
      COMMENT ON COLUMN nop_wf_instance.SUSPENDER_ID IS '暂停人ID';
                    
      COMMENT ON COLUMN nop_wf_instance.SUSPENDER_NAME IS '暂停人';
                    
      COMMENT ON COLUMN nop_wf_instance.MANAGER_TYPE IS '管理者类型';
                    
      COMMENT ON COLUMN nop_wf_instance.MANAGER_DEPT_ID IS '管理者单位ID';
                    
      COMMENT ON COLUMN nop_wf_instance.MANAGER_NAME IS '管理者';
                    
      COMMENT ON COLUMN nop_wf_instance.MANAGER_ID IS '管理者ID';
                    
      COMMENT ON COLUMN nop_wf_instance.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_wf_instance.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_wf_instance.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_wf_instance.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_wf_instance.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_wf_instance.REMARK IS '备注';
                    
