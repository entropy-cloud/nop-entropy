
class NopDatavDashboard{

  String dashboardId; //看板ID

  String dashboardName; //看板名

  String displayName; //显示名

  String description; //描述

  Integer dashboardType; //看板类型

  Integer publishStatus; //发布状态

  Long publishedVersion; //已发布版本

  String publishedBy; //发布人

  Timestamp publishedTime; //发布时间

  String layoutConfig; //布局配置

  Byte delFlag; //删除标记

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopDatavPanel{

  String panelId; //面板ID

  String dashboardId; //看板ID

  String panelName; //面板名

  String displayName; //显示名

  Integer panelType; //面板类型

  String datasetRefId; //数据集引用ID

  String tabId; //页签ID

  Integer sortOrder; //排序

  String panelConfig; //面板配置

  Byte delFlag; //删除标记

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopDatavDashboard dashboard; //看板

}

class NopDatavDashboardTab{

  String tabId; //页签ID

  String dashboardId; //看板ID

  String tabName; //页签名

  String displayName; //显示名

  Integer sortOrder; //排序

  String tabConfig; //页签配置

  Byte delFlag; //删除标记

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopDatavDashboard dashboard; //看板

}

class NopDatavDatasetRef{

  String datasetRefId; //数据集引用ID

  String dashboardId; //看板ID

  String refDatasetId; //引用数据集ID

  String refDatasetName; //引用数据集名

  String paramMapping; //参数映射

  Byte delFlag; //删除标记

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopDatavDashboard dashboard; //看板

}

class NopDatavDashboardSnapshot{

  String snapshotId; //快照ID

  String dashboardId; //看板ID

  Long snapshotVersion; //快照版本

  String snapshotContent; //快照内容

  String publishedBy; //发布人

  Timestamp publishedTime; //发布时间

  Byte delFlag; //删除标记

  Long version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopDatavDashboard dashboard; //看板

}
