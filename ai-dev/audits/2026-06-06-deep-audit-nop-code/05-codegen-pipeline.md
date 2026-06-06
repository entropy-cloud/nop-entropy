# 维度 05：生成管线完整性

## 第 1 轮（初审）

### [维度05-01] _service.beans.xml 时间戳滞后于源模型

- **文件**: `nop-code/nop-code-service/src/main/resources/_vfs/nop/code/beans/_service.beans.xml:1-89`
- **证据片段**:
```
源模型 model/nop-code.orm.xml 时间戳: 2026-06-06 08:04
生成产物 _service.beans.xml 时间戳: 2026-05-26 23:29
时间差: 约11天
```
- **严重程度**: P2
- **现状**: `_service.beans.xml` 的修改时间为 2026-05-26，而源模型为 2026-06-06，相差约 11 天。内容包含全部 11 个实体的 BizModel 注册，与源模型一致，暂无功能影响。
- **风险**: 如果源模型新增了实体但未重新运行 codegen，`_service.beans.xml` 将缺少新实体的 BizModel 注册。
- **建议**: 每次修改 `model/nop-code.orm.xml` 后，必须执行 `./mvnw clean install -pl nop-code/nop-code-codegen -am` 重新生成全链路产物。
- **信心水平**: 确定
- **误报排除**: 已验证文件内容（11 个 BizModel bean）与源模型（11 个 entity）完全匹配。
- **复核状态**: 未复核

### [维度05-02] IBiz 接口时间戳滞后于源模型

- **文件**: `nop-code/nop-code-dao/src/main/java/io/nop/code/biz/INopCodeIndexBiz.java:1`（及同目录其余 10 个 IBiz 接口）
- **证据片段**:
```
INopCodeIndexBiz.java 时间戳: 2026-06-01 22:18
源模型时间戳: 2026-06-06 08:04（差约5天）
```
- **严重程度**: P2
- **现状**: 所有 11 个 `INopCode*Biz.java` 接口的时间戳均早于源模型最后修改时间。与 [05-01] 同源——codegen 管线未被完整重新执行。当前内容匹配。
- **风险**: 如果源模型对实体增加了新关系/列影响 IBiz 签名，旧的接口将不包含最新签名。
- **建议**: 与 [05-01] 同——每次修改源模型后重新运行 codegen 全链路。
- **信心水平**: 很可能
- **误报排除**: 与 [05-01] 同根，反映管线执行纪律问题。
- **复核状态**: 未复核

### 生成管线完整性确认

从 `model/nop-code.orm.xml` 到最终 web 页面的完整生成链路已全部闭合。11 个实体在每一层都有对应的生成产物。Delta 定制机制正确运作。pom.xml Maven 插件配置正确。i18n 生成正确。

| 检查项 | 数量 | 状态 |
|--------|------|------|
| 源模型实体 | 11 | OK |
| _gen/ Java 实体 | 11 | 匹配 |
| IBiz 接口 | 11 | 匹配 |
| XMeta | 11 | 匹配 |
| XBiz | 11 | 匹配 |
| View XML | 11 | 匹配 |
| Page YAML | 15 | 匹配 |
| Service Beans | 11 | 匹配 |
| i18n | 4 | 匹配 |
