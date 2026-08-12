# 15: optional 依赖 + 编译期类型引用 = 启动崩溃；SPI 多实现应经 ioc:collect-beans 自动发现

> Date: 2026-08-13
> Severity: High — nop-app-erp 应用启动崩溃根因：`MfaStoreProvider` 编译期 `import io.nop.nosql.core.INosqlService` + `nop-nosql-core` 声明为 `<optional>` 依赖 → 未引入 nosql 的应用在 IoC 反射 bean 方法签名时抛 `NoClassDefFoundError`，Quarkus 启动失败

## 场景

nop-app-erp 应用启动崩溃（`Failed to start quarkus`），栈底：

```
Caused by: java.lang.NoClassDefFoundError: io/nop/nosql/core/INosqlService
    at java.base/java.lang.Class.getDeclaredMethods0(Native Method)
    at io.nop.ioc.loader.BeanDefinitionBuilder.initFactoryBeans(BeanDefinitionBuilder.java:261)
```

根因链：

1. `nop-auth-service` 新增 MFA 存储：`MfaStoreProvider.java:20` **编译期直接 `import io.nop.nosql.core.INosqlService`**（setter 方法签名 `setNosqlService(INosqlService)`）。
2. `nop-auth-service/pom.xml` 把 `nop-nosql-core` 声明为 **`<optional>true</optional>`**——意图是"使用方按需引入"。
3. 但 Nop IoC 容器在 `BeanDefinitionBuilder.initFactoryBeans` 阶段**反射 bean 类的全部 declared methods**（含 setter 签名），`INosqlService` 不在 classpath → `NoClassDefFoundError` 在**反射期**抛出——即使应用不启用 Redis、不启用 MFA，只要该 bean 类被扫描就崩。
4. "optional 依赖 + 类签名引用"组合使"可选能力不引入也能跑"的意图完全失效。

## 根因

1. **optional Maven 依赖 ≠ 可选的编译期类型引用**：`<optional>true</optional>` 只影响传递依赖，不影响本模块编译；本模块代码一旦在类签名中引用 optional 类型，所有消费者（无论是否需要该能力）在类加载/反射时都会撞上缺失的类。
2. **Nop IoC 反射期脆弱点**：`BeanDefinitionBuilder.initFactoryBeans` 会 `getDeclaredMethods()` 反射 bean 类全部方法——方法签名中的任何缺失类型都会导致类加载失败，与运行时是否使用无关。
3. **手工 new 实现 + Provider 硬编码分支**（`MfaStoreProvider.getMfaChallengeStore()` 里 `if (isRedis()) return new RedisMfaChallengeStore(...)`）把"实现发现"写死成编译期依赖，而不是交给容器装配。

## 正确做法

**SPI 多实现应用 `ioc:collect-beans` 按 bean id 前缀收集**（`beans.xdef`：`<ioc:collect-beans name-prefix="..." as-map="true"/>`）——这是平台既有命名型扩展点的标准模式：

```xml
<!-- 消费者：按前缀收集所有已注册实现，Map 键 = bean id 去掉前缀的后缀 -->
<ioc:collect-beans name-prefix="nopMfaChallengeStore" as-map="true"/>

<!-- 实现者各自注册 bean（条件注册），id 遵循 nop 前缀强约定（code-style.md §IoC Bean 命名） -->
<bean id="nopMfaChallengeStore_db" class="...DbMfaChallengeStore"/>      <!-- store-type=db/默认 -->
<bean id="nopMfaChallengeStore_redis" class="...RedisMfaChallengeStore"/> <!-- 独立 beans.xml + 条件激活 -->
```

要点：

1. **前缀收集是首选**（`BeanDefinitionBuilder:897-905`）：`name-prefix` 纯字符串匹配 bean id（`id.startsWith(prefix)`），**不加载任何类**——从机制上杜绝类加载崩溃；`by-type` 需 `loadBeanClass`（`:892`）加载类型，optional 依赖场景下仍有风险。
2. **bean 命名遵循平台强约定**（`docs-for-ai/02-core-guides/code-style.md:110-114`）：平台内置 bean 以 `nop` 为前缀（强约定非硬性保留规则）；业务自定义 bean 避免复用 `nop*`。**平台命名型扩展点先例**：`nopJobInvoker_`（`nop-job.md:264`，前缀+executorKind 后缀）、`nopCodeRuleVariable_`（`generate-business-code.md:104`，平台经 `ioc:collect-beans` 自动收集）——前缀收集是这些扩展点的统一装配机制。
3. **接口与实现解耦**：消费者不 import 具体实现类，只依赖接口 + 前缀约定——新增实现零消费者改动（注册新 bean 即被自动收集）。
4. **optional 依赖的实现类必须条件注册**：Redis 实现放独立 beans.xml（或 `ioc:if` 条件），仅在对应 store-type 且依赖存在时激活——classpath 无 nosql 时该 bean 根本不加载，类签名零引用。
5. **新功能默认值应走无外部依赖的路径**：MFA 存储默认 `store-type=db`（数据库实现，零外部依赖、多实例共享、持久化），Redis 为显式可选——「缺省不使用 Redis；所有存储必须有基于数据库的实现」。
6. **回归守门**：验证"classpath 不含 optional 依赖时应用可启动"必须成为该类修改的 Exit Criteria（直接复现崩溃场景）。

## 判定规则

> **optional Maven 依赖的模块代码禁止在类签名（方法参数/返回/字段类型）中直接引用该依赖类型。** 若确实提供可选实现：实现类与装配隔离（独立 beans.xml + 条件激活），消费者只依赖接口，经 `ioc:collect-beans` **按 id 前缀（name-prefix，纯字符串匹配不加载类）**自动收集。
>
> 凡是"某依赖标 optional/可选"的新功能，Exit Criteria 必须含一条：**去掉该依赖时应用仍可启动/相关功能仍可用**（复现类加载场景）。

## 适用范围

- 平台可选能力（nosql/Redis、外部 SDK、驱动）的封装与装配
- 新增 SPI 多实现（store/Provider/Strategy 族）——**优先 name-prefix 收集，非 by-type**
- Maven `<optional>` 依赖的引入与消费
- 应用启动 `NoClassDefFoundError`/`ClassNotFoundException` 排查

## 参考

- `nop-auth/nop-auth-service/src/main/java/io/nop/auth/service/mfa/MfaStoreProvider.java`（:20 import、:59 setter、:87-95 requireNosql——修复前）
- `nop-kernel/nop-xdefs/src/main/resources/_vfs/nop/schema/beans.xdef`（:45-53 `BeanCollectBeansValue`：name-prefix/by-type/by-annotation/tag/as-map）
- `nop-core-framework/nop-ioc/src/main/java/io/nop/ioc/loader/BeanDefinitionBuilder.java`（:892 by-type 需 loadBeanClass；:897-905 name-prefix 纯字符串匹配 `id.startsWith(prefix)`，Map 键=去前缀后缀）
- `docs-for-ai/02-core-guides/code-style.md`（:110-114 §IoC Bean 命名：平台内置 bean `nop` 前缀为强约定；业务 bean 避免 `nop*`；测试 bean `test`/`testMock`）
- `docs-for-ai/03-modules/nop-job.md`（:264 `nopJobInvoker_` 前缀+executorKind 后缀）与 `docs-for-ai/03-runbooks/generate-business-code.md`（:104 `nopCodeRuleVariable_` 前缀经 `ioc:collect-beans` 自动收集）——前缀收集是平台命名型扩展点的统一装配机制
- `docs-for-ai/04-reference/source-anchors.md`（IOC-003：平台内置 bean 广泛 `nop*` 命名是仓库强约定，非 IoC 保留前缀规则）
- `nop-auth/nop-auth-service/pom.xml`（`nop-nosql-core` `<optional>true</optional>`）
- 计划：`ai-dev/plans/2026-08-13-0900-1-mfa-db-store-and-default-db.md`（Phase 3 类加载崩溃修复 = collect-beans name-prefix 收集 + 条件注册）
- 相关教训：08（校验函数存在≠接线——同理，类存在≠可加载）
