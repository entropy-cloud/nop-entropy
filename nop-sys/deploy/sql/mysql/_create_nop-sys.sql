
CREATE TABLE nop_sys_sequence(
  SEQ_NAME VARCHAR(150) NOT NULL    COMMENT '名称',
  SEQ_TYPE VARCHAR(10) NULL    COMMENT '类型',
  IS_UUID TINYINT default 0  NOT NULL    COMMENT '是否UUID',
  NEXT_VALUE BIGINT NOT NULL    COMMENT '下一个值',
  STEP_SIZE INTEGER NOT NULL    COMMENT '步长',
  CACHE_SIZE INTEGER NULL    COMMENT '缓存个数',
  MAX_VALUE BIGINT NULL    COMMENT '最大值',
  RESET_TYPE INTEGER NULL    COMMENT '重置方式',
  DEL_FLAG TINYINT NOT NULL    COMMENT '删除标识',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_sequence primary key (SEQ_NAME)
);

CREATE TABLE nop_sys_dict(
  SID VARCHAR(32) NOT NULL    COMMENT '主键',
  DICT_NAME VARCHAR(150) NOT NULL    COMMENT '字典名',
  DISPLAY_NAME VARCHAR(50) NOT NULL    COMMENT '显示名',
  DEL_FLAG TINYINT NOT NULL    COMMENT '删除标识',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_dict primary key (SID)
);

CREATE TABLE nop_sys_i18n(
  I18N_KEY VARCHAR(200) NOT NULL    COMMENT '字符串Key',
  I18N_LOCALE VARCHAR(20) NOT NULL    COMMENT '语言',
  VALUE VARCHAR(300) NOT NULL    COMMENT '值',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_i18n primary key (I18N_KEY,I18N_LOCALE)
);

CREATE TABLE nop_sys_checker_record(
  SID VARCHAR(32) NOT NULL    COMMENT '主键',
  BIZ_OBJ_NAME VARCHAR(200) NOT NULL    COMMENT '业务对象名',
  BIZ_OBJ_ID VARCHAR(100) NULL    COMMENT '业务对象ID',
  MAKER_ID VARCHAR(50) NOT NULL    COMMENT '请求发起人ID',
  MAKER_NAME VARCHAR(150) NOT NULL    COMMENT '请求发起人',
  REQUEST_ACTION VARCHAR(100) NOT NULL    COMMENT '请求操作',
  REQUEST_DATA MEDIUMTEXT NULL    COMMENT '请求数据',
  REQUEST_TIME DATETIME NOT NULL    COMMENT '请求时间',
  CHECKER_ID VARCHAR(50) NULL    COMMENT '审批人ID',
  CHECKER_NAME VARCHAR(150) NULL    COMMENT '审批人',
  CHECK_TIME DATETIME NULL    COMMENT '审批时间',
  TRY_RESULT MEDIUMTEXT NULL    COMMENT '请求结果',
  INPUT_PAGE VARCHAR(1000) NULL    COMMENT '输入页面',
  STATUS INTEGER NOT NULL    COMMENT '审批状态',
  CANCEL_ACTION VARCHAR(200) NULL    COMMENT '取消方法',
  CB_ERR_CODE VARCHAR(150) NULL    COMMENT '回调错误码',
  CE_ERR_MSG VARCHAR(1000) NULL    COMMENT '回调错误消息',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_checker_record primary key (SID)
);

CREATE TABLE nop_sys_code_rule(
  SID VARCHAR(32) NOT NULL    COMMENT '主键',
  NAME VARCHAR(100) NOT NULL    COMMENT '名称',
  DISPLAY_NAME VARCHAR(100) NOT NULL    COMMENT '显示名称',
  CODE_PATTERN VARCHAR(200) NOT NULL    COMMENT '编码模式',
  SEQ_NAME VARCHAR(100) NULL    COMMENT '序列号名称',
  DEL_FLAG TINYINT NOT NULL    COMMENT '删除标识',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_code_rule primary key (SID)
);

CREATE TABLE nop_sys_notice_template(
  SID VARCHAR(32) NOT NULL    COMMENT '主键',
  NAME VARCHAR(100) NOT NULL    COMMENT '名称',
  TPL_TYPE VARCHAR(10) NOT NULL    COMMENT '模板类型',
  CONTENT VARCHAR(4000) NOT NULL    COMMENT '模板内容',
  DEL_FLAG TINYINT NOT NULL    COMMENT '删除标识',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_notice_template primary key (SID)
);

CREATE TABLE nop_sys_user_variable(
  USER_ID VARCHAR(32) NOT NULL    COMMENT '用户ID',
  VAR_NAME VARCHAR(32) NOT NULL    COMMENT '变量名',
  VAR_VALUE VARCHAR(4000) NULL    COMMENT '变量值',
  STD_DOMAIN VARCHAR(100) NULL    COMMENT '变量域',
  VAR_TYPE VARCHAR(100) NULL    COMMENT '变量类型',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_user_variable primary key (USER_ID,VAR_NAME)
);

CREATE TABLE nop_sys_variable(
  VAR_NAME VARCHAR(32) NOT NULL    COMMENT '变量名',
  VAR_VALUE VARCHAR(4000) NULL    COMMENT '变量值',
  STD_DOMAIN VARCHAR(100) NULL    COMMENT '变量域',
  VAR_TYPE VARCHAR(100) NULL    COMMENT '变量类型',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_variable primary key (VAR_NAME)
);

CREATE TABLE nop_sys_ext_field(
  ENTITY_NAME VARCHAR(200) NOT NULL    COMMENT '实体名',
  ENTITY_ID VARCHAR(100) NOT NULL    COMMENT '实体ID',
  FIELD_NAME VARCHAR(100) NOT NULL    COMMENT '字段名',
  FIELD_TYPE INTEGER NOT NULL    COMMENT '字段类型',
  DECIMAL_SCALE TINYINT NULL    COMMENT '浮点精度',
  DECIMAL_VALUE DECIMAL(24,8) NULL    COMMENT '浮点值',
  DATE_VALUE DATE NULL    COMMENT '日期值',
  TIMESTAMP_VALUE DATETIME(3) NULL    COMMENT '时间点值',
  STRING_VALUE VARCHAR(4000) NULL    COMMENT '字符串值',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_ext_field primary key (ENTITY_NAME,ENTITY_ID,FIELD_NAME)
);

CREATE TABLE nop_sys_lock(
  LOCK_NAME VARCHAR(200) NOT NULL    COMMENT '锁名称',
  LOCK_GROUP VARCHAR(200) NOT NULL    COMMENT '分组',
  LOCK_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '锁定时间',
  EXPIRE_AT DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '过期时间',
  LOCK_REASON VARCHAR(200) NULL    COMMENT '锁定原因',
  HOLDER_ID VARCHAR(100) NOT NULL    COMMENT '锁的持有者',
  HOLDER_ADDER VARCHAR(100) NOT NULL    COMMENT '持有者地址',
  APP_ID VARCHAR(100) NOT NULL    COMMENT '应用ID',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  constraint PK_nop_sys_lock primary key (LOCK_NAME,LOCK_GROUP)
);

CREATE TABLE nop_sys_cluster_leader(
  CLUSTER_ID VARCHAR(200) NOT NULL    COMMENT '集群ID',
  LEADER_ID VARCHAR(100) NOT NULL    COMMENT '主服务器ID',
  LEADER_ADDER VARCHAR(100) NOT NULL    COMMENT '主服务器地址',
  LEADER_EPOCH BIGINT NOT NULL    COMMENT '选举世代',
  ELECT_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '选举时间',
  EXPIRE_AT DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '过期时间',
  REFRESH_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '刷新时间',
  VERSION INTEGER NOT NULL    COMMENT '修改版本',
  APP_NAME VARCHAR(100) NOT NULL    COMMENT '应用名',
  constraint PK_nop_sys_cluster_leader primary key (CLUSTER_ID)
);

CREATE TABLE nop_sys_event(
  EVENT_ID BIGINT NOT NULL    COMMENT '事件ID',
  EVENT_TOPIC VARCHAR(100) NOT NULL    COMMENT '事件主题',
  EVENT_NAME VARCHAR(100) NOT NULL    COMMENT '事件名称',
  EVENT_HEADERS JSON NOT NULL    COMMENT '事件元数据',
  EVENT_DATA JSON NOT NULL    COMMENT '数据',
  SELECTION VARCHAR(1000) NULL    COMMENT '字段选择',
  EVENT_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '事件时间',
  EVENT_STATUS INTEGER NOT NULL    COMMENT '事件状态',
  PROCESS_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '处理时间',
  SCHEDULE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '调度时间',
  IS_BROADCAST BOOLEAN NOT NULL    COMMENT '是否广播',
  BIZ_OBJ_NAME VARCHAR(100) NULL    COMMENT '业务对象名',
  BIZ_KEY VARCHAR(50) NULL    COMMENT '业务标识',
  BIZ_DATE DATE NOT NULL    COMMENT '业务日期',
  PARTITION_INDEX INTEGER NOT NULL    COMMENT '数据分区',
  RETRY_TIMES INTEGER NOT NULL    COMMENT '重试次数',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  constraint PK_nop_sys_event primary key (EVENT_ID)
);

CREATE TABLE nop_sys_service_instance(
  INSTANCE_ID VARCHAR(50) NOT NULL    COMMENT '服务实例ID',
  SERVICE_NAME VARCHAR(100) NOT NULL    COMMENT '服务名',
  CLUSTER_NAME VARCHAR(100) NOT NULL    COMMENT '集群名',
  GROUP_NAME VARCHAR(100) NOT NULL    COMMENT '分组名',
  TAGS_TEXT VARCHAR(100) NOT NULL    COMMENT '标签',
  SERVER_ADDR VARCHAR(20) NOT NULL    COMMENT '服务地址',
  SERVER_PORT INTEGER NOT NULL    COMMENT '服务端口',
  WEIGHT INTEGER NOT NULL    COMMENT '权重',
  META_DATA VARCHAR(1000) NULL    COMMENT '扩展数据',
  IS_HEALTHY BOOLEAN NOT NULL    COMMENT '是否健康',
  IS_ENABLED BOOLEAN NOT NULL    COMMENT '是否启用',
  IS_EPHEMERAL BOOLEAN NOT NULL    COMMENT '是否临时',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  constraint PK_nop_sys_service_instance primary key (INSTANCE_ID)
);

CREATE TABLE nop_sys_change_log(
  SID VARCHAR(32) NOT NULL    COMMENT '主键',
  BIZ_OBJ_NAME VARCHAR(100) NOT NULL    COMMENT '业务对象',
  OBJ_ID VARCHAR(100) NOT NULL    COMMENT '对象ID',
  BIZ_KEY VARCHAR(100) NULL    COMMENT '业务键',
  OPERATION_NAME VARCHAR(150) NOT NULL    COMMENT '业务操作',
  PROP_NAME VARCHAR(100) NOT NULL    COMMENT '属性名',
  OLD_VALUE VARCHAR(4000) NULL    COMMENT '旧值',
  NEW_VALUE VARCHAR(4000) NULL    COMMENT '新值',
  CHANGE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '变更时间',
  APP_ID VARCHAR(100) NULL    COMMENT '应用ID',
  OPERATOR_ID VARCHAR(50) NOT NULL    COMMENT '操作人',
  APPROVER_ID VARCHAR(50) NULL    COMMENT '审核人',
  constraint PK_nop_sys_change_log primary key (SID)
);

CREATE TABLE nop_sys_tag(
  SID BIGINT NOT NULL    COMMENT '主键',
  NAME VARCHAR(100) NOT NULL    COMMENT '名称',
  DESCRIPTION VARCHAR(500) NULL    COMMENT '描述',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  constraint PK_nop_sys_tag primary key (SID)
);

CREATE TABLE nop_sys_dict_option(
  SID VARCHAR(32) NOT NULL    COMMENT '主键',
  DICT_ID VARCHAR(32) NOT NULL    COMMENT '字典ID',
  LABEL VARCHAR(150) NOT NULL    COMMENT '显示名',
  VALUE VARCHAR(150) NOT NULL    COMMENT '值',
  CODE_VALUE VARCHAR(100) NULL    COMMENT '内部编码',
  GROUP_NAME VARCHAR(50) NULL    COMMENT '分组名',
  IS_INTERNAL TINYINT default 0  NOT NULL    COMMENT '是否内部',
  IS_DEPRECATED TINYINT default 0  NOT NULL    COMMENT '是否已废弃',
  DEL_FLAG TINYINT NOT NULL    COMMENT '删除标识',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  REMARK VARCHAR(200) NULL    COMMENT '备注',
  constraint PK_nop_sys_dict_option primary key (SID)
);

CREATE TABLE nop_sys_obj_tag(
  BIZ_OBJ_ID VARCHAR(50) NOT NULL    COMMENT '对象ID',
  BIZ_OBJ_NAME VARCHAR(100) NOT NULL    COMMENT '对象名',
  TAG_ID BIGINT NOT NULL    COMMENT '标签ID',
  VERSION INTEGER NOT NULL    COMMENT '数据版本',
  CREATED_BY VARCHAR(50) NOT NULL    COMMENT '创建人',
  CREATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '创建时间',
  UPDATED_BY VARCHAR(50) NOT NULL    COMMENT '修改人',
  UPDATE_TIME DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)  NOT NULL    COMMENT '修改时间',
  constraint PK_nop_sys_obj_tag primary key (BIZ_OBJ_ID,BIZ_OBJ_NAME,TAG_ID)
);


   ALTER TABLE nop_sys_sequence COMMENT '序列号';
                
   ALTER TABLE nop_sys_dict COMMENT '字典表';
                
   ALTER TABLE nop_sys_i18n COMMENT '多语言消息';
                
   ALTER TABLE nop_sys_checker_record COMMENT 'MakerChecker审批记录';
                
   ALTER TABLE nop_sys_code_rule COMMENT '编码规则';
                
   ALTER TABLE nop_sys_notice_template COMMENT '通知模板';
                
   ALTER TABLE nop_sys_user_variable COMMENT '用户变量';
                
   ALTER TABLE nop_sys_variable COMMENT '系统变量';
                
   ALTER TABLE nop_sys_ext_field COMMENT '扩展字段';
                
   ALTER TABLE nop_sys_lock COMMENT '资源锁';
                
   ALTER TABLE nop_sys_cluster_leader COMMENT '集群选举';
                
   ALTER TABLE nop_sys_event COMMENT '事件队列';
                
   ALTER TABLE nop_sys_service_instance COMMENT '服务实例';
                
   ALTER TABLE nop_sys_change_log COMMENT '变更跟踪日志';
                
   ALTER TABLE nop_sys_tag COMMENT '标签';
                
   ALTER TABLE nop_sys_dict_option COMMENT '字典明细';
                
   ALTER TABLE nop_sys_obj_tag COMMENT '对象标签';
                
