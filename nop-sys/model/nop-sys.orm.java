
class NopSysSequence{

  String seqName; //名称

  String seqType; //类型

  Byte isUuid; //是否UUID

  Long nextValue; //下一个值

  Integer stepSize; //步长

  Integer cacheSize; //缓存个数

  Long maxValue; //最大值

  Integer resetType; //重置方式

  Byte delFlag; //删除标识

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysDict{

  String sid; //主键

  String dictName; //字典名

  String displayName; //显示名

  Byte delFlag; //删除标识

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  Set<NopSysDictOption> dictOptions; //

}

class NopSysDictOption{

  String sid; //主键

  String dictId; //字典ID

  String label; //显示名

  String value; //值

  String codeValue; //内部编码

  String groupName; //分组名

  Byte isInternal; //是否内部

  Byte isDeprecated; //是否已废弃

  Byte delFlag; //删除标识

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  NopSysDict dict; //字典

}

class NopSysI18n{

  String i18nKey; //字符串Key

  String i18nLocale; //语言

  String value; //值

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysCheckerRecord{

  String sid; //主键

  String bizObjName; //业务对象名

  String bizObjId; //业务对象ID

  String makerId; //请求发起人ID

  String makerName; //请求发起人

  String requestAction; //请求操作

  String requestData; //请求数据

  LocalDateTime requestTime; //请求时间

  String checkerId; //审批人ID

  String checkerName; //审批人

  LocalDateTime checkTime; //审批时间

  String tryResult; //请求结果

  String inputPage; //输入页面

  Integer status; //审批状态

  String cancelAction; //取消方法

  String cbErrCode; //回调错误码

  String ceErrMsg; //回调错误消息

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysCodeRule{

  String sid; //主键

  String name; //名称

  String displayName; //显示名称

  String codePattern; //编码模式

  String seqName; //序列号名称

  Byte delFlag; //删除标识

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysNoticeTemplate{

  String sid; //主键

  String name; //名称

  String tplType; //模板类型

  String content; //模板内容

  Byte delFlag; //删除标识

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

  Set<NopSysExtField> extFields; //

}

class NopSysUserVariable{

  String userId; //用户ID

  String varName; //变量名

  String varValue; //变量值

  String stdDomain; //变量域

  String varType; //变量类型

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysVariable{

  String varName; //变量名

  String varValue; //变量值

  String stdDomain; //变量域

  String varType; //变量类型

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysExtField{

  String entityName; //实体名

  String entityId; //实体ID

  String fieldName; //字段名

  Integer fieldType; //字段类型

  Byte decimalScale; //浮点精度

  BigDecimal decimalValue; //浮点值

  LocalDate dateValue; //日期值

  Timestamp timestampValue; //时间点值

  String stringValue; //字符串值

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  String remark; //备注

}

class NopSysLock{

  String lockName; //锁名称

  String lockGroup; //分组

  Timestamp lockTime; //锁定时间

  Timestamp expireAt; //过期时间

  String lockReason; //锁定原因

  String holderId; //锁的持有者

  String holderAdder; //持有者地址

  String appId; //应用ID

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

}

class NopSysClusterLeader{

  String clusterId; //集群ID

  String leaderId; //主服务器ID

  String leaderAdder; //主服务器地址

  Long leaderEpoch; //选举世代

  Timestamp electTime; //选举时间

  Timestamp expireAt; //过期时间

  Timestamp refreshTime; //刷新时间

  Integer version; //修改版本

  String appName; //应用名

}

class NopSysEvent{

  Long eventId; //事件ID

  String eventTopic; //事件主题

  String eventName; //事件名称

  String eventHeaders; //事件元数据

  String eventData; //数据

  String selection; //字段选择

  Timestamp eventTime; //事件时间

  Integer eventStatus; //事件状态

  Timestamp processTime; //处理时间

  Timestamp scheduleTime; //调度时间

  Boolean isBroadcast; //是否广播

  String bizObjName; //业务对象名

  String bizKey; //业务标识

  LocalDate bizDate; //业务日期

  Integer partitionIndex; //数据分区

  Integer retryTimes; //重试次数

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

}

class NopSysServiceInstance{

  String instanceId; //服务实例ID

  String serviceName; //服务名

  String clusterName; //集群名

  String groupName; //分组名

  String tagsText; //标签

  String serverAddr; //服务地址

  Integer serverPort; //服务端口

  Integer weight; //权重

  String metaData; //扩展数据

  Boolean isHealthy; //是否健康

  Boolean isEnabled; //是否启用

  Boolean isEphemeral; //是否临时

  Integer version; //数据版本

  Timestamp createTime; //创建时间

  Timestamp updateTime; //修改时间

}

class NopSysChangeLog{

  String sid; //主键

  String bizObjName; //业务对象

  String objId; //对象ID

  String bizKey; //业务键

  String operationName; //业务操作

  String propName; //属性名

  String oldValue; //旧值

  String newValue; //新值

  Timestamp changeTime; //变更时间

  String appId; //应用ID

  String operatorId; //操作人

  String approverId; //审核人

}

class NopSysTag{

  Long sid; //主键

  String name; //名称

  String description; //描述

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

}

class NopSysObjTag{

  String bizObjId; //对象ID

  String bizObjName; //对象名

  Long tagId; //标签ID

  Integer version; //数据版本

  String createdBy; //创建人

  Timestamp createTime; //创建时间

  String updatedBy; //修改人

  Timestamp updateTime; //修改时间

  NopSysTag tag; //标签

}
