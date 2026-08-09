
CREATE TABLE nop_datav_dashboard(
  dashboard_id VARCHAR(32) NOT NULL ,
  dashboard_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200) NOT NULL ,
  description VARCHAR(4000)  ,
  dashboard_type INT4  ,
  publish_status INT4  ,
  published_version INT8  ,
  published_by VARCHAR(50)  ,
  published_time TIMESTAMP  ,
  layout_config VARCHAR(4000)  ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  param_config TEXT  ,
  constraint PK_nop_datav_dashboard primary key (dashboard_id)
);

CREATE TABLE nop_datav_panel(
  panel_id VARCHAR(32) NOT NULL ,
  dashboard_id VARCHAR(32) NOT NULL ,
  panel_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  panel_type INT4  ,
  dataset_ref_id VARCHAR(32)  ,
  tab_id VARCHAR(32)  ,
  sort_order INT4 default 0   ,
  panel_config VARCHAR(4000)  ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_datav_panel primary key (panel_id)
);

CREATE TABLE nop_datav_tab(
  tab_id VARCHAR(32) NOT NULL ,
  dashboard_id VARCHAR(32) NOT NULL ,
  tab_name VARCHAR(100) NOT NULL ,
  display_name VARCHAR(200)  ,
  sort_order INT4 default 0   ,
  tab_config VARCHAR(4000)  ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_datav_tab primary key (tab_id)
);

CREATE TABLE nop_datav_dataset_ref(
  dataset_ref_id VARCHAR(32) NOT NULL ,
  dashboard_id VARCHAR(32) NOT NULL ,
  ref_dataset_id VARCHAR(100) NOT NULL ,
  ref_dataset_name VARCHAR(200)  ,
  param_mapping VARCHAR(4000)  ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_datav_dataset_ref primary key (dataset_ref_id)
);

CREATE TABLE nop_datav_snapshot(
  snapshot_id VARCHAR(32) NOT NULL ,
  dashboard_id VARCHAR(32) NOT NULL ,
  snapshot_version INT8 NOT NULL ,
  snapshot_content TEXT  ,
  published_by VARCHAR(50)  ,
  published_time TIMESTAMP  ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_datav_snapshot primary key (snapshot_id)
);

CREATE TABLE nop_datav_filter_state(
  state_id VARCHAR(32) NOT NULL ,
  user_name VARCHAR(50) NOT NULL ,
  dashboard_id VARCHAR(32) NOT NULL ,
  state_content TEXT  ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_datav_filter_state primary key (state_id)
);

CREATE TABLE nop_datav_share(
  share_id VARCHAR(32) NOT NULL ,
  share_token VARCHAR(64) NOT NULL ,
  dashboard_id VARCHAR(32) NOT NULL ,
  password_hash VARCHAR(200)  ,
  expire_time TIMESTAMP  ,
  enabled INT4 default 1   ,
  del_flag INT4  ,
  version INT8 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  updated_by VARCHAR(50) NOT NULL ,
  update_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_datav_share primary key (share_id)
);


      COMMENT ON TABLE nop_datav_dashboard IS '看板';
                
      COMMENT ON COLUMN nop_datav_dashboard.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_dashboard.dashboard_name IS '看板名';
                    
      COMMENT ON COLUMN nop_datav_dashboard.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_dashboard.description IS '描述';
                    
      COMMENT ON COLUMN nop_datav_dashboard.dashboard_type IS '看板类型';
                    
      COMMENT ON COLUMN nop_datav_dashboard.publish_status IS '发布状态';
                    
      COMMENT ON COLUMN nop_datav_dashboard.published_version IS '已发布版本';
                    
      COMMENT ON COLUMN nop_datav_dashboard.published_by IS '发布人';
                    
      COMMENT ON COLUMN nop_datav_dashboard.published_time IS '发布时间';
                    
      COMMENT ON COLUMN nop_datav_dashboard.layout_config IS '布局配置';
                    
      COMMENT ON COLUMN nop_datav_dashboard.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_dashboard.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_dashboard.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_dashboard.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_dashboard.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_dashboard.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_dashboard.remark IS '备注';
                    
      COMMENT ON COLUMN nop_datav_dashboard.param_config IS '参数定义';
                    
      COMMENT ON TABLE nop_datav_panel IS '面板';
                
      COMMENT ON COLUMN nop_datav_panel.panel_id IS '面板ID';
                    
      COMMENT ON COLUMN nop_datav_panel.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_panel.panel_name IS '面板名';
                    
      COMMENT ON COLUMN nop_datav_panel.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_panel.panel_type IS '面板类型';
                    
      COMMENT ON COLUMN nop_datav_panel.dataset_ref_id IS '数据集引用ID';
                    
      COMMENT ON COLUMN nop_datav_panel.tab_id IS '页签ID';
                    
      COMMENT ON COLUMN nop_datav_panel.sort_order IS '排序';
                    
      COMMENT ON COLUMN nop_datav_panel.panel_config IS '面板配置';
                    
      COMMENT ON COLUMN nop_datav_panel.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_panel.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_panel.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_panel.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_panel.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_panel.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_panel.remark IS '备注';
                    
      COMMENT ON TABLE nop_datav_tab IS '页签';
                
      COMMENT ON COLUMN nop_datav_tab.tab_id IS '页签ID';
                    
      COMMENT ON COLUMN nop_datav_tab.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_tab.tab_name IS '页签名';
                    
      COMMENT ON COLUMN nop_datav_tab.display_name IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_tab.sort_order IS '排序';
                    
      COMMENT ON COLUMN nop_datav_tab.tab_config IS '页签配置';
                    
      COMMENT ON COLUMN nop_datav_tab.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_tab.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_tab.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_tab.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_tab.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_tab.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_tab.remark IS '备注';
                    
      COMMENT ON TABLE nop_datav_dataset_ref IS '数据集引用';
                
      COMMENT ON COLUMN nop_datav_dataset_ref.dataset_ref_id IS '数据集引用ID';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.ref_dataset_id IS '引用数据集ID';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.ref_dataset_name IS '引用数据集名';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.param_mapping IS '参数映射';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.remark IS '备注';
                    
      COMMENT ON TABLE nop_datav_snapshot IS '发布快照';
                
      COMMENT ON COLUMN nop_datav_snapshot.snapshot_id IS '快照ID';
                    
      COMMENT ON COLUMN nop_datav_snapshot.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_snapshot.snapshot_version IS '快照版本';
                    
      COMMENT ON COLUMN nop_datav_snapshot.snapshot_content IS '快照内容';
                    
      COMMENT ON COLUMN nop_datav_snapshot.published_by IS '发布人';
                    
      COMMENT ON COLUMN nop_datav_snapshot.published_time IS '发布时间';
                    
      COMMENT ON COLUMN nop_datav_snapshot.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_snapshot.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_snapshot.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_snapshot.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_snapshot.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_snapshot.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_snapshot.remark IS '备注';
                    
      COMMENT ON TABLE nop_datav_filter_state IS '筛选状态';
                
      COMMENT ON COLUMN nop_datav_filter_state.state_id IS '状态ID';
                    
      COMMENT ON COLUMN nop_datav_filter_state.user_name IS '用户名';
                    
      COMMENT ON COLUMN nop_datav_filter_state.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_filter_state.state_content IS '状态内容';
                    
      COMMENT ON COLUMN nop_datav_filter_state.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_filter_state.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_filter_state.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_filter_state.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_filter_state.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_filter_state.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_filter_state.remark IS '备注';
                    
      COMMENT ON TABLE nop_datav_share IS '看板分享';
                
      COMMENT ON COLUMN nop_datav_share.share_id IS '分享ID';
                    
      COMMENT ON COLUMN nop_datav_share.share_token IS '分享令牌';
                    
      COMMENT ON COLUMN nop_datav_share.dashboard_id IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_share.password_hash IS '密码哈希';
                    
      COMMENT ON COLUMN nop_datav_share.expire_time IS '过期时间';
                    
      COMMENT ON COLUMN nop_datav_share.enabled IS '启用标记';
                    
      COMMENT ON COLUMN nop_datav_share.del_flag IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_share.version IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_share.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_share.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_share.updated_by IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_share.update_time IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_share.remark IS '备注';
                    
