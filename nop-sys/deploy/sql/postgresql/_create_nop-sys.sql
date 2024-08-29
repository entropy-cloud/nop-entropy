
CREATE TABLE nop_sys_sequence(
  SEQ_NAME VARCHAR(150) NOT NULL ,
  SEQ_TYPE VARCHAR(10)  ,
  IS_UUID INT4 NOT NULL  default '0' ,
  NEXT_VALUE INT8 NOT NULL ,
  STEP_SIZE INT4 NOT NULL ,
  CACHE_SIZE INT4  ,
  MAX_VALUE INT8  ,
  RESET_TYPE INT4  ,
  DEL_FLAG INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_sequence primary key (SEQ_NAME)
);

CREATE TABLE nop_sys_dict(
  SID VARCHAR(32) NOT NULL ,
  DICT_NAME VARCHAR(150) NOT NULL ,
  DISPLAY_NAME VARCHAR(50) NOT NULL ,
  DEL_FLAG INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_dict primary key (SID)
);

CREATE TABLE nop_sys_i18n(
  I18N_KEY VARCHAR(200) NOT NULL ,
  I18N_LOCALE VARCHAR(20) NOT NULL ,
  VALUE VARCHAR(300) NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_i18n primary key (I18N_KEY,I18N_LOCALE)
);

CREATE TABLE nop_sys_checker_record(
  SID VARCHAR(32) NOT NULL ,
  BIZ_OBJ_NAME VARCHAR(200) NOT NULL ,
  BIZ_OBJ_ID VARCHAR(100)  ,
  MAKER_ID VARCHAR(50) NOT NULL ,
  MAKER_NAME VARCHAR(150) NOT NULL ,
  REQUEST_ACTION VARCHAR(100) NOT NULL ,
  REQUEST_DATA TEXT  ,
  REQUEST_TIME TIMESTAMP NOT NULL ,
  CHECKER_ID VARCHAR(50)  ,
  CHECKER_NAME VARCHAR(150)  ,
  CHECK_TIME TIMESTAMP  ,
  TRY_RESULT TEXT  ,
  INPUT_PAGE VARCHAR(1000)  ,
  STATUS INT4 NOT NULL ,
  CANCEL_ACTION VARCHAR(200)  ,
  CB_ERR_CODE VARCHAR(150)  ,
  CE_ERR_MSG VARCHAR(1000)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_checker_record primary key (SID)
);

CREATE TABLE nop_sys_code_rule(
  SID VARCHAR(32) NOT NULL ,
  NAME VARCHAR(100) NOT NULL ,
  DISPLAY_NAME VARCHAR(100) NOT NULL ,
  CODE_PATTERN VARCHAR(200) NOT NULL ,
  SEQ_NAME VARCHAR(100)  ,
  DEL_FLAG INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_code_rule primary key (SID)
);

CREATE TABLE nop_sys_notice_template(
  SID VARCHAR(32) NOT NULL ,
  NAME VARCHAR(100) NOT NULL ,
  TPL_TYPE VARCHAR(10) NOT NULL ,
  CONTENT VARCHAR(4000) NOT NULL ,
  DEL_FLAG INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_notice_template primary key (SID)
);

CREATE TABLE nop_sys_user_variable(
  USER_ID VARCHAR(32) NOT NULL ,
  VAR_NAME VARCHAR(32) NOT NULL ,
  VAR_VALUE VARCHAR(4000)  ,
  STD_DOMAIN VARCHAR(100)  ,
  VAR_TYPE VARCHAR(100)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_user_variable primary key (USER_ID,VAR_NAME)
);

CREATE TABLE nop_sys_variable(
  VAR_NAME VARCHAR(32) NOT NULL ,
  VAR_VALUE VARCHAR(4000)  ,
  STD_DOMAIN VARCHAR(100)  ,
  VAR_TYPE VARCHAR(100)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_variable primary key (VAR_NAME)
);

CREATE TABLE nop_sys_ext_field(
  ENTITY_NAME VARCHAR(200) NOT NULL ,
  ENTITY_ID VARCHAR(100) NOT NULL ,
  FIELD_NAME VARCHAR(100) NOT NULL ,
  FIELD_TYPE INT4 NOT NULL ,
  DECIMAL_SCALE INT4  ,
  DECIMAL_VALUE NUMERIC(24,8)  ,
  DATE_VALUE DATE  ,
  TIMESTAMP_VALUE TIMESTAMP  ,
  STRING_VALUE VARCHAR(4000)  ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_sys_ext_field primary key (ENTITY_NAME,ENTITY_ID,FIELD_NAME)
);

CREATE TABLE nop_sys_lock(
  LOCK_GROUP VARCHAR(200) NOT NULL ,
  LOCK_NAME VARCHAR(200) NOT NULL ,
  LOCK_TIME TIMESTAMP NOT NULL ,
  EXPIRE_AT TIMESTAMP NOT NULL ,
  LOCK_REASON VARCHAR(200)  ,
  HOLDER_ID VARCHAR(100) NOT NULL ,
  HOLDER_ADDER VARCHAR(100) NOT NULL ,
  APP_ID VARCHAR(100) NOT NULL ,
  constraint PK_nop_sys_lock primary key (LOCK_GROUP,LOCK_NAME)
);

CREATE TABLE nop_sys_cluster_leader(
  CLUSTER_ID VARCHAR(200) NOT NULL ,
  LEADER_ID VARCHAR(100) NOT NULL ,
  LEADER_ADDER VARCHAR(100) NOT NULL ,
  LEADER_EPOCH INT8 NOT NULL ,
  ELECT_TIME TIMESTAMP NOT NULL ,
  EXPIRE_AT TIMESTAMP NOT NULL ,
  APP_ID VARCHAR(100) NOT NULL ,
  constraint PK_nop_sys_cluster_leader primary key (CLUSTER_ID)
);

CREATE TABLE nop_sys_dict_option(
  SID VARCHAR(32) NOT NULL ,
  DICT_ID VARCHAR(32) NOT NULL ,
  LABEL VARCHAR(150) NOT NULL ,
  VALUE VARCHAR(150) NOT NULL ,
  CODE_VALUE VARCHAR(100)  ,
  GROUP_NAME VARCHAR(50)  ,
  IS_INTERNAL INT4 NOT NULL  default '0' ,
  IS_DEPRECATED INT4 NOT NULL  default '0' ,
  DEL_FLAG INT4 NOT NULL ,
  VERSION INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
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
                
      COMMENT ON COLUMN nop_sys_lock.LOCK_GROUP IS '分组';
                    
      COMMENT ON COLUMN nop_sys_lock.LOCK_NAME IS '锁名称';
                    
      COMMENT ON COLUMN nop_sys_lock.LOCK_TIME IS '锁定时间';
                    
      COMMENT ON COLUMN nop_sys_lock.EXPIRE_AT IS '过期时间';
                    
      COMMENT ON COLUMN nop_sys_lock.LOCK_REASON IS '锁定原因';
                    
      COMMENT ON COLUMN nop_sys_lock.HOLDER_ID IS '锁的持有者';
                    
      COMMENT ON COLUMN nop_sys_lock.HOLDER_ADDER IS '持有者地址';
                    
      COMMENT ON COLUMN nop_sys_lock.APP_ID IS '应用ID';
                    
      COMMENT ON TABLE nop_sys_cluster_leader IS '集群选举';
                
      COMMENT ON COLUMN nop_sys_cluster_leader.CLUSTER_ID IS '集群ID';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.LEADER_ID IS '主服务器ID';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.LEADER_ADDER IS '主服务器地址';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.LEADER_EPOCH IS '选举世代';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.ELECT_TIME IS '选举时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.EXPIRE_AT IS '过期时间';
                    
      COMMENT ON COLUMN nop_sys_cluster_leader.APP_ID IS '应用ID';
                    
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
                    
