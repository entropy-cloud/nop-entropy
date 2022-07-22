# nop-entropy

#### 介绍
Entropy Platform 2.0是基于可逆计算理论实现的低代码开发平台。nop-entropy是它的后端部分，采用java语言实现，不依赖spring框架。

#### 设计原理
[可逆计算：下一代软件构造理论](https://zhuanlan.zhihu.com/p/64004026)

[可逆计算的技术实现](https://zhuanlan.zhihu.com/p/163852896)

[从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

[低代码平台需要什么样的ORM引擎？](https://zhuanlan.zhihu.com/p/543252423)

#### 软件架构
包含如下模块:
1. nop-api-support: API接口的支持类
2. nop-core: 虚拟文件系统，反射系统，基本的树、图、表格模型，数据驱动的代码生成器框架，XNode(通用的Tree结构)和JSON解析器
3. nop-xlang: XPL模板语言，类TypeScript的脚本语言，类XPath的通用Tree路径访问语言, 类XSLT的通用Tree变换语言，XDefinition元模型定义语言，XDelta差量合并运算。
4. nop-ioc: 依赖注入容器
5. nop-config: 配置管理
6. nop-dao: SQL管理、事务、JDBC访问、数据库方言
7. nop-orm: 支持EQL对象查询语言的ORM引擎
8. nop-office: Excel和Word模板文件的解析和生成
9. nop-graphsql: GraphQL语言解析器和执行引擎
10. nop-biz: 业务流引擎
11. nop-view: 视图层模型
12. nop-web: Web框架

nop-entropy没有使用spring框架，所有模块均从零开始采用模型驱动的方式研发（框架本身的很多代码也是根据模型生成并可以通过声明式方式进行定制的）。

#### 安装教程

1.  xxxx
2.  xxxx
3.  xxxx

#### 使用说明

1.  xxxx
2.  xxxx
3.  xxxx

#### 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request

