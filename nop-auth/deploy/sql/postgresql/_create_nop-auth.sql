
CREATE TABLE nop_auth_dept(
  dept_id VARCHAR(50) NOT NULL ,
  dept_name VARCHAR(100) NOT NULL ,
  parent_id VARCHAR(50)  ,
  order_num INT4  ,
  dept_type VARCHAR(10)  ,
  manager_id VARCHAR(50)  ,
  email VARCHAR(100)  ,
  phone VARCHAR(50)  ,
  del_flag INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_dept primary key (dept_id)
);

CREATE TABLE nop_auth_position(
  position_id VARCHAR(50) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  del_flag INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_position primary key (position_id)
);

CREATE TABLE nop_auth_role(
  role_id VARCHAR(50) NOT NULL ,
  role_name VARCHAR(50) NOT NULL ,
  child_role_ids VARCHAR(500)  ,
  is_primary INT4 default 0   ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint UK_NOP_AUTH_ROLE_NAME unique (role_name),
  constraint PK_nop_auth_role primary key (role_id)
);

CREATE TABLE nop_auth_site(
  site_id VARCHAR(100) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  order_no INT4 NOT NULL ,
  url VARCHAR(200)  ,
  status INT4 NOT NULL ,
  ext_config VARCHAR(1000)  ,
  config_version VARCHAR(20)  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_site primary key (site_id)
);

CREATE TABLE nop_auth_role_data_auth(
  sid VARCHAR(32) NOT NULL ,
  role_ids VARCHAR(200) NOT NULL ,
  biz_obj VARCHAR(100)  ,
  priority INT4 NOT NULL ,
  filter_config VARCHAR(4000) NOT NULL ,
  when_config VARCHAR(4000)  ,
  description VARCHAR(4000)  ,
  del_flag INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_role_data_auth primary key (sid)
);

CREATE TABLE nop_auth_tenant(
  tenant_id VARCHAR(32) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  begin_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  status INT4 default 1  NOT NULL ,
  del_flag INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_tenant primary key (tenant_id)
);

CREATE TABLE nop_auth_user(
  user_id VARCHAR(50) NOT NULL ,
  user_name VARCHAR(50) NOT NULL ,
  password VARCHAR(80) NOT NULL ,
  salt VARCHAR(32)  ,
  nick_name VARCHAR(50) NOT NULL ,
  dept_id VARCHAR(50)  ,
  open_id VARCHAR(32) NOT NULL ,
  rel_dept_id VARCHAR(50)  ,
  gender INT4 NOT NULL ,
  avatar VARCHAR(100)  ,
  email VARCHAR(100)  ,
  email_verified INT4 default 0   ,
  phone VARCHAR(50)  ,
  phone_verified INT4 default 0   ,
  birthday DATE  ,
  user_type INT4 NOT NULL ,
  status INT4 NOT NULL ,
  id_type VARCHAR(10)  ,
  id_nbr VARCHAR(100)  ,
  expire_at TIMESTAMP  ,
  pwd_update_time TIMESTAMP  ,
  change_pwd_at_login INT4 default 0   ,
  real_name VARCHAR(50)  ,
  manager_id VARCHAR(50)  ,
  work_no VARCHAR(100)  ,
  position_id VARCHAR(32)  ,
  telephone VARCHAR(50)  ,
  client_id VARCHAR(100)  ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  tenant_id VARCHAR(32) NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint UK_NOP_AUTH_USER_NAME unique (user_name),
  constraint PK_nop_auth_user primary key (user_id)
);

CREATE TABLE nop_auth_resource(
  resource_id VARCHAR(100) NOT NULL ,
  site_id VARCHAR(100) NOT NULL ,
  display_name VARCHAR(100) NOT NULL ,
  order_no INT4 NOT NULL ,
  resource_type VARCHAR(4) NOT NULL ,
  parent_id VARCHAR(100)  ,
  icon VARCHAR(150)  ,
  route_path VARCHAR(200)  ,
  url VARCHAR(1000)  ,
  component VARCHAR(200)  ,
  target VARCHAR(50)  ,
  hidden INT4 default 0  NOT NULL ,
  keep_alive INT4 default 0  NOT NULL ,
  permissions VARCHAR(200)  ,
  no_auth INT4 default 0  NOT NULL ,
  depends VARCHAR(1000)  ,
  is_leaf INT4 default 0  NOT NULL ,
  status INT4 default 1  NOT NULL ,
  auth_cascade_up INT4 default 0   ,
  meta_config VARCHAR(1000)  ,
  props_config VARCHAR(1000)  ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_resource primary key (resource_id)
);

CREATE TABLE nop_auth_ext_login(
  sid VARCHAR(32) NOT NULL ,
  user_id VARCHAR(50) NOT NULL ,
  login_type INT4 NOT NULL ,
  ext_id VARCHAR(50) NOT NULL ,
  credential VARCHAR(50)  ,
  verified BOOLEAN NOT NULL ,
  last_login_time TIMESTAMP  ,
  last_login_ip VARCHAR(20)  ,
  del_flag INT4 NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_ext_login primary key (sid)
);

CREATE TABLE nop_auth_user_role(
  user_id VARCHAR(32) NOT NULL ,
  role_id VARCHAR(50) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_user_role primary key (user_id,role_id)
);

CREATE TABLE nop_auth_user_substitution(
  sid VARCHAR(32) NOT NULL ,
  user_id VARCHAR(32) NOT NULL ,
  substituted_user_id VARCHAR(32) NOT NULL ,
  work_scope VARCHAR(50) NOT NULL ,
  start_time TIMESTAMP  ,
  end_time TIMESTAMP  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_user_substitution primary key (sid)
);

CREATE TABLE nop_auth_session(
  session_id VARCHAR(100) NOT NULL ,
  user_id VARCHAR(32) NOT NULL ,
  user_name VARCHAR(100) NOT NULL ,
  tenant_id VARCHAR(32) NOT NULL ,
  login_addr VARCHAR(100)  ,
  login_device VARCHAR(100)  ,
  login_app VARCHAR(100)  ,
  login_os VARCHAR(100)  ,
  login_time TIMESTAMP NOT NULL ,
  login_type INT4 NOT NULL ,
  logout_time TIMESTAMP  ,
  logout_type INT4 NOT NULL ,
  logout_by VARCHAR(100)  ,
  last_access_time TIMESTAMP  ,
  access_token VARCHAR(500)  ,
  refresh_token VARCHAR(500)  ,
  cache_data VARCHAR(4000)  ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_session primary key (session_id)
);

CREATE TABLE nop_auth_group(
  group_id VARCHAR(50) NOT NULL ,
  name VARCHAR(100) NOT NULL ,
  parent_id VARCHAR(50)  ,
  owner_id VARCHAR(50)  ,
  del_flag INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_group primary key (group_id)
);

CREATE TABLE nop_auth_role_resource(
  sid VARCHAR(32) NOT NULL ,
  role_id VARCHAR(50) NOT NULL ,
  resource_id VARCHAR(100)  ,
  del_flag INT4  ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_role_resource primary key (sid)
);

CREATE TABLE nop_auth_op_log(
  log_id VARCHAR(32) NOT NULL ,
  user_name VARCHAR(32) NOT NULL ,
  user_id VARCHAR(50)  ,
  session_id VARCHAR(100)  ,
  operation VARCHAR(800)  ,
  description VARCHAR(2000)  ,
  action_time TIMESTAMP NOT NULL ,
  used_time INT8 NOT NULL ,
  result_status INT4 NOT NULL ,
  error_code VARCHAR(200)  ,
  ret_message VARCHAR(1000)  ,
  op_request TEXT  ,
  op_response VARCHAR(4000)  ,
  constraint PK_nop_auth_op_log primary key (log_id)
);

CREATE TABLE nop_auth_group_dept(
  dept_id VARCHAR(50) NOT NULL ,
  group_id VARCHAR(50) NOT NULL ,
  include_child INT4 default 0  NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_group_dept primary key (dept_id,group_id)
);

CREATE TABLE nop_auth_group_user(
  user_id VARCHAR(50) NOT NULL ,
  group_id VARCHAR(50) NOT NULL ,
  version INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_auth_group_user primary key (user_id,group_id)
);


      COMMENT ON TABLE nop_auth_dept IS '部门';
                
      COMMENT ON COLUMN nop_auth_dept.dept_id IS '主键';
                    
      COMMENT ON COLUMN nop_auth_dept.dept_name IS '名称';
                    
      COMMENT ON COLUMN nop_auth_dept.parent_id IS '父ID';
                    
      COMMENT ON COLUMN nop_auth_dept.order_num IS '排序';
                    
      COMMENT ON COLUMN nop_auth_dept.dept_type IS '类型';
                    
      COMMENT ON COLUMN nop_auth_dept.manager_id IS '部门负责人';
                    
      COMMENT ON COLUMN nop_auth_dept.email IS '邮件';
                    
      COMMENT ON COLUMN nop_auth_dept.phone IS '电话';
                    
      COMMENT ON COLUMN nop_auth_dept.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_dept.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_dept.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_dept.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_dept.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_dept.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_dept.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_position IS '岗位';
                
      COMMENT ON COLUMN nop_auth_position.position_id IS '主键';
                    
      COMMENT ON COLUMN nop_auth_position.name IS '名称';
                    
      COMMENT ON COLUMN nop_auth_position.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_position.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_position.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_position.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_position.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_position.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_position.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_role IS '角色';
                
      COMMENT ON COLUMN nop_auth_role.role_id IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_role.role_name IS '角色名';
                    
      COMMENT ON COLUMN nop_auth_role.child_role_ids IS '子角色';
                    
      COMMENT ON COLUMN nop_auth_role.is_primary IS '是否主角色';
                    
      COMMENT ON COLUMN nop_auth_role.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_role.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_role.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_role.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_role.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_role.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_role.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_site IS '子站点';
                
      COMMENT ON COLUMN nop_auth_site.site_id IS '站点ID';
                    
      COMMENT ON COLUMN nop_auth_site.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_auth_site.order_no IS '排序';
                    
      COMMENT ON COLUMN nop_auth_site.url IS '链接';
                    
      COMMENT ON COLUMN nop_auth_site.status IS '状态';
                    
      COMMENT ON COLUMN nop_auth_site.ext_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_auth_site.config_version IS '配置版本';
                    
      COMMENT ON COLUMN nop_auth_site.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_site.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_site.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_site.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_site.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_site.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_role_data_auth IS '角色数据权限';
                
      COMMENT ON COLUMN nop_auth_role_data_auth.sid IS '主键';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.role_ids IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.biz_obj IS '业务对象名';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.priority IS '优先级';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.filter_config IS '业务过滤条件';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.when_config IS '权限应用条件';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.description IS '描述';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_role_data_auth.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_tenant IS '租户';
                
      COMMENT ON COLUMN nop_auth_tenant.tenant_id IS '主键';
                    
      COMMENT ON COLUMN nop_auth_tenant.name IS '名称';
                    
      COMMENT ON COLUMN nop_auth_tenant.begin_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.status IS '租户状态';
                    
      COMMENT ON COLUMN nop_auth_tenant.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_tenant.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_tenant.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_tenant.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_tenant.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_tenant.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_user IS '用户';
                
      COMMENT ON COLUMN nop_auth_user.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_user.user_name IS '用户名';
                    
      COMMENT ON COLUMN nop_auth_user.password IS '密码';
                    
      COMMENT ON COLUMN nop_auth_user.salt IS '密码加盐';
                    
      COMMENT ON COLUMN nop_auth_user.nick_name IS '昵称';
                    
      COMMENT ON COLUMN nop_auth_user.dept_id IS '所属部门';
                    
      COMMENT ON COLUMN nop_auth_user.open_id IS '用户外部标识';
                    
      COMMENT ON COLUMN nop_auth_user.rel_dept_id IS '相关部门';
                    
      COMMENT ON COLUMN nop_auth_user.gender IS '性别';
                    
      COMMENT ON COLUMN nop_auth_user.avatar IS '头像';
                    
      COMMENT ON COLUMN nop_auth_user.email IS '邮件';
                    
      COMMENT ON COLUMN nop_auth_user.email_verified IS '邮件已验证';
                    
      COMMENT ON COLUMN nop_auth_user.phone IS '电话';
                    
      COMMENT ON COLUMN nop_auth_user.phone_verified IS '电话已验证';
                    
      COMMENT ON COLUMN nop_auth_user.birthday IS '生日';
                    
      COMMENT ON COLUMN nop_auth_user.user_type IS '用户类型';
                    
      COMMENT ON COLUMN nop_auth_user.status IS '用户状态';
                    
      COMMENT ON COLUMN nop_auth_user.id_type IS '证件类型';
                    
      COMMENT ON COLUMN nop_auth_user.id_nbr IS '证件号';
                    
      COMMENT ON COLUMN nop_auth_user.expire_at IS '用户过期时间';
                    
      COMMENT ON COLUMN nop_auth_user.pwd_update_time IS '上次密码更新时间';
                    
      COMMENT ON COLUMN nop_auth_user.change_pwd_at_login IS '登陆后立刻修改密码';
                    
      COMMENT ON COLUMN nop_auth_user.real_name IS '真实姓名';
                    
      COMMENT ON COLUMN nop_auth_user.manager_id IS '上级';
                    
      COMMENT ON COLUMN nop_auth_user.work_no IS '工号';
                    
      COMMENT ON COLUMN nop_auth_user.position_id IS '职务';
                    
      COMMENT ON COLUMN nop_auth_user.telephone IS '座机';
                    
      COMMENT ON COLUMN nop_auth_user.client_id IS '设备ID';
                    
      COMMENT ON COLUMN nop_auth_user.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_user.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_user.tenant_id IS '租户ID';
                    
      COMMENT ON COLUMN nop_auth_user.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_user.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_user.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_user.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_user.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_resource IS '菜单资源';
                
      COMMENT ON COLUMN nop_auth_resource.resource_id IS '资源ID';
                    
      COMMENT ON COLUMN nop_auth_resource.site_id IS '站点ID';
                    
      COMMENT ON COLUMN nop_auth_resource.display_name IS '显示名称';
                    
      COMMENT ON COLUMN nop_auth_resource.order_no IS '排序';
                    
      COMMENT ON COLUMN nop_auth_resource.resource_type IS '资源类型';
                    
      COMMENT ON COLUMN nop_auth_resource.parent_id IS '父资源ID';
                    
      COMMENT ON COLUMN nop_auth_resource.icon IS '图标';
                    
      COMMENT ON COLUMN nop_auth_resource.route_path IS '前端路由';
                    
      COMMENT ON COLUMN nop_auth_resource.url IS '链接';
                    
      COMMENT ON COLUMN nop_auth_resource.component IS '组件名';
                    
      COMMENT ON COLUMN nop_auth_resource.target IS '链接目标';
                    
      COMMENT ON COLUMN nop_auth_resource.hidden IS '是否隐藏';
                    
      COMMENT ON COLUMN nop_auth_resource.keep_alive IS '隐藏时保持状态';
                    
      COMMENT ON COLUMN nop_auth_resource.permissions IS '权限标识';
                    
      COMMENT ON COLUMN nop_auth_resource.no_auth IS '不检查权限';
                    
      COMMENT ON COLUMN nop_auth_resource.depends IS '依赖资源';
                    
      COMMENT ON COLUMN nop_auth_resource.is_leaf IS '是否叶子节点';
                    
      COMMENT ON COLUMN nop_auth_resource.status IS '状态';
                    
      COMMENT ON COLUMN nop_auth_resource.auth_cascade_up IS '自动更新父节点的权限';
                    
      COMMENT ON COLUMN nop_auth_resource.meta_config IS '扩展配置';
                    
      COMMENT ON COLUMN nop_auth_resource.props_config IS '组件属性';
                    
      COMMENT ON COLUMN nop_auth_resource.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_resource.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_resource.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_resource.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_resource.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_resource.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_resource.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_ext_login IS '扩展登录方式';
                
      COMMENT ON COLUMN nop_auth_ext_login.sid IS 'ID';
                    
      COMMENT ON COLUMN nop_auth_ext_login.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_ext_login.login_type IS '登录类型';
                    
      COMMENT ON COLUMN nop_auth_ext_login.ext_id IS '登录标识';
                    
      COMMENT ON COLUMN nop_auth_ext_login.credential IS '登录密码';
                    
      COMMENT ON COLUMN nop_auth_ext_login.verified IS '是否已验证';
                    
      COMMENT ON COLUMN nop_auth_ext_login.last_login_time IS '上次登录时间';
                    
      COMMENT ON COLUMN nop_auth_ext_login.last_login_ip IS '上次登录IP';
                    
      COMMENT ON COLUMN nop_auth_ext_login.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_ext_login.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_ext_login.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_ext_login.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_ext_login.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_ext_login.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_ext_login.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_user_role IS '用户角色';
                
      COMMENT ON COLUMN nop_auth_user_role.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_user_role.role_id IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_user_role.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_user_role.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_user_role.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_user_role.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_user_role.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_user_role.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_user_substitution IS '用户代理';
                
      COMMENT ON COLUMN nop_auth_user_substitution.sid IS '主键';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.substituted_user_id IS '被代理的用户ID';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.work_scope IS '工作范围';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.start_time IS '开始时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.end_time IS '结束时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_user_substitution.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_session IS '会话日志';
                
      COMMENT ON COLUMN nop_auth_session.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_auth_session.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_session.user_name IS '用户名';
                    
      COMMENT ON COLUMN nop_auth_session.tenant_id IS '租户ID';
                    
      COMMENT ON COLUMN nop_auth_session.login_addr IS '登录地址';
                    
      COMMENT ON COLUMN nop_auth_session.login_device IS '登录设备';
                    
      COMMENT ON COLUMN nop_auth_session.login_app IS '应用程序';
                    
      COMMENT ON COLUMN nop_auth_session.login_os IS '操作系统';
                    
      COMMENT ON COLUMN nop_auth_session.login_time IS '登录时间';
                    
      COMMENT ON COLUMN nop_auth_session.login_type IS '登录方式';
                    
      COMMENT ON COLUMN nop_auth_session.logout_time IS '退出时间';
                    
      COMMENT ON COLUMN nop_auth_session.logout_type IS '退出方式';
                    
      COMMENT ON COLUMN nop_auth_session.logout_by IS '退出操作人';
                    
      COMMENT ON COLUMN nop_auth_session.last_access_time IS '最后访问时间';
                    
      COMMENT ON COLUMN nop_auth_session.access_token IS '访问令牌';
                    
      COMMENT ON COLUMN nop_auth_session.refresh_token IS '刷新令牌';
                    
      COMMENT ON COLUMN nop_auth_session.cache_data IS '缓存数据';
                    
      COMMENT ON COLUMN nop_auth_session.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_session.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_session.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_group IS '用户组';
                
      COMMENT ON COLUMN nop_auth_group.group_id IS '主键';
                    
      COMMENT ON COLUMN nop_auth_group.name IS '名称';
                    
      COMMENT ON COLUMN nop_auth_group.parent_id IS '父ID';
                    
      COMMENT ON COLUMN nop_auth_group.owner_id IS '所有者ID';
                    
      COMMENT ON COLUMN nop_auth_group.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_group.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_group.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_group.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_group.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_group.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_group.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_role_resource IS '角色可访问资源';
                
      COMMENT ON COLUMN nop_auth_role_resource.sid IS '主键';
                    
      COMMENT ON COLUMN nop_auth_role_resource.role_id IS '角色ID';
                    
      COMMENT ON COLUMN nop_auth_role_resource.resource_id IS '资源ID';
                    
      COMMENT ON COLUMN nop_auth_role_resource.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_auth_role_resource.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_role_resource.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_role_resource.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_role_resource.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_role_resource.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_role_resource.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_op_log IS '操作日志';
                
      COMMENT ON COLUMN nop_auth_op_log.log_id IS '主键';
                    
      COMMENT ON COLUMN nop_auth_op_log.user_name IS '用户名';
                    
      COMMENT ON COLUMN nop_auth_op_log.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_op_log.session_id IS '会话ID';
                    
      COMMENT ON COLUMN nop_auth_op_log.operation IS '业务操作';
                    
      COMMENT ON COLUMN nop_auth_op_log.description IS '操作描述';
                    
      COMMENT ON COLUMN nop_auth_op_log.action_time IS '操作时间';
                    
      COMMENT ON COLUMN nop_auth_op_log.used_time IS '操作时长';
                    
      COMMENT ON COLUMN nop_auth_op_log.result_status IS '操作状态';
                    
      COMMENT ON COLUMN nop_auth_op_log.error_code IS '错误码';
                    
      COMMENT ON COLUMN nop_auth_op_log.ret_message IS '返回消息';
                    
      COMMENT ON COLUMN nop_auth_op_log.op_request IS '请求参数';
                    
      COMMENT ON COLUMN nop_auth_op_log.op_response IS '响应数据';
                    
      COMMENT ON TABLE nop_auth_group_dept IS '分组部门';
                
      COMMENT ON COLUMN nop_auth_group_dept.dept_id IS '部门ID';
                    
      COMMENT ON COLUMN nop_auth_group_dept.group_id IS '分组ID';
                    
      COMMENT ON COLUMN nop_auth_group_dept.include_child IS '是否包含下级';
                    
      COMMENT ON COLUMN nop_auth_group_dept.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_group_dept.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_group_dept.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_group_dept.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_group_dept.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_group_dept.remark IS '备注';
                    
      COMMENT ON TABLE nop_auth_group_user IS '分组用户';
                
      COMMENT ON COLUMN nop_auth_group_user.user_id IS '用户ID';
                    
      COMMENT ON COLUMN nop_auth_group_user.group_id IS '分组ID';
                    
      COMMENT ON COLUMN nop_auth_group_user.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_auth_group_user.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_auth_group_user.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_auth_group_user.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_auth_group_user.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_auth_group_user.remark IS '备注';
                    
