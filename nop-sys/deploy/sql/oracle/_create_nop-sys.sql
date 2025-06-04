
CREATE TABLE nop_sys_sequence(
  SEQ_NAME VARCHAR2(150) NOT NULL ,
  SEQ_TYPE VARCHAR2(10)  ,
  IS_UUID SMALLINT default 0  NOT NULL ,
  NEXT_VALUE NUMBER(20) NOT NULL ,
  STEP_SIZE INTEGER NOT NULL ,
  CACHE_SIZE INTEGER  ,
  MAX_VALUE NUMBER(20)  ,
  RESET_TYPE INTEGER  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_sequence primary key (SEQ_NAME)
);

CREATE TABLE nop_sys_dict(
  SID VARCHAR2(32) NOT NULL ,
  DICT_NAME VARCHAR2(150) NOT NULL ,
  DISPLAY_NAME VARCHAR2(50) NOT NULL ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_dict primary key (SID)
);

CREATE TABLE nop_sys_i18n(
  I18N_KEY VARCHAR2(200) NOT NULL ,
  I18N_LOCALE VARCHAR2(20) NOT NULL ,
  VALUE VARCHAR2(300) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_i18n primary key (I18N_KEY,I18N_LOCALE)
);

CREATE TABLE nop_sys_checker_record(
  SID VARCHAR2(32) NOT NULL ,
  BIZ_OBJ_NAME VARCHAR2(200) NOT NULL ,
  BIZ_OBJ_ID VARCHAR2(100)  ,
  MAKER_ID VARCHAR2(50) NOT NULL ,
  MAKER_NAME VARCHAR2(150) NOT NULL ,
  REQUEST_ACTION VARCHAR2(100) NOT NULL ,
  REQUEST_DATA CLOB  ,
  REQUEST_TIME DATE NOT NULL ,
  CHECKER_ID VARCHAR2(50)  ,
  CHECKER_NAME VARCHAR2(150)  ,
  CHECK_TIME DATE  ,
  TRY_RESULT CLOB  ,
  INPUT_PAGE VARCHAR2(1000)  ,
  STATUS INTEGER NOT NULL ,
  CANCEL_ACTION VARCHAR2(200)  ,
  CB_ERR_CODE VARCHAR2(150)  ,
  CE_ERR_MSG VARCHAR2(1000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_checker_record primary key (SID)
);

CREATE TABLE nop_sys_code_rule(
  SID VARCHAR2(32) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  CODE_PATTERN VARCHAR2(200) NOT NULL ,
  SEQ_NAME VARCHAR2(100)  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_code_rule primary key (SID)
);

CREATE TABLE nop_sys_notice_template(
  SID VARCHAR2(32) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  TPL_TYPE VARCHAR2(10) NOT NULL ,
  CONTENT VARCHAR2(4000) NOT NULL ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_notice_template primary key (SID)
);

CREATE TABLE nop_sys_user_variable(
  USER_ID VARCHAR2(32) NOT NULL ,
  VAR_NAME VARCHAR2(32) NOT NULL ,
  VAR_VALUE VARCHAR2(4000)  ,
  STD_DOMAIN VARCHAR2(100)  ,
  VAR_TYPE VARCHAR2(100)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_user_variable primary key (USER_ID,VAR_NAME)
);

CREATE TABLE nop_sys_variable(
  VAR_NAME VARCHAR2(32) NOT NULL ,
  VAR_VALUE VARCHAR2(4000)  ,
  STD_DOMAIN VARCHAR2(100)  ,
  VAR_TYPE VARCHAR2(100)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_variable primary key (VAR_NAME)
);

CREATE TABLE nop_sys_ext_field(
  ENTITY_NAME VARCHAR2(200) NOT NULL ,
  ENTITY_ID VARCHAR2(100) NOT NULL ,
  FIELD_NAME VARCHAR2(100) NOT NULL ,
  FIELD_TYPE INTEGER NOT NULL ,
  DECIMAL_SCALE SMALLINT  ,
  DECIMAL_VALUE NUMBER(24,8)  ,
  DATE_VALUE DATE  ,
  TIMESTAMP_VALUE TIMESTAMP  ,
  STRING_VALUE VARCHAR2(4000)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_ext_field primary key (ENTITY_NAME,ENTITY_ID,FIELD_NAME)
);

CREATE TABLE nop_sys_lock(
  LOCK_NAME VARCHAR2(200) NOT NULL ,
  LOCK_GROUP VARCHAR2(200) NOT NULL ,
  LOCK_TIME TIMESTAMP NOT NULL ,
  EXPIRE_AT TIMESTAMP NOT NULL ,
  LOCK_REASON VARCHAR2(200)  ,
  HOLDER_ID VARCHAR2(100) NOT NULL ,
  HOLDER_ADDER VARCHAR2(100) NOT NULL ,
  APP_ID VARCHAR2(100) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_lock primary key (LOCK_NAME,LOCK_GROUP)
);

CREATE TABLE nop_sys_cluster_leader(
  CLUSTER_ID VARCHAR2(200) NOT NULL ,
  LEADER_ID VARCHAR2(100) NOT NULL ,
  LEADER_ADDER VARCHAR2(100) NOT NULL ,
  LEADER_EPOCH NUMBER(20) NOT NULL ,
  ELECT_TIME TIMESTAMP NOT NULL ,
  EXPIRE_AT TIMESTAMP NOT NULL ,
  REFRESH_TIME TIMESTAMP NOT NULL ,
  VERSION INTEGER NOT NULL ,
  APP_NAME VARCHAR2(100) NOT NULL ,
  constraint PK_nop_sys_cluster_leader primary key (CLUSTER_ID)
);

CREATE TABLE nop_sys_event(
  EVENT_ID NUMBER(20) NOT NULL ,
  EVENT_TOPIC VARCHAR2(100) NOT NULL ,
  EVENT_NAME VARCHAR2(100) NOT NULL ,
  EVENT_HEADERS JSON NOT NULL ,
  EVENT_DATA JSON NOT NULL ,
  SELECTION VARCHAR2(1000)  ,
  EVENT_TIME TIMESTAMP NOT NULL ,
  EVENT_STATUS INTEGER NOT NULL ,
  PROCESS_TIME TIMESTAMP NOT NULL ,
  SCHEDULE_TIME TIMESTAMP NOT NULL ,
  IS_BROADCAST CHAR(1) NOT NULL ,
  BIZ_OBJ_NAME VARCHAR2(100)  ,
  BIZ_KEY VARCHAR2(50)  ,
  BIZ_DATE DATE NOT NULL ,
  PARTITION_INDEX INTEGER NOT NULL ,
  RETRY_TIMES INTEGER NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_event primary key (EVENT_ID)
);

CREATE TABLE nop_sys_service_instance(
  INSTANCE_ID VARCHAR2(50) NOT NULL ,
  SERVICE_NAME VARCHAR2(100) NOT NULL ,
  CLUSTER_NAME VARCHAR2(100) NOT NULL ,
  GROUP_NAME VARCHAR2(100) NOT NULL ,
  TAGS_TEXT VARCHAR2(100) NOT NULL ,
  SERVER_ADDR VARCHAR2(20) NOT NULL ,
  SERVER_PORT INTEGER NOT NULL ,
  WEIGHT INTEGER NOT NULL ,
  META_DATA VARCHAR2(1000)  ,
  IS_HEALTHY CHAR(1) NOT NULL ,
  IS_ENABLED CHAR(1) NOT NULL ,
  IS_EPHEMERAL CHAR(1) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_sys_service_instance primary key (INSTANCE_ID)
);

CREATE TABLE nop_sys_change_log(
  SID VARCHAR2(32) NOT NULL ,
  BIZ_OBJ_NAME VARCHAR2(100) NOT NULL ,
  OBJ_ID VARCHAR2(100) NOT NULL ,
  BIZ_KEY VARCHAR2(100)  ,
  OPERATION_NAME VARCHAR2(150) NOT NULL ,
  PROP_NAME VARCHAR2(100) NOT NULL ,
  OLD_VALUE VARCHAR2(4000)  ,
  NEW_VALUE VARCHAR2(4000)  ,
  CHANGE_TIME TIMESTAMP NOT NULL ,
  APP_ID VARCHAR2(100)  ,
  OPERATOR_ID VARCHAR2(50) NOT NULL ,
  APPROVER_ID VARCHAR2(50)  ,
  constraint PK_nop_sys_change_log primary key (SID)
);

CREATE TABLE nop_sys_dict_option(
  SID VARCHAR2(32) NOT NULL ,
  DICT_ID VARCHAR2(32) NOT NULL ,
  LABEL VARCHAR2(150) NOT NULL ,
  VALUE VARCHAR2(150) NOT NULL ,
  CODE_VALUE VARCHAR2(100)  ,
  GROUP_NAME VARCHAR2(50)  ,
  IS_INTERNAL SMALLINT default 0  NOT NULL ,
  IS_DEPRECATED SMALLINT default 0  NOT NULL ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_sys_dict_option primary key (SID)
);


      COMMENT ON TABLE nop_sys_sequence IS '序列号';
                
      COMMENT ON COLUMN nop_sys_sequence.SEQ_NAME IS '名称';
                    
      COMMENT ON COLUMN nop_sys_sequence.SEQ_TYPE IS '类型';
                    
      COMMENT ON COLUMN nop_sys_sequence.IS_UUID IS '是否UUID';
                    
      COMMENT ON COLUMN nop_sys_sequence.NEXT_VALUE IS '下一个值';
                    
      COMMENT ON COLUMN nop_sys_sequence.STEP_SIZE IS '步长';
                    
      COMMENT ON COLUMN nop_sys_sequence.CACHE_SIZE IS '缓存个数';
                    
      COMMENT ON COLUMN nop_sys_sequence.MAX_VALUE IS '最大值';
                    
      COMMENT ON COLUMN nop_sys_sequence.RESET_TYPE IS '重置方式';
                    
      COMMENT ON COLUMN nop_sys_sequence.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_sequence.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_sequence.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_sequence.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_sequence.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_sequence.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_sequence.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_dict IS '字典表';
                
      COMMENT ON COLUMN nop_sys_dict.SID IS '主键';
                    
      COMMENT ON COLUMN nop_sys_dict.DICT_NAME IS '字典名';
                    
      COMMENT ON COLUMN nop_sys_dict.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_sys_dict.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_dict.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_dict.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_dict.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_dict.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_dict.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_dict.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_i18n IS '多语言消息';
                
      COMMENT ON COLUMN nop_sys_i18n.I18N_KEY IS '字符串Key';
                    
      COMMENT ON COLUMN nop_sys_i18n.I18N_LOCALE IS '语言';
                    
      COMMENT ON COLUMN nop_sys_i18n.VALUE IS '值';
                    
      COMMENT ON COLUMN nop_sys_i18n.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_i18n.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_i18n.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_i18n.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_i18n.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_i18n.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_checker_record IS 'MakerChecker审批记录';
                
      COMMENT ON COLUMN nop_sys_checker_record.SID IS '主键';
                    
      COMMENT ON COLUMN nop_sys_checker_record.BIZ_OBJ_NAME IS '业务对象名';
                    
      COMMENT ON COLUMN nop_sys_checker_record.BIZ_OBJ_ID IS '业务对象ID';
                    
      COMMENT ON COLUMN nop_sys_checker_record.MAKER_ID IS '请求发起人ID';
                    
      COMMENT ON COLUMN nop_sys_checker_record.MAKER_NAME IS '请求发起人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.REQUEST_ACTION IS '请求操作';
                    
      COMMENT ON COLUMN nop_sys_checker_record.REQUEST_DATA IS '请求数据';
                    
      COMMENT ON COLUMN nop_sys_checker_record.REQUEST_TIME IS '请求时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CHECKER_ID IS '审批人ID';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CHECKER_NAME IS '审批人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CHECK_TIME IS '审批时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.TRY_RESULT IS '请求结果';
                    
      COMMENT ON COLUMN nop_sys_checker_record.INPUT_PAGE IS '输入页面';
                    
      COMMENT ON COLUMN nop_sys_checker_record.STATUS IS '审批状态';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CANCEL_ACTION IS '取消方法';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CB_ERR_CODE IS '回调错误码';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CE_ERR_MSG IS '回调错误消息';
                    
      COMMENT ON COLUMN nop_sys_checker_record.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_checker_record.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_checker_record.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_code_rule IS '编码规则';
                
      COMMENT ON COLUMN nop_sys_code_rule.SID IS '主键';
                    
      COMMENT ON COLUMN nop_sys_code_rule.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_sys_code_rule.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_sys_code_rule.CODE_PATTERN IS '编码模式';
                    
      COMMENT ON COLUMN nop_sys_code_rule.SEQ_NAME IS '序列号名称';
                    
      COMMENT ON COLUMN nop_sys_code_rule.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_code_rule.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_code_rule.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_code_rule.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_code_rule.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_code_rule.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_code_rule.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_notice_template IS '通知模板';
                
      COMMENT ON COLUMN nop_sys_notice_template.SID IS '主键';
                    
      COMMENT ON COLUMN nop_sys_notice_template.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_sys_notice_template.TPL_TYPE IS '模板类型';
                    
      COMMENT ON COLUMN nop_sys_notice_template.CONTENT IS '模板内容';
                    
      COMMENT ON COLUMN nop_sys_notice_template.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_notice_template.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_notice_template.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_notice_template.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_notice_template.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_notice_template.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_notice_template.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_user_variable IS '用户变量';
                
      COMMENT ON COLUMN nop_sys_user_variable.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_sys_user_variable.VAR_NAME IS '变量名';
                    
      COMMENT ON COLUMN nop_sys_user_variable.VAR_VALUE IS '变量值';
                    
      COMMENT ON COLUMN nop_sys_user_variable.STD_DOMAIN IS '变量域';
                    
      COMMENT ON COLUMN nop_sys_user_variable.VAR_TYPE IS '变量类型';
                    
      COMMENT ON COLUMN nop_sys_user_variable.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_user_variable.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_user_variable.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_user_variable.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_user_variable.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_user_variable.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_variable IS '系统变量';
                
      COMMENT ON COLUMN nop_sys_variable.VAR_NAME IS '变量名';
                    
      COMMENT ON COLUMN nop_sys_variable.VAR_VALUE IS '变量值';
                    
      COMMENT ON COLUMN nop_sys_variable.STD_DOMAIN IS '变量域';
                    
      COMMENT ON COLUMN nop_sys_variable.VAR_TYPE IS '变量类型';
                    
      COMMENT ON COLUMN nop_sys_variable.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_variable.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_variable.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_variable.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_variable.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_variable.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_ext_field IS '扩展字段';
                
      COMMENT ON COLUMN nop_sys_ext_field.ENTITY_NAME IS '实体名';
                    
      COMMENT ON COLUMN nop_sys_ext_field.ENTITY_ID IS '实体ID';
                    
      COMMENT ON COLUMN nop_sys_ext_field.FIELD_NAME IS '字段名';
                    
      COMMENT ON COLUMN nop_sys_ext_field.FIELD_TYPE IS '字段类型';
                    
      COMMENT ON COLUMN nop_sys_ext_field.DECIMAL_SCALE IS '浮点精度';
                    
      COMMENT ON COLUMN nop_sys_ext_field.DECIMAL_VALUE IS '浮点值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.DATE_VALUE IS '日期值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.TIMESTAMP_VALUE IS '时间点值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.STRING_VALUE IS '字符串值';
                    
      COMMENT ON COLUMN nop_sys_ext_field.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_ext_field.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_ext_field.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_ext_field.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_ext_field.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_ext_field.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_sys_lock IS '资源锁';
                
      COMMENT ON COLUMN nop_sys_lock.LOCK_NAME IS '锁名称';
                    
      COMMENT ON COLUMN nop_sys_lock.LOCK_GROUP IS '分组';
                    
      COMMENT ON COLUMN nop_sys_lock.LOCK_TIME IS '锁定时间';
                    
      COMMENT ON COLUMN nop_sys_lock.EXPIRE_AT IS '过期时间';
                    
      COMMENT ON COLUMN nop_sys_lock.LOCK_REASON IS '锁定原因';
                    
      COMMENT ON COLUMN nop_sys_lock.HOLDER_ID IS '锁的持有者';
                    
      COMMENT ON COLUMN nop_sys_lock.HOLDER_ADDER IS '持有者地址';
                    
      COMMENT ON COLUMN nop_sys_lock.APP_ID IS '应用ID';
                    
      COMMENT ON COLUMN nop_sys_lock.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_lock.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_lock.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_lock.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_lock.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_cluster_leader IS '集群选举';
                
      COMMENT ON COLUMN nop_sys_cluster_leader.CLUSTER_ID IS '集群ID';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.LEADER_ID IS '主服务器ID';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.LEADER_ADDER IS '主服务器地址';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.LEADER_EPOCH IS '选举世代';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.ELECT_TIME IS '选举时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.EXPIRE_AT IS '过期时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.REFRESH_TIME IS '刷新时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.VERSION IS '修改版本';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.APP_NAME IS '应用名';
                    
      COMMENT ON TABLE nop_sys_event IS '事件队列';
                
      COMMENT ON COLUMN nop_sys_event.EVENT_ID IS '事件ID';
                    
      COMMENT ON COLUMN nop_sys_event.EVENT_TOPIC IS '事件主题';
                    
      COMMENT ON COLUMN nop_sys_event.EVENT_NAME IS '事件名称';
                    
      COMMENT ON COLUMN nop_sys_event.EVENT_HEADERS IS '事件元数据';
                    
      COMMENT ON COLUMN nop_sys_event.EVENT_DATA IS '数据';
                    
      COMMENT ON COLUMN nop_sys_event.SELECTION IS '字段选择';
                    
      COMMENT ON COLUMN nop_sys_event.EVENT_TIME IS '事件时间';
                    
      COMMENT ON COLUMN nop_sys_event.EVENT_STATUS IS '事件状态';
                    
      COMMENT ON COLUMN nop_sys_event.PROCESS_TIME IS '处理时间';
                    
      COMMENT ON COLUMN nop_sys_event.SCHEDULE_TIME IS '调度时间';
                    
      COMMENT ON COLUMN nop_sys_event.IS_BROADCAST IS '是否广播';
                    
      COMMENT ON COLUMN nop_sys_event.BIZ_OBJ_NAME IS '业务对象名';
                    
      COMMENT ON COLUMN nop_sys_event.BIZ_KEY IS '业务标识';
                    
      COMMENT ON COLUMN nop_sys_event.BIZ_DATE IS '业务日期';
                    
      COMMENT ON COLUMN nop_sys_event.PARTITION_INDEX IS '数据分区';
                    
      COMMENT ON COLUMN nop_sys_event.RETRY_TIMES IS '重试次数';
                    
      COMMENT ON COLUMN nop_sys_event.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_event.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_event.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_event.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_event.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_service_instance IS '服务实例';
                
      COMMENT ON COLUMN nop_sys_service_instance.INSTANCE_ID IS '服务实例ID';
                    
      COMMENT ON COLUMN nop_sys_service_instance.SERVICE_NAME IS '服务名';
                    
      COMMENT ON COLUMN nop_sys_service_instance.CLUSTER_NAME IS '集群名';
                    
      COMMENT ON COLUMN nop_sys_service_instance.GROUP_NAME IS '分组名';
                    
      COMMENT ON COLUMN nop_sys_service_instance.TAGS_TEXT IS '标签';
                    
      COMMENT ON COLUMN nop_sys_service_instance.SERVER_ADDR IS '服务地址';
                    
      COMMENT ON COLUMN nop_sys_service_instance.SERVER_PORT IS '服务端口';
                    
      COMMENT ON COLUMN nop_sys_service_instance.WEIGHT IS '权重';
                    
      COMMENT ON COLUMN nop_sys_service_instance.META_DATA IS '扩展数据';
                    
      COMMENT ON COLUMN nop_sys_service_instance.IS_HEALTHY IS '是否健康';
                    
      COMMENT ON COLUMN nop_sys_service_instance.IS_ENABLED IS '是否启用';
                    
      COMMENT ON COLUMN nop_sys_service_instance.IS_EPHEMERAL IS '是否临时';
                    
      COMMENT ON COLUMN nop_sys_service_instance.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_service_instance.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_service_instance.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON TABLE nop_sys_change_log IS '变更跟踪日志';
                
      COMMENT ON COLUMN nop_sys_change_log.SID IS '主键';
                    
      COMMENT ON COLUMN nop_sys_change_log.BIZ_OBJ_NAME IS '业务对象';
                    
      COMMENT ON COLUMN nop_sys_change_log.OBJ_ID IS '对象ID';
                    
      COMMENT ON COLUMN nop_sys_change_log.BIZ_KEY IS '业务键';
                    
      COMMENT ON COLUMN nop_sys_change_log.OPERATION_NAME IS '业务操作';
                    
      COMMENT ON COLUMN nop_sys_change_log.PROP_NAME IS '属性名';
                    
      COMMENT ON COLUMN nop_sys_change_log.OLD_VALUE IS '旧值';
                    
      COMMENT ON COLUMN nop_sys_change_log.NEW_VALUE IS '新值';
                    
      COMMENT ON COLUMN nop_sys_change_log.CHANGE_TIME IS '变更时间';
                    
      COMMENT ON COLUMN nop_sys_change_log.APP_ID IS '应用ID';
                    
      COMMENT ON COLUMN nop_sys_change_log.OPERATOR_ID IS '操作人';
                    
      COMMENT ON COLUMN nop_sys_change_log.APPROVER_ID IS '审核人';
                    
      COMMENT ON TABLE nop_sys_dict_option IS '字典明细';
                
      COMMENT ON COLUMN nop_sys_dict_option.SID IS '主键';
                    
      COMMENT ON COLUMN nop_sys_dict_option.DICT_ID IS '字典ID';
                    
      COMMENT ON COLUMN nop_sys_dict_option.LABEL IS '显示名';
                    
      COMMENT ON COLUMN nop_sys_dict_option.VALUE IS '值';
                    
      COMMENT ON COLUMN nop_sys_dict_option.CODE_VALUE IS '内部编码';
                    
      COMMENT ON COLUMN nop_sys_dict_option.GROUP_NAME IS '分组名';
                    
      COMMENT ON COLUMN nop_sys_dict_option.IS_INTERNAL IS '是否内部';
                    
      COMMENT ON COLUMN nop_sys_dict_option.IS_DEPRECATED IS '是否已废弃';
                    
      COMMENT ON COLUMN nop_sys_dict_option.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_sys_dict_option.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_sys_dict_option.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_sys_dict_option.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_sys_dict_option.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_sys_dict_option.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_sys_dict_option.REMARK IS '备注';
                    
