
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
  PARAM_CONFIG CLOB  ,
  constraint PK_nop_datav_dashboard primary key (DASHBOARD_ID)
);

CREATE TABLE nop_datav_export_task(
  TASK_ID VARCHAR2(32) NOT NULL ,
  SOURCE_TYPE VARCHAR2(20) NOT NULL ,
  SOURCE_ID VARCHAR2(32) NOT NULL ,
  FORMAT VARCHAR2(10) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  PARAMS CLOB  ,
  FILE_RECORD_ID VARCHAR2(64)  ,
  ROW_COUNT NUMBER(20)  ,
  ERROR_MSG VARCHAR2(1000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_export_task primary key (TASK_ID)
);

CREATE TABLE nop_datav_screen(
  SCREEN_ID VARCHAR2(32) NOT NULL ,
  SCREEN_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200) NOT NULL ,
  DESCRIPTION VARCHAR2(4000)  ,
  SCREEN_WIDTH INTEGER NOT NULL ,
  SCREEN_HEIGHT INTEGER NOT NULL ,
  ADAPTOR_MODE INTEGER default 10   ,
  BACKGROUND_CONFIG VARCHAR2(4000)  ,
  PUBLISH_STATUS INTEGER  ,
  PUBLISHED_VERSION NUMBER(20)  ,
  PUBLISHED_BY VARCHAR2(50)  ,
  PUBLISHED_TIME TIMESTAMP  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  THUMBNAIL VARCHAR2(4000)  ,
  constraint PK_nop_datav_screen primary key (SCREEN_ID)
);

CREATE TABLE nop_datav_chat_session(
  SESSION_ID VARCHAR2(32) NOT NULL ,
  USER_NAME VARCHAR2(50) NOT NULL ,
  SESSION_TITLE VARCHAR2(200)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_chat_session primary key (SESSION_ID)
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

CREATE TABLE nop_datav_filter_state(
  STATE_ID VARCHAR2(32) NOT NULL ,
  USER_NAME VARCHAR2(50) NOT NULL ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  STATE_CONTENT CLOB  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_filter_state primary key (STATE_ID)
);

CREATE TABLE nop_datav_share(
  SHARE_ID VARCHAR2(32) NOT NULL ,
  SHARE_TOKEN VARCHAR2(64) NOT NULL ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  PASSWORD_HASH VARCHAR2(200)  ,
  EXPIRE_TIME TIMESTAMP  ,
  ENABLED SMALLINT default 1   ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  VISIT_COUNT NUMBER(20) default 0   ,
  LAST_VISIT_TIME TIMESTAMP  ,
  constraint PK_nop_datav_share primary key (SHARE_ID)
);

CREATE TABLE nop_datav_report_task(
  REPORT_TASK_ID VARCHAR2(32) NOT NULL ,
  TASK_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  DASHBOARD_ID VARCHAR2(32) NOT NULL ,
  CRON_EXPR VARCHAR2(100) NOT NULL ,
  FORMAT VARCHAR2(10) NOT NULL ,
  RECIPIENTS CLOB  ,
  NOTIFY_CHANNELS CLOB  ,
  PARAMS CLOB  ,
  STATUS INTEGER NOT NULL ,
  GRACE_MINUTES INTEGER default 60   ,
  TEMPLATE_KEY VARCHAR2(100)  ,
  LAST_RUN_TIME TIMESTAMP  ,
  LAST_RUN_STATUS VARCHAR2(20)  ,
  LAST_RUN_ERROR VARCHAR2(1000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_report_task primary key (REPORT_TASK_ID)
);

CREATE TABLE nop_datav_screen_widget(
  WIDGET_ID VARCHAR2(32) NOT NULL ,
  SCREEN_ID VARCHAR2(32) NOT NULL ,
  WIDGET_NAME VARCHAR2(100)  ,
  DISPLAY_NAME VARCHAR2(200)  ,
  COMPONENT_TYPE VARCHAR2(50) NOT NULL ,
  DATASET_REF_ID VARCHAR2(32)  ,
  X INTEGER default 0  NOT NULL ,
  Y INTEGER default 0  NOT NULL ,
  W INTEGER NOT NULL ,
  H INTEGER NOT NULL ,
  Z INTEGER default 0   ,
  WIDGET_CONFIG VARCHAR2(4000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_screen_widget primary key (WIDGET_ID)
);

CREATE TABLE nop_datav_screen_snapshot(
  SNAPSHOT_ID VARCHAR2(32) NOT NULL ,
  SCREEN_ID VARCHAR2(32) NOT NULL ,
  SNAPSHOT_VERSION NUMBER(20) NOT NULL ,
  SNAPSHOT_CONTENT CLOB NOT NULL ,
  PUBLISHED_BY VARCHAR2(50)  ,
  PUBLISHED_TIME TIMESTAMP  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_screen_snapshot primary key (SNAPSHOT_ID)
);

CREATE TABLE nop_datav_chat_message(
  MESSAGE_ID VARCHAR2(32) NOT NULL ,
  SESSION_ID VARCHAR2(32) NOT NULL ,
  SEQ INTEGER NOT NULL ,
  ROLE VARCHAR2(20) NOT NULL ,
  CONTENT CLOB NOT NULL ,
  RESULT_JSON CLOB  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_chat_message primary key (MESSAGE_ID)
);

CREATE TABLE nop_datav_alert_rule(
  ALERT_RULE_ID VARCHAR2(32) NOT NULL ,
  RULE_NAME VARCHAR2(100) NOT NULL ,
  DISPLAY_NAME VARCHAR2(200)  ,
  PANEL_ID VARCHAR2(32) NOT NULL ,
  VALUE_FIELD VARCHAR2(100) NOT NULL ,
  AGGREGATION VARCHAR2(20) NOT NULL ,
  OPERATOR VARCHAR2(20) NOT NULL ,
  THRESHOLD_VALUE NUMBER(20,4) NOT NULL ,
  THRESHOLD_VALUE2 NUMBER(20,4)  ,
  REARM_SECONDS INTEGER default 0   ,
  NOTIFY_CHANNELS CLOB  ,
  RECIPIENTS CLOB  ,
  CRON_EXPR VARCHAR2(100) NOT NULL ,
  PARAMS CLOB  ,
  TEMPLATE_KEY VARCHAR2(100)  ,
  STATUS INTEGER NOT NULL ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_alert_rule primary key (ALERT_RULE_ID)
);

CREATE TABLE nop_datav_report_delivery(
  DELIVERY_ID VARCHAR2(32) NOT NULL ,
  REPORT_TASK_ID VARCHAR2(32) NOT NULL ,
  STATUS INTEGER NOT NULL ,
  GENERATED_FILE_RECORD_ID VARCHAR2(64)  ,
  DELIVERED_CHANNELS VARCHAR2(200)  ,
  ROW_COUNT NUMBER(20)  ,
  ERROR_MSG VARCHAR2(1000)  ,
  TRIGGERED_BY VARCHAR2(20) NOT NULL ,
  START_TIME TIMESTAMP  ,
  END_TIME TIMESTAMP  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_report_delivery primary key (DELIVERY_ID)
);

CREATE TABLE nop_datav_alert_state(
  ALERT_STATE_ID VARCHAR2(32) NOT NULL ,
  ALERT_RULE_ID VARCHAR2(32) NOT NULL ,
  STATE VARCHAR2(20) NOT NULL ,
  LAST_EVAL_TIME TIMESTAMP  ,
  LAST_TRIGGERED_TIME TIMESTAMP  ,
  LAST_RESOLVED_TIME TIMESTAMP  ,
  LAST_NOTIFIED_TIME TIMESTAMP  ,
  CONSECUTIVE_EVAL_COUNT INTEGER default 0   ,
  ERROR_MSG VARCHAR2(1000)  ,
  DEL_FLAG SMALLINT  ,
  VERSION NUMBER(20) NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  UPDATED_BY VARCHAR2(50) NOT NULL ,
  UPDATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
  constraint PK_nop_datav_alert_state primary key (ALERT_STATE_ID)
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
                    
      COMMENT ON COLUMN nop_datav_dashboard.PARAM_CONFIG IS '参数定义';
                    
      COMMENT ON TABLE nop_datav_export_task IS '导出任务';
                
      COMMENT ON COLUMN nop_datav_export_task.TASK_ID IS '任务ID';
                    
      COMMENT ON COLUMN nop_datav_export_task.SOURCE_TYPE IS '来源类型';
                    
      COMMENT ON COLUMN nop_datav_export_task.SOURCE_ID IS '来源ID';
                    
      COMMENT ON COLUMN nop_datav_export_task.FORMAT IS '导出格式';
                    
      COMMENT ON COLUMN nop_datav_export_task.STATUS IS '任务状态';
                    
      COMMENT ON COLUMN nop_datav_export_task.PARAMS IS '导出参数';
                    
      COMMENT ON COLUMN nop_datav_export_task.FILE_RECORD_ID IS '文件记录ID';
                    
      COMMENT ON COLUMN nop_datav_export_task.ROW_COUNT IS '导出行数';
                    
      COMMENT ON COLUMN nop_datav_export_task.ERROR_MSG IS '错误信息';
                    
      COMMENT ON COLUMN nop_datav_export_task.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_export_task.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_export_task.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_export_task.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_export_task.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_export_task.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_export_task.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_screen IS '大屏';
                
      COMMENT ON COLUMN nop_datav_screen.SCREEN_ID IS '大屏ID';
                    
      COMMENT ON COLUMN nop_datav_screen.SCREEN_NAME IS '大屏名';
                    
      COMMENT ON COLUMN nop_datav_screen.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_screen.DESCRIPTION IS '描述';
                    
      COMMENT ON COLUMN nop_datav_screen.SCREEN_WIDTH IS '画布宽度';
                    
      COMMENT ON COLUMN nop_datav_screen.SCREEN_HEIGHT IS '画布高度';
                    
      COMMENT ON COLUMN nop_datav_screen.ADAPTOR_MODE IS '适配模式';
                    
      COMMENT ON COLUMN nop_datav_screen.BACKGROUND_CONFIG IS '背景配置';
                    
      COMMENT ON COLUMN nop_datav_screen.PUBLISH_STATUS IS '发布状态';
                    
      COMMENT ON COLUMN nop_datav_screen.PUBLISHED_VERSION IS '已发布版本';
                    
      COMMENT ON COLUMN nop_datav_screen.PUBLISHED_BY IS '发布人';
                    
      COMMENT ON COLUMN nop_datav_screen.PUBLISHED_TIME IS '发布时间';
                    
      COMMENT ON COLUMN nop_datav_screen.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_screen.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_screen.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_screen.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_screen.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_screen.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_screen.REMARK IS '备注';
                    
      COMMENT ON COLUMN nop_datav_screen.THUMBNAIL IS '缩略图';
                    
      COMMENT ON TABLE nop_datav_chat_session IS 'ChatBI会话';
                
      COMMENT ON COLUMN nop_datav_chat_session.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_datav_chat_session.USER_NAME IS '用户名';
                    
      COMMENT ON COLUMN nop_datav_chat_session.SESSION_TITLE IS '会话标题';
                    
      COMMENT ON COLUMN nop_datav_chat_session.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_chat_session.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_chat_session.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_chat_session.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_chat_session.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_chat_session.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_chat_session.REMARK IS '备注';
                    
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
                    
      COMMENT ON TABLE nop_datav_filter_state IS '筛选状态';
                
      COMMENT ON COLUMN nop_datav_filter_state.STATE_ID IS '状态ID';
                    
      COMMENT ON COLUMN nop_datav_filter_state.USER_NAME IS '用户名';
                    
      COMMENT ON COLUMN nop_datav_filter_state.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_filter_state.STATE_CONTENT IS '状态内容';
                    
      COMMENT ON COLUMN nop_datav_filter_state.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_filter_state.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_filter_state.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_filter_state.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_filter_state.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_filter_state.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_filter_state.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_share IS '看板分享';
                
      COMMENT ON COLUMN nop_datav_share.SHARE_ID IS '分享ID';
                    
      COMMENT ON COLUMN nop_datav_share.SHARE_TOKEN IS '分享令牌';
                    
      COMMENT ON COLUMN nop_datav_share.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_share.PASSWORD_HASH IS '密码哈希';
                    
      COMMENT ON COLUMN nop_datav_share.EXPIRE_TIME IS '过期时间';
                    
      COMMENT ON COLUMN nop_datav_share.ENABLED IS '启用标记';
                    
      COMMENT ON COLUMN nop_datav_share.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_share.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_share.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_share.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_share.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_share.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_share.REMARK IS '备注';
                    
      COMMENT ON COLUMN nop_datav_share.VISIT_COUNT IS '访问计数';
                    
      COMMENT ON COLUMN nop_datav_share.LAST_VISIT_TIME IS '最近访问时间';
                    
      COMMENT ON TABLE nop_datav_report_task IS '定时报告任务';
                
      COMMENT ON COLUMN nop_datav_report_task.REPORT_TASK_ID IS '报告任务ID';
                    
      COMMENT ON COLUMN nop_datav_report_task.TASK_NAME IS '任务名';
                    
      COMMENT ON COLUMN nop_datav_report_task.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_report_task.DASHBOARD_ID IS '看板ID';
                    
      COMMENT ON COLUMN nop_datav_report_task.CRON_EXPR IS 'cron表达式';
                    
      COMMENT ON COLUMN nop_datav_report_task.FORMAT IS '导出格式';
                    
      COMMENT ON COLUMN nop_datav_report_task.RECIPIENTS IS '收件人';
                    
      COMMENT ON COLUMN nop_datav_report_task.NOTIFY_CHANNELS IS '通知渠道';
                    
      COMMENT ON COLUMN nop_datav_report_task.PARAMS IS '报告参数';
                    
      COMMENT ON COLUMN nop_datav_report_task.STATUS IS '任务状态';
                    
      COMMENT ON COLUMN nop_datav_report_task.GRACE_MINUTES IS 'grace期(分钟)';
                    
      COMMENT ON COLUMN nop_datav_report_task.TEMPLATE_KEY IS '模板键';
                    
      COMMENT ON COLUMN nop_datav_report_task.LAST_RUN_TIME IS '最后执行时间';
                    
      COMMENT ON COLUMN nop_datav_report_task.LAST_RUN_STATUS IS '最后执行状态';
                    
      COMMENT ON COLUMN nop_datav_report_task.LAST_RUN_ERROR IS '最后执行错误';
                    
      COMMENT ON COLUMN nop_datav_report_task.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_report_task.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_report_task.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_report_task.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_report_task.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_report_task.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_report_task.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_screen_widget IS '大屏组件';
                
      COMMENT ON COLUMN nop_datav_screen_widget.WIDGET_ID IS '组件ID';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.SCREEN_ID IS '大屏ID';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.WIDGET_NAME IS '组件名';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.COMPONENT_TYPE IS '组件类型';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.DATASET_REF_ID IS '数据集引用ID';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.X IS 'X坐标';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.Y IS 'Y坐标';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.W IS '宽度';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.H IS '高度';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.Z IS '层级';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.WIDGET_CONFIG IS '组件配置';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_screen_widget.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_screen_snapshot IS '大屏快照';
                
      COMMENT ON COLUMN nop_datav_screen_snapshot.SNAPSHOT_ID IS '快照ID';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.SCREEN_ID IS '大屏ID';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.SNAPSHOT_VERSION IS '快照版本';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.SNAPSHOT_CONTENT IS '快照内容';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.PUBLISHED_BY IS '发布人';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.PUBLISHED_TIME IS '发布时间';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_screen_snapshot.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_chat_message IS 'ChatBI会话消息';
                
      COMMENT ON COLUMN nop_datav_chat_message.MESSAGE_ID IS '消息ID';
                    
      COMMENT ON COLUMN nop_datav_chat_message.SESSION_ID IS '会话ID';
                    
      COMMENT ON COLUMN nop_datav_chat_message.SEQ IS '消息序号';
                    
      COMMENT ON COLUMN nop_datav_chat_message.ROLE IS '消息角色';
                    
      COMMENT ON COLUMN nop_datav_chat_message.CONTENT IS '消息内容';
                    
      COMMENT ON COLUMN nop_datav_chat_message.RESULT_JSON IS '结构化结果JSON';
                    
      COMMENT ON COLUMN nop_datav_chat_message.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_chat_message.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_chat_message.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_chat_message.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_chat_message.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_chat_message.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_chat_message.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_alert_rule IS '告警规则';
                
      COMMENT ON COLUMN nop_datav_alert_rule.ALERT_RULE_ID IS '告警规则ID';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.RULE_NAME IS '规则名';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.DISPLAY_NAME IS '显示名';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.PANEL_ID IS '面板ID';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.VALUE_FIELD IS '取值字段';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.AGGREGATION IS '聚合方式';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.OPERATOR IS '比较运算符';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.THRESHOLD_VALUE IS '阈值';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.THRESHOLD_VALUE2 IS '阈值上限';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.REARM_SECONDS IS '冷静期秒数';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.NOTIFY_CHANNELS IS '通知渠道';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.RECIPIENTS IS '收件人';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.CRON_EXPR IS 'cron表达式';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.PARAMS IS '查询参数';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.TEMPLATE_KEY IS '模板键';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.STATUS IS '规则状态';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_alert_rule.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_report_delivery IS '报告交付历史';
                
      COMMENT ON COLUMN nop_datav_report_delivery.DELIVERY_ID IS '交付ID';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.REPORT_TASK_ID IS '报告任务ID';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.STATUS IS '交付状态';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.GENERATED_FILE_RECORD_ID IS '生成文件记录ID';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.DELIVERED_CHANNELS IS '已送达渠道';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.ROW_COUNT IS '导出行数';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.ERROR_MSG IS '错误信息';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.TRIGGERED_BY IS '触发来源';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.START_TIME IS '开始时间';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.END_TIME IS '结束时间';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_report_delivery.REMARK IS '备注';
                    
      COMMENT ON TABLE nop_datav_alert_state IS '告警状态';
                
      COMMENT ON COLUMN nop_datav_alert_state.ALERT_STATE_ID IS '状态ID';
                    
      COMMENT ON COLUMN nop_datav_alert_state.ALERT_RULE_ID IS '告警规则ID';
                    
      COMMENT ON COLUMN nop_datav_alert_state.STATE IS '告警状态';
                    
      COMMENT ON COLUMN nop_datav_alert_state.LAST_EVAL_TIME IS '最后评估时间';
                    
      COMMENT ON COLUMN nop_datav_alert_state.LAST_TRIGGERED_TIME IS '最后触发时间';
                    
      COMMENT ON COLUMN nop_datav_alert_state.LAST_RESOLVED_TIME IS '最后恢复时间';
                    
      COMMENT ON COLUMN nop_datav_alert_state.LAST_NOTIFIED_TIME IS '最后通知时间';
                    
      COMMENT ON COLUMN nop_datav_alert_state.CONSECUTIVE_EVAL_COUNT IS '连续评估满足次数';
                    
      COMMENT ON COLUMN nop_datav_alert_state.ERROR_MSG IS '错误信息';
                    
      COMMENT ON COLUMN nop_datav_alert_state.DEL_FLAG IS '删除标记';
                    
      COMMENT ON COLUMN nop_datav_alert_state.VERSION IS '数据版本';
                    
      COMMENT ON COLUMN nop_datav_alert_state.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_datav_alert_state.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_datav_alert_state.UPDATED_BY IS '修改人';
                    
      COMMENT ON COLUMN nop_datav_alert_state.UPDATE_TIME IS '修改时间';
                    
      COMMENT ON COLUMN nop_datav_alert_state.REMARK IS '备注';
                    
