
CREATE TABLE nop_auth_dept(
  DEPT_ID VARCHAR2(50) NOT NULL ,
  DEPT_NAME VARCHAR2(100) NOT NULL ,
  PARENT_ID VARCHAR2(50)  ,
  ORDER_NUM INTEGER  ,
  DEPT_TYPE VARCHAR2(10)  ,
  MANAGER_ID VARCHAR2(50)  ,
  EMAIL VARCHAR2(100)  ,
  PHONE VARCHAR2(50)  ,
  DEL_FLAG SMALLINT  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_dept primary key (DEPT_ID)
);

CREATE TABLE nop_auth_position(
  POSITION_ID VARCHAR2(50) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  DEL_FLAG SMALLINT  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_position primary key (POSITION_ID)
);

CREATE TABLE nop_auth_role(
  ROLE_ID VARCHAR2(50) NOT NULL ,
  ROLE_NAME VARCHAR2(50) NOT NULL ,
  CHILD_ROLE_IDS VARCHAR2(500)  ,
  IS_PRIMARY SMALLINT  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint UK_NOP_AUTH_ROLE_NAME unique (ROLE_NAME),
  constraint PK_nop_auth_role primary key (ROLE_ID)
);

CREATE TABLE nop_auth_site(
  SITE_ID VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  ORDER_NO INTEGER NOT NULL ,
  URL VARCHAR2(200)  ,
  STATUS INTEGER NOT NULL ,
  EXT_CONFIG VARCHAR2(1000)  ,
  CONFIG_VERSION VARCHAR2(20)  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_site primary key (SITE_ID)
);

CREATE TABLE nop_auth_tenant(
  TENANT_ID VARCHAR2(32) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  BEGIN_TIME DATE  ,
  END_TIME DATE  ,
  STATUS INTEGER NOT NULL ,
  DEL_FLAG SMALLINT  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_tenant primary key (TENANT_ID)
);

CREATE TABLE nop_auth_user(
  USER_ID VARCHAR2(50) NOT NULL ,
  USER_NAME VARCHAR2(50) NOT NULL ,
  PASSWORD VARCHAR2(80) NOT NULL ,
  SALT VARCHAR2(32)  ,
  NICK_NAME VARCHAR2(50) NOT NULL ,
  DEPT_ID VARCHAR2(50)  ,
  OPEN_ID VARCHAR2(32) NOT NULL ,
  REL_DEPT_ID VARCHAR2(50)  ,
  GENDER INTEGER NOT NULL ,
  AVATAR VARCHAR2(100)  ,
  EMAIL VARCHAR2(100)  ,
  EMAIL_VERIFIED SMALLINT  ,
  PHONE VARCHAR2(50)  ,
  PHONE_VERIFIED SMALLINT  ,
  BIRTHDAY DATE  ,
  USER_TYPE INTEGER NOT NULL ,
  STATUS INTEGER NOT NULL ,
  ID_TYPE VARCHAR2(10)  ,
  ID_NBR VARCHAR2(100)  ,
  EXPIRE_AT DATE  ,
  PWD_UPDATE_TIME DATE  ,
  CHANGE_PWD_AT_LOGIN SMALLINT  ,
  REAL_NAME VARCHAR2(50)  ,
  MANAGER_ID VARCHAR2(50)  ,
  WORK_NO VARCHAR2(100)  ,
  POSITION_ID VARCHAR2(32)  ,
  TELEPHONE VARCHAR2(50)  ,
  CLIENT_ID VARCHAR2(100)  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  TENANT_ID VARCHAR2(32) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint UK_NOP_AUTH_USER_NAME unique (USER_NAME),
  constraint PK_nop_auth_user primary key (USER_ID)
);

CREATE TABLE nop_auth_role_data_auth(
  SID VARCHAR2(32) NOT NULL ,
  ROLE_ID VARCHAR2(50) NOT NULL ,
  BIZ_OBJ VARCHAR2(100)  ,
  PRIORITY INTEGER NOT NULL ,
  FILTER_CONFIG VARCHAR2(4000) NOT NULL ,
  DESCRIPTION VARCHAR2(4000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_role_data_auth primary key (SID)
);

CREATE TABLE nop_auth_resource(
  RESOURCE_ID VARCHAR2(100) NOT NULL ,
  SITE_ID VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(100) NOT NULL ,
  ORDER_NO INTEGER NOT NULL ,
  RESOURCE_TYPE VARCHAR2(4) NOT NULL ,
  PARENT_ID VARCHAR2(100)  ,
  ICON VARCHAR2(150)  ,
  ROUTE_PATH VARCHAR2(200)  ,
  URL VARCHAR2(1000)  ,
  COMPONENT VARCHAR2(200)  ,
  TARGET VARCHAR2(50)  ,
  HIDDEN SMALLINT NOT NULL ,
  KEEP_ALIVE SMALLINT NOT NULL ,
  PERMISSIONS VARCHAR2(200)  ,
  NO_AUTH SMALLINT NOT NULL ,
  DEPENDS VARCHAR2(1000)  ,
  IS_LEAF SMALLINT NOT NULL ,
  STATUS INTEGER NOT NULL ,
  Meta_CONFIG VARCHAR2(1000)  ,
  PROPS_CONFIG VARCHAR2(1000)  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_resource primary key (RESOURCE_ID)
);

CREATE TABLE nop_auth_ext_login(
  SID VARCHAR2(32) NOT NULL ,
  USER_ID VARCHAR2(50) NOT NULL ,
  LOGIN_TYPE INTEGER NOT NULL ,
  EXT_ID VARCHAR2(50) NOT NULL ,
  CREDENTIAL VARCHAR2(50)  ,
  VERIFIED CHAR(1) NOT NULL ,
  LAST_LOGIN_TIME TIMESTAMP  ,
  LAST_LOGIN_IP VARCHAR2(20)  ,
  DEL_FLAG SMALLINT NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_ext_login primary key (SID)
);

CREATE TABLE nop_auth_user_role(
  USER_ID VARCHAR2(32) NOT NULL ,
  ROLE_ID VARCHAR2(50) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_user_role primary key (USER_ID,ROLE_ID)
);

CREATE TABLE nop_auth_user_substitution(
  SID VARCHAR2(32) NOT NULL ,
  USER_ID VARCHAR2(32) NOT NULL ,
  SUBSTITUTED_USER_ID VARCHAR2(32) NOT NULL ,
  WORK_SCOPE VARCHAR2(50) NOT NULL ,
  START_TIME DATE  ,
  END_TIME DATE  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_user_substitution primary key (SID)
);

CREATE TABLE nop_auth_session(
  SESSION_ID VARCHAR2(100) NOT NULL ,
  USER_ID VARCHAR2(32) NOT NULL ,
  USER_NAME VARCHAR2(100) NOT NULL ,
  TENANT_ID VARCHAR2(32) NOT NULL ,
  LOGIN_ADDR VARCHAR2(100)  ,
  LOGIN_DEVICE VARCHAR2(100)  ,
  LOGIN_APP VARCHAR2(100)  ,
  LOGIN_OS VARCHAR2(100)  ,
  LOGIN_TIME TIMESTAMP NOT NULL ,
  LOGIN_TYPE INTEGER NOT NULL ,
  LOGOUT_TIME TIMESTAMP  ,
  LOGOUT_TYPE INTEGER NOT NULL ,
  LOGOUT_BY VARCHAR2(100)  ,
  LAST_ACCESS_TIME DATE  ,
  ACCESS_TOKEN VARCHAR2(500)  ,
  REFRESH_TOKEN VARCHAR2(500)  ,
  CACHE_DATA VARCHAR2(4000)  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_session primary key (SESSION_ID)
);

CREATE TABLE nop_auth_group(
  GROUP_ID VARCHAR2(50) NOT NULL ,
  NAME VARCHAR2(100) NOT NULL ,
  PARENT_ID VARCHAR2(50)  ,
  OWNER_ID VARCHAR2(50)  ,
  DEL_FLAG SMALLINT  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_group primary key (GROUP_ID)
);

CREATE TABLE nop_auth_role_resource(
  SID VARCHAR2(32) NOT NULL ,
  ROLE_ID VARCHAR2(50) NOT NULL ,
  RESOURCE_ID VARCHAR2(100)  ,
  DEL_FLAG SMALLINT  ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_role_resource primary key (SID)
);

CREATE TABLE nop_auth_op_log(
  LOG_ID VARCHAR2(32) NOT NULL ,
  USER_NAME VARCHAR2(32) NOT NULL ,
  SESSION_ID VARCHAR2(100)  ,
  TITLE VARCHAR2(200)  ,
  BIZ_OBJ_NAME VARCHAR2(100)  ,
  BIZ_ACTION_NAME VARCHAR2(100)  ,
  OP_REQUEST CLOB  ,
  OP_RESPONSE VARCHAR2(4000)  ,
  RESULT_STATUS INTEGER  ,
  ERROR_CODE VARCHAR2(200)  ,
  USED_TIME NUMBER(20)  ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  constraint PK_nop_auth_op_log primary key (LOG_ID)
);

CREATE TABLE nop_auth_group_dept(
  USER_ID VARCHAR2(50) NOT NULL ,
  GROUP_ID VARCHAR2(50) NOT NULL ,
  VERSION INTEGER NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_auth_group_dept primary key (USER_ID,GROUP_ID)
);


      COMMENT ON TABLE nop_auth_dept IS '部门';
                
      COMMENT ON COLUMN nop_auth_dept.DEPT_ID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_dept.DEPT_NAME IS '名称';
                    
      COMMENT ON COLUMN nop_auth_dept.PARENT_ID IS '父ID';
                    
      COMMENT ON COLUMN nop_auth_dept.ORDER_NUM IS '排序';
                    
      COMMENT ON COLUMN nop_auth_dept.DEPT_TYPE IS '类型';
                    
      COMMENT ON COLUMN nop_auth_dept.MANAGER_ID IS '部分负责人';
                    
      COMMENT ON COLUMN nop_auth_dept.EMAIL IS '邮件';
                    
      COMMENT ON COLUMN nop_auth_dept.PHONE IS '电话';
                    
      COMMENT ON COLUMN nop_auth_dept.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_dept.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_dept.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_dept.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_dept.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_dept.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_dept.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_position IS '岗位';
                
      COMMENT ON COLUMN nop_auth_position.POSITION_ID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_position.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_auth_position.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_position.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_position.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_position.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_position.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_position.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_position.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_role IS '角色';
                
      COMMENT ON COLUMN nop_auth_role.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_role.ROLE_NAME IS '角色名';
                    
      COMMENT ON COLUMN nop_auth_role.CHILD_ROLE_IDS IS '子角色';
                    
      COMMENT ON COLUMN nop_auth_role.IS_PRIMARY IS '是否主角色';
                    
      COMMENT ON COLUMN nop_auth_role.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_role.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_role.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_role.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_role.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_role.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_role.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_site IS '子站点';
                
      COMMENT ON COLUMN nop_auth_site.SITE_ID IS '站点ID';
                    
      COMMENT ON COLUMN nop_auth_site.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_auth_site.ORDER_NO IS '排序';
                    
      COMMENT ON COLUMN nop_auth_site.URL IS '链接';
                    
      COMMENT ON COLUMN nop_auth_site.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_auth_site.EXT_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_auth_site.CONFIG_VERSION IS '配置版本';
                    
      COMMENT ON COLUMN nop_auth_site.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_site.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_site.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_site.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_site.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_site.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_tenant IS '租户';
                
      COMMENT ON COLUMN nop_auth_tenant.TENANT_ID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_tenant.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_auth_tenant.BEGIN_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.STATUS IS '租户状态';
                    
      COMMENT ON COLUMN nop_auth_tenant.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_tenant.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_tenant.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_tenant.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_tenant.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_user IS '用户';
                
      COMMENT ON COLUMN nop_auth_user.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_user.USER_NAME IS '用户名';
                    
      COMMENT ON COLUMN nop_auth_user.PASSWORD IS '密码';
                    
      COMMENT ON COLUMN nop_auth_user.SALT IS '密码加盐';
                    
      COMMENT ON COLUMN nop_auth_user.NICK_NAME IS '昵称';
                    
      COMMENT ON COLUMN nop_auth_user.DEPT_ID IS '所属部门';
                    
      COMMENT ON COLUMN nop_auth_user.OPEN_ID IS '用户外部标识';
                    
      COMMENT ON COLUMN nop_auth_user.REL_DEPT_ID IS '相关部门';
                    
      COMMENT ON COLUMN nop_auth_user.GENDER IS '性别';
                    
      COMMENT ON COLUMN nop_auth_user.AVATAR IS '头像';
                    
      COMMENT ON COLUMN nop_auth_user.EMAIL IS '邮件';
                    
      COMMENT ON COLUMN nop_auth_user.EMAIL_VERIFIED IS '邮件已验证';
                    
      COMMENT ON COLUMN nop_auth_user.PHONE IS '电话';
                    
      COMMENT ON COLUMN nop_auth_user.PHONE_VERIFIED IS '电话已验证';
                    
      COMMENT ON COLUMN nop_auth_user.BIRTHDAY IS '生日';
                    
      COMMENT ON COLUMN nop_auth_user.USER_TYPE IS '用户类型';
                    
      COMMENT ON COLUMN nop_auth_user.STATUS IS '用户状态';
                    
      COMMENT ON COLUMN nop_auth_user.ID_TYPE IS '证件类型';
                    
      COMMENT ON COLUMN nop_auth_user.ID_NBR IS '证件号';
                    
      COMMENT ON COLUMN nop_auth_user.EXPIRE_AT IS '用户过期时间';
                    
      COMMENT ON COLUMN nop_auth_user.PWD_UPDATE_TIME IS '上次密码更新时间';
                    
      COMMENT ON COLUMN nop_auth_user.CHANGE_PWD_AT_LOGIN IS '登陆后立刻修改密码';
                    
      COMMENT ON COLUMN nop_auth_user.REAL_NAME IS '真实姓名';
                    
      COMMENT ON COLUMN nop_auth_user.MANAGER_ID IS '上级';
                    
      COMMENT ON COLUMN nop_auth_user.WORK_NO IS '工号';
                    
      COMMENT ON COLUMN nop_auth_user.POSITION_ID IS '职务';
                    
      COMMENT ON COLUMN nop_auth_user.TELEPHONE IS '座机';
                    
      COMMENT ON COLUMN nop_auth_user.CLIENT_ID IS '设备ID';
                    
      COMMENT ON COLUMN nop_auth_user.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_user.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_user.TENANT_ID IS '租户ID';
                    
      COMMENT ON COLUMN nop_auth_user.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_user.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_user.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_user.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_user.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_role_data_auth IS '角色数据权限';
                
      COMMENT ON COLUMN nop_auth_role_data_auth.SID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.BIZ_OBJ IS '业务对象名';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.PRIORITY IS '优先级';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.FILTER_CONFIG IS '业务过滤条件';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_resource IS '菜单资源';
                
      COMMENT ON COLUMN nop_auth_resource.RESOURCE_ID IS '资源ID';
                    
      COMMENT ON COLUMN nop_auth_resource.SITE_ID IS '站点ID';
                    
      COMMENT ON COLUMN nop_auth_resource.DISPLAY_NAME IS '显示名称';
                    
      COMMENT ON COLUMN nop_auth_resource.ORDER_NO IS '排序';
                    
      COMMENT ON COLUMN nop_auth_resource.RESOURCE_TYPE IS '资源类型';
                    
      COMMENT ON COLUMN nop_auth_resource.PARENT_ID IS '父资源ID';
                    
      COMMENT ON COLUMN nop_auth_resource.ICON IS '图标';
                    
      COMMENT ON COLUMN nop_auth_resource.ROUTE_PATH IS '前端路由';
                    
      COMMENT ON COLUMN nop_auth_resource.URL IS '链接';
                    
      COMMENT ON COLUMN nop_auth_resource.COMPONENT IS '组件名';
                    
      COMMENT ON COLUMN nop_auth_resource.TARGET IS '链接目标';
                    
      COMMENT ON COLUMN nop_auth_resource.HIDDEN IS '是否隐藏';
                    
      COMMENT ON COLUMN nop_auth_resource.KEEP_ALIVE IS '隐藏时保持状态';
                    
      COMMENT ON COLUMN nop_auth_resource.PERMISSIONS IS '权限标识';
                    
      COMMENT ON COLUMN nop_auth_resource.NO_AUTH IS '不检查权限';
                    
      COMMENT ON COLUMN nop_auth_resource.DEPENDS IS '依赖资源';
                    
      COMMENT ON COLUMN nop_auth_resource.IS_LEAF IS '是否叶子节点';
                    
      COMMENT ON COLUMN nop_auth_resource.STATUS IS '状态';
                    
      COMMENT ON COLUMN nop_auth_resource.Meta_CONFIG IS '扩展配置';
                    
      COMMENT ON COLUMN nop_auth_resource.PROPS_CONFIG IS '组件属性';
                    
      COMMENT ON COLUMN nop_auth_resource.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_resource.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_resource.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_resource.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_resource.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_resource.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_resource.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_ext_login IS '扩展登录方式';
                
      COMMENT ON COLUMN nop_auth_ext_login.SID IS 'ID';
                    
      COMMENT ON COLUMN nop_auth_ext_login.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_ext_login.LOGIN_TYPE IS '登录类型';
                    
      COMMENT ON COLUMN nop_auth_ext_login.EXT_ID IS '登录标识';
                    
      COMMENT ON COLUMN nop_auth_ext_login.CREDENTIAL IS '登录密码';
                    
      COMMENT ON COLUMN nop_auth_ext_login.VERIFIED IS '是否已验证';
                    
      COMMENT ON COLUMN nop_auth_ext_login.LAST_LOGIN_TIME IS '上次登录时间';
                    
      COMMENT ON COLUMN nop_auth_ext_login.LAST_LOGIN_IP IS '上次登录IP';
                    
      COMMENT ON COLUMN nop_auth_ext_login.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_ext_login.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_ext_login.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_ext_login.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_ext_login.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_ext_login.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_ext_login.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_user_role IS '用户角色';
                
      COMMENT ON COLUMN nop_auth_user_role.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_user_role.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_user_role.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_user_role.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_user_role.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_user_role.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_user_role.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_user_role.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_user_substitution IS '用户代理';
                
      COMMENT ON COLUMN nop_auth_user_substitution.SID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.SUBSTITUTED_USER_ID IS '被代理的用户ID';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.WORK_SCOPE IS '工作范围';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_session IS '会话日志';
                
      COMMENT ON COLUMN nop_auth_session.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_auth_session.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_session.USER_NAME IS '用户名';
                    
      COMMENT ON COLUMN nop_auth_session.TENANT_ID IS '租户ID';
                    
      COMMENT ON COLUMN nop_auth_session.LOGIN_ADDR IS '登录地址';
                    
      COMMENT ON COLUMN nop_auth_session.LOGIN_DEVICE IS '登录设备';
                    
      COMMENT ON COLUMN nop_auth_session.LOGIN_APP IS '应用程序';
                    
      COMMENT ON COLUMN nop_auth_session.LOGIN_OS IS '操作系统';
                    
      COMMENT ON COLUMN nop_auth_session.LOGIN_TIME IS '登录时间';
                    
      COMMENT ON COLUMN nop_auth_session.LOGIN_TYPE IS '登录方式';
                    
      COMMENT ON COLUMN nop_auth_session.LOGOUT_TIME IS '退出时间';
                    
      COMMENT ON COLUMN nop_auth_session.LOGOUT_TYPE IS '退出方式';
                    
      COMMENT ON COLUMN nop_auth_session.LOGOUT_BY IS '退出操作人';
                    
      COMMENT ON COLUMN nop_auth_session.LAST_ACCESS_TIME IS '最后访问时间';
                    
      COMMENT ON COLUMN nop_auth_session.ACCESS_TOKEN IS '访问令牌';
                    
      COMMENT ON COLUMN nop_auth_session.REFRESH_TOKEN IS '刷新令牌';
                    
      COMMENT ON COLUMN nop_auth_session.CACHE_DATA IS '缓存数据';
                    
      COMMENT ON COLUMN nop_auth_session.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_session.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_session.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_group IS '用户组';
                
      COMMENT ON COLUMN nop_auth_group.GROUP_ID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_group.NAME IS '名称';
                    
      COMMENT ON COLUMN nop_auth_group.PARENT_ID IS '父ID';
                    
      COMMENT ON COLUMN nop_auth_group.OWNER_ID IS '所有者ID';
                    
      COMMENT ON COLUMN nop_auth_group.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_group.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_group.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_group.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_group.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_group.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_group.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_role_resource IS '角色可访问资源';
                
      COMMENT ON COLUMN nop_auth_role_resource.SID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_role_resource.ROLE_ID IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_role_resource.RESOURCE_ID IS '资源ID';
                    
      COMMENT ON COLUMN nop_auth_role_resource.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_role_resource.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_role_resource.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_role_resource.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_role_resource.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_role_resource.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_role_resource.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_auth_op_log IS '操作日志';
                
      COMMENT ON COLUMN nop_auth_op_log.LOG_ID IS '主键';
                    
      COMMENT ON COLUMN nop_auth_op_log.USER_NAME IS '用户名';
                    
      COMMENT ON COLUMN nop_auth_op_log.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_auth_op_log.TITLE IS '标题';
                    
      COMMENT ON COLUMN nop_auth_op_log.BIZ_OBJ_NAME IS '业务对象';
                    
      COMMENT ON COLUMN nop_auth_op_log.BIZ_ACTION_NAME IS '业务操作';
                    
      COMMENT ON COLUMN nop_auth_op_log.OP_REQUEST IS '请求参数';
                    
      COMMENT ON COLUMN nop_auth_op_log.OP_RESPONSE IS '响应数据';
                    
      COMMENT ON COLUMN nop_auth_op_log.RESULT_STATUS IS '操作状态';
                    
      COMMENT ON COLUMN nop_auth_op_log.ERROR_CODE IS '错误码';
                    
      COMMENT ON COLUMN nop_auth_op_log.USED_TIME IS '操作时长';
                    
      COMMENT ON COLUMN nop_auth_op_log.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_op_log.CREATE_TIME IS '创建时间';
                    
      COMMENT ON TABLE nop_auth_group_dept IS '分组用户';
                
      COMMENT ON COLUMN nop_auth_group_dept.USER_ID IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_group_dept.GROUP_ID IS '分组ID';
                    
      COMMENT ON COLUMN nop_auth_group_dept.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_group_dept.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_group_dept.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_group_dept.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_group_dept.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_group_dept.REMARK IS '备注';
                    
