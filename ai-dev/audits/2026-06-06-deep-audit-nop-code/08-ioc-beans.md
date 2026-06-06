# 维度 08：IoC 与 Bean 配置

## 零发现

检查范围：6个 beans.xml 文件、8处字段注入、5处 setter 注入、全模块 Java 源文件。

- 所有 @Inject 字段使用 protected（生产代码）或包可见（测试代码）
- 无 @Autowired/@Value/@Component/@Service 误用
- 手写 app-service.beans.xml 语法正确
- 生成文件未被手写篡改
- _module 文件为空（标准 VFS 注册惯例）
- bean 引用的 class 均存在且匹配
