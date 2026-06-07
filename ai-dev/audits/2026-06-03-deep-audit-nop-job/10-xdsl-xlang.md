# 维度 10：XDSL 与 XLang 正确性

## 第 1 轮（初审）

### 结论：XDSL 文件合规，无发现

1. **x:schema 引用**: 所有 xbiz/xmeta/beans/orm 文件均指向正确的 xdef 定义。
2. **x:extends 与 x:override**: 手写文件正确 extends 生成的 _* 文件；app.beans.xml 正确 import _*.beans.xml；worker Delta 使用 x:extends="super"。
3. **bean 定义与 Java 类路径**: 所有 beans.xml 中的 class 属性与实际 Java 类全限定名一致。
4. **xbiz 方法与 BizModel 签名**: _*.xbiz 使用 biz-gen:DefaultBizGenExtends 自动生成，方法名与 BizModel 的 @BizMutation 注解一致。
