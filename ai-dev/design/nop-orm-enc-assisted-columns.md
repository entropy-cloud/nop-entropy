# nop-orm 可检索加密契约（enc 列辅助查询列）

**日期**：2026-10-05
**范围**：`nop-persistence/nop-orm`、`nop-persistence/nop-orm-eql`、`nop-persistence/nop-orm-model`（声明校验）
**状态**：active
**灵感来源**：ShardingSphere encrypt 三列模型（cipher/assistedQuery/likeQuery）的 nop 化；执行计划见 `ai-dev/plans/2318-orm-enc-assisted-query-columns.md`

---

## 一、设计结论

1. **声明走扩展属性 + 角色标签，不改 xdef**：主列（`tagSet` 含 `enc`）用 `ext:enc-assisted="<辅助列属性名>"`、`ext:enc-like="<辅助列属性名>"` 指向同实体内声明的真实 VARCHAR 列；辅助列用 `tagSet` 角色标签 `enc-assisted` / `enc-like` 标记身份。依据既有 namespaced 扩展属性保留机制（先例：`ext:dict`、`ext:auto-upgrade-database` 经 `prop_get` 读取），零 codegen 链路改动。
2. **assisted 列 = keyed HMAC-SHA256 十六进制摘要（定长 64 字符）**：确定性、不可逆；密钥取 `nop.crypt.enc-assisted-key`（空则回落 `nop.crypt.default-enc-key`）。等值/IN/不等值谓词经它检索。
3. **like 列 = 定长逐字符 keyed 摘要拼接**：每字符 `HMAC-SHA256(key, salt || c)` 截断为定宽 hex（`nop.orm.enc-like-digest-width` 缺省 8 字符）顺序拼接。LIKE/NOT LIKE/ILIKE 谓词经它检索，`%`/`_` 通配语义精确保留。
4. **改写语义矩阵**（编译期改列、执行期变参）：`=, IN, !=, NOT IN` → assisted 列 + 参数摘要；`LIKE/NOT LIKE` → like 列 + pattern 摘要串；`ILIKE` → like 列 + 两侧小写规范化后摘要（普通 LIKE，不依赖方言 ILIKE 函数）；**范围谓词/ORDER BY/GROUP BY/JOIN ON 涉及 enc 主列一律 `ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE` 显式失败**。
5. **辅助列框架派生、用户只读**：flush 挂点统一派生（主列 null → 辅助列 null；主列变更 → 重算）；用户代码对辅助列显式赋值 → `ERR_ORM_ENC_HELPER_COLUMN_READONLY`。存量数据用回填组件（幂等、分批）。
6. **bulk UPDATE 边界（既定用户裁定，2026-10-05）**：`updateByExample` 等 bulk UPDATE SET 涉及 enc 主列时框架不做辅助列维护（不重算、不报错），由业务层校验负责；owner doc 显式声明该限制。

## 二、背景与动机

`enc` 标签透明列加密已生产可用（`EncodedDataParameterBinder`，AES/GCM v1 随机 IV），但随机 IV 使密文不确定——加密列等值检索失效（`TestColumnEnhancer` 已注释钉住该预期）。ShardingSphere 用辅助列解决"加密后可检索"：cipher 列保持强加密，确定性摘要存辅助列供查询。nop 的等价物可以更薄：nop 有列级标签元模型与 binder/编译期改写两个挂点，不需要 SS 的 JDBC 层 SQL 改写管线。

## 三、核心设计

### 3.1 声明契约（模型层）

```xml
<column name="email" code="EMAIL" tagSet="enc"
        ext:enc-assisted="emailAssisted" ext:enc-like="emailLike"
        stdSqlType="VARCHAR" precision="256"/>
<!-- 辅助列是普通模型列（参与 DDL/升级），角色由标签标记 -->
<column name="emailAssisted" code="EMAIL_ASSISTED" tagSet="enc-assisted"
        stdSqlType="VARCHAR" precision="64"/>
<column name="emailLike" code="EMAIL_LIKE" tagSet="enc-like"
        stdSqlType="VARCHAR" precision="2048"/>
```

模型加载校验（启动 fail-fast，错误码进 `OrmErrors`）：主列必须含 `enc` 标签；被指向的辅助列必须存在于同一实体、VARCHAR、非主键、非惰性；`enc-assisted`/`enc-like` 标签列必须被某个主列指向（孤立角色标签报错）；一个辅助列只能被一个主列指向；主列不得同时是辅助列；ext 指向不存在的属性名报错。

### 3.2 值契约

| | assisted 列 | like 列 |
|---|---|---|
| 函数 | `hex(HMAC-SHA256(key, normalize(v)))` 定长 64 | `concat_i hex_8(HMAC-SHA256(key, salt ‖ c_i))`（逐字符，长度 = 8×len(v)） |
| 规范化 | `String.valueOf(v).trim()`，不折叠大小写 | ILIKE 场景两侧先 `trim + toLowerCase` 再逐字符摘要 |
| 密钥 | `nop.crypt.enc-assisted-key`，空回落 `nop.crypt.default-enc-key` | 同左；字符盐取 `nop.crypt.default-salt` |
| 可逆性 | 不可逆 | 不可逆（宽度可配 4–16 hex；8 hex 下跨字符碰撞概率 ≈ 1e-6 量级，属已声明的残余风险） |

长度泄漏是 like 列的已知代价（列长 ∝ 明文长），文档向使用者声明；不愿泄露长度的场景只用 assisted（放弃模糊检索）。

### 3.3 查询改写语义

编译期完成列替换（EQL 路径经 before-analyze AST transformer，含谓词形态识别与参数 marker 注入；GenSqlHelper 路径在 SQL 构建点直接替换列与参数值），执行期完成参数摘要（自定义 param builder / 构建期摘要）。

| 主列上的谓词 | 改写 |
|---|---|
| `x = ?` / `x IN (...)` | `assisted = digest(?)` / `assisted IN (digest(?1),...)` |
| `x != ?` / `x NOT IN (...)` | `assisted != digest(?)` / `assisted NOT IN (...)`（确定性摘要保持语义等价） |
| `x LIKE 'p'` / `NOT LIKE` | `likeCol LIKE digestPattern(p)` / `NOT LIKE`；`%`→`%`、`_`→`_`（定宽 chunk 下单字符通配语义保持）、字面字符→其摘要 |
| `x ILIKE 'p'` | like 列 + 两侧小写规范化摘要 + 普通 `LIKE`（不依赖方言 ILIKE 注册） |
| `<,>,<=,>=,BETWEEN`、`ORDER BY/GROUP BY/JOIN ON` 涉及主列、含 `escape` 的 LIKE、正则字符类 `[..]` | `ERR_ORM_ENC_COLUMN_NOT_SEARCHABLE`（消息含实体.列名与建议做法） |

辅助列本身允许出现在用户查询中（读到的是摘要，非敏感数据）；xmeta 指引将辅助列标 `not-pub` 缺省不发布到 GraphQL。

### 3.4 写路径与回填

- 派生挂点在 flush 链路统一单点（与审计列自动填充同点）：用户经公开 setter 触碰辅助列 → 保存时报 `ERR_ORM_ENC_HELPER_COLUMN_READONLY`；框架派生用不置 dirty 的内部写（对齐租户列填充模式）。
- 回填组件按 id 分批（`nop.orm.enc-backfill-batch-size` 缺省 500）：读 id+主列 → 解密 → 派生 → 仅更新辅助列；幂等（重算覆盖）；主列为 null 的行跳过。

### 3.5 约束边界

- 不改变既有 `enc` 列的密文格式与读写解密行为（`EncodedDataParameterBinder` 原样）。
- 未声明辅助列的 enc 列行为完全不变（范围谓词等本就无检索语义，改写只在声明了对应辅助列时发生；未声明辅助列的 enc 列上做等值查询维持现状——随机 IV 下命中不了，不额外报错，文档说明应声明辅助列或使用 legacy 确定性模式）。
- ORM 层不脱敏、不做可逆辅助列（scope 边界见 `nop-orm-scope-and-non-goals.md`）。

## 四、拒绝了什么

| 被拒方案 | 拒绝理由 |
|---|---|
| like 列存全值摘要，仅支持 `LIKE '全值%'` 形态 | contains（`%x%`，最常用形态）语义不可用，功能面不成立 |
| like 列存 n-gram 摘要集合（`LIKE '%x%'` → 逐 gram 成员判断） | 前缀/后缀锚点语义静默变超集（`abc%` 误命中含 abc 于中部的行）——静默错误结果，违反平台无静默错误原则 |
| 照抄 SS `CHAR_DIGEST_LIKE` 算法 | 上游 master 已整体删除该算法、无维护基线；本设计的逐字符定宽摘要与其思路同族但契约自定义（宽度可配、通配语义显式定义） |
| assisted 列存可逆加密（确定性 AES 固定 IV） | 辅助列只需等值性不需可逆性；HMAC 摘要不泄露明文结构，安全性严格更优 |
| 大小写不敏感等值（assisted 侧 `ignoreCase`） | 需要第二套规范化规则与第二列；v1 以"ILIKE 走 like 列小写规范化"覆盖该需求 |
| 策略配置进 orm.xml 一级属性 | 与分片策略同判定：声明是模型事实，但辅助列是普通列已显式建模，无需新属性面（ext 属性即声明通道） |
| bulk UPDATE 自动重算辅助列 | 用户裁定归业务层校验（2026-10-05）；框架义务收窄为 owner doc 声明 |

## 五、与已有设计的关系

- 承接 `nop-orm-scope-and-non-goals.md`：不触碰其任何裁定；本设计的"无静默错误"取向（不可检索谓词显式失败）与其 §3.6 一致。
- 与 `nop-orm-sharding-strategy-design.md` 构成"编译期改列 / 执行期路由"的对照样本；两者共享"fail-fast 优先于静默兜底"取向。
- 与 `nop-dao-readwrite-splitting-design.md` 无耦合；组合 E2E 由 roadmap 收口项承载。
- 实施契约锚点：列标签 `TAG_ENC`（既有）、扩展属性读取（`prop_get` 先例）、flush 派生挂点（审计列同点）、EQL 编译期 transformer 扩展点与 marker-first 参数 builder 分流（TenantParamBuilder 先例）。
