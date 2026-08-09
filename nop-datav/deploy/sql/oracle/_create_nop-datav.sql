
CREATE TABLE nop_datav_dashboard(
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  DASHBOARD_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  DESCRIPTION VARCHAR2(4000)  ,
  DASHBOARD_TYPE INTEGER  ,
  PUBLISH_STATUS INTEGER  ,
  PUBLISHED_VERSION NUMBER(20)  ,
  PUBLISHED_BY VARCHAR2(50)  ,
  PUBLISHED_TIME TIMESTAMP  ,
  LAYOUT_CONFIG VARCHAR2(4000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_dashboard primary key (DASHBOARD_ID)
);

CREATE TABLE nop_datav_panel(
  PANEL_ID VARCHAR2(32) NOT NULL ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  PANEL_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  PANEL_TYPE INTEGER  ,
  DATASET_REF_ID VARCHAR2(32)  ,
  TAB_ID VARCHAR2(32)  ,
  SORT_ORDER INTEGER default 0   ,
  PANEL_CONFIG VARCHAR2(4000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_panel primary key (PANEL_ID)
);

CREATE TABLE nop_datav_tab(
  TAB_ID VARCHAR2(32) NOT NULL ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  TAB_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  SORT_ORDER INTEGER default 0   ,
  TAB_CONFIG VARCHAR2(4000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_tab primary key (TAB_ID)
);

CREATE TABLE nop_datav_dataset_ref(
  DATASET_REF_ID VARCHAR2(32) NOT NULL ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  REF_DATASET_ID VARCHAR2(100) NOT NULL ,
  REF_DATASET_NAME VARCHAR2(200)  ,
  PARAM_MAPPING VARCHAR2(4000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_dataset_ref primary key (DATASET_REF_ID)
);

CREATE TABLE nop_datav_snapshot(
  SNAPSHOT_ID VARCHAR2(32) NOT NULL ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  SNAPSHOT_VERSION NUMBER(20) NOT NULL ,
  SNAPSHOT_CONTENT CLOB  ,
  PUBLISHED_BY VARCHAR2(50)  ,
  PUBLISHED_TIME TIMESTAMP  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_snapshot primary key (SNAPSHOT_ID)
);


      COMMENT ON TABLE nop_datav_dashboard IS '看板';
                
      COMMENT ON COLUMN nop_datav_dashboard.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_dashboard.DASHBOARD_NAME IS '看板名';
                    
      COMMENT ON COLUMN nop_datav_dashboard.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_dashboard.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_datav_dashboard.DASHBOARD_TYPE IS '看板类型';
                    
      COMMENT ON COLUMN nop_datav_dashboard.PUBLISH_STATUS IS '发布状态';
                    
      COMMENT ON COLUMN nop_datav_dashboard.PUBLISHED_VERSION IS '已发布版本';
                    
      COMMENT ON COLUMN nop_datav_dashboard.PUBLISHED_BY IS '发布人';
                    
      COMMENT ON COLUMN nop_datav_dashboard.PUBLISHED_TIME IS '发布时间';
                    
      COMMENT ON COLUMN nop_datav_dashboard.LAYOUT_CONFIG IS '布局配置';
                    
      COMMENT ON COLUMN nop_datav_dashboard.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_dashboard.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_dashboard.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_dashboard.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_dashboard.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_dashboard.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_dashboard.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_panel IS '面板';
                
      COMMENT ON COLUMN nop_datav_panel.PANEL_ID IS '面板ID';
                    
      COMMENT ON COLUMN nop_datav_panel.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_panel.PANEL_NAME IS '面板名';
                    
      COMMENT ON COLUMN nop_datav_panel.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_panel.PANEL_TYPE IS '面板类型';
                    
      COMMENT ON COLUMN nop_datav_panel.DATASET_REF_ID IS '数据集引用ID';
                    
      COMMENT ON COLUMN nop_datav_panel.TAB_ID IS '页签ID';
                    
      COMMENT ON COLUMN nop_datav_panel.SORT_ORDER IS '排序';
                    
      COMMENT ON COLUMN nop_datav_panel.PANEL_CONFIG IS '面板配置';
                    
      COMMENT ON COLUMN nop_datav_panel.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_panel.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_panel.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_panel.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_panel.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_panel.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_panel.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_tab IS '页签';
                
      COMMENT ON COLUMN nop_datav_tab.TAB_ID IS '页签ID';
                    
      COMMENT ON COLUMN nop_datav_tab.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_tab.TAB_NAME IS '页签名';
                    
      COMMENT ON COLUMN nop_datav_tab.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_tab.SORT_ORDER IS '排序';
                    
      COMMENT ON COLUMN nop_datav_tab.TAB_CONFIG IS '页签配置';
                    
      COMMENT ON COLUMN nop_datav_tab.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_tab.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_tab.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_tab.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_tab.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_tab.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_tab.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_dataset_ref IS '数据集引用';
                
      COMMENT ON COLUMN nop_datav_dataset_ref.DATASET_REF_ID IS '数据集引用ID';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.REF_DATASET_ID IS '引用数据集ID';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.REF_DATASET_NAME IS '引用数据集名';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.PARAM_MAPPING IS '参数映射';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_dataset_ref.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_snapshot IS '发布快照';
                
      COMMENT ON COLUMN nop_datav_snapshot.SNAPSHOT_ID IS '快照ID';
                    
      COMMENT ON COLUMN nop_datav_snapshot.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_snapshot.SNAPSHOT_VERSION IS '快照版本';
                    
      COMMENT ON COLUMN nop_datav_snapshot.SNAPSHOT_CONTENT IS '快照内容';
                    
      COMMENT ON COLUMN nop_datav_snapshot.PUBLISHED_BY IS '发布人';
                    
      COMMENT ON COLUMN nop_datav_snapshot.PUBLISHED_TIME IS '发布时间';
                    
      COMMENT ON COLUMN nop_datav_snapshot.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_snapshot.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_snapshot.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_snapshot.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_snapshot.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_snapshot.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_snapshot.REMARK IS '备注';
                    
