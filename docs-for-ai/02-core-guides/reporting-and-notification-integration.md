# 报表与通知集成默认路线

## 适用范围

本页回答外部 Nop 应用项目中一个高频问题：当业务 owner doc 已经明确需要报表输出、邮件、短信或类似通知能力时，默认应该如何落位。

本页只描述默认路线，不替代应用项目本地 requirement / design / architecture owner docs。

## 默认判断顺序

1. 先确认应用项目本地 requirement 和 design 是否已经定义了业务事实。
2. 再决定它属于报表能力还是通知能力。
3. 报表默认优先走 `nop-report`。
4. 邮件、短信等外发通道默认优先走 `nop-integration`。
5. 不要在应用项目里先发明一套平行的报表引擎或通道适配层，除非本地 owner doc 明确要求偏离平台默认路线。

## 报表能力默认路线

当业务问题是：

- 后台经营统计
- 数据集驱动的报表视图
- 按模板导出结果文件
- 定时报送或批量生成报表结果

默认优先使用 `nop-report`。

应用项目本地 owner doc 应回答：

- 谁看报表
- 报表回答什么业务问题
- 报表口径和状态语义是什么
- 支持哪些筛选维度和周期

应用项目不应在 design owner doc 中展开：

- 报表数据集实体细节
- 报表模板存储结构
- 结果文件技术格式细节
- 报表引擎内部实现

这些属于 `nop-report` 和本地 architecture / implementation 的落位问题。

## 通知能力默认路线

当业务问题是：

- 支付成功通知
- 发货提醒
- 退款结果通知
- 运营提醒
- 邮件、短信等外发消息

默认优先使用 `nop-integration` 提供的通道能力。

应用项目本地 owner doc 应回答：

- 什么业务事件触发通知
- 谁接收通知
- 通知表达的业务结果是什么
- 通知失败时对业务事实有无影响

应用项目不应在 design owner doc 中展开：

- 短信供应商 SDK 细节
- 邮件发送器实现细节
- 重试任务和失败队列实现
- 通道适配器内部协议

这些属于 `nop-integration` 和本地 architecture / implementation 的落位问题。

## 设计与架构的边界

应用项目本地 design owner-doc：

- 负责业务语义
- 说明谁收到什么通知、看什么报表、这些能力服务什么业务目标

应用项目本地 architecture / implementation owner-doc：

- 负责技术路线
- 说明为什么用 `nop-report` 或 `nop-integration`
- 说明模块、适配、模板、调度、导出、通道配置等实现策略

模型 / 生成契约：

- 仍以应用项目本地 `model/*.orm.xml`、`model/*.api.xml` 或平台模块模型为准

## 不要这样做

- 因为需要一个导出文件，就先手写一套脱离 `nop-report` 的本地报表框架
- 因为需要发短信或邮件，就直接在业务代码里散落供应商 SDK 调用作为默认模式
- 把通道实现细节写进应用项目 design owner doc
- 把 `nop-report` / `nop-integration` 的平台实现细节当成业务 requirement

## 实现锚点

- 报表模型与页面：见 `nop-report` 相关实体和页面
- 短信接口：`io.nop.integration.api.sms.ISmsSender`
- 短信消息：`io.nop.integration.api.sms.SmsMessage`
- 邮件接口：`io.nop.integration.api.email.IEmailSender`

## 相关文档

- `../INDEX.md`
- `./application-project-docs-and-domain-design.md`
- `./external-app-development.md`
- `./service-layer.md`
- `../04-reference/source-anchors.md`
