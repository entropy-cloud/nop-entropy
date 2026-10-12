# 2447 WI13 benchmark JPA stack upgrade

> Plan Status: active
> Last Reviewed: 2026-10-12
> Source: ai-dev/backlog/jdk25-springboot4-quarkus4-roadmap.md WI13；plan 2444（BOM 分歧裁决政策）
> Related: 2444/2445/2446（同 roadmap 前序 WI）

## Purpose

nop-benchmark-orm（JPA/SQL 框架对比基准，JMH）从 javax 时代的 spring 5.2.8 / spring-data-jpa 2.3.3 / hibernate 5.4.20 旧栈迁移到 SB4 提供的 jakarta 栈（Hibernate 7.4.5.Final + spring-data-jpa 4.x + spring-orm 7），源码 javax.persistence → jakarta.persistence，使对比面与平台新基线一致且可构建。

## Current Baseline

- 模块**不在 reactor**（nop-benchmark/pom.xml 的 module 注释行），实质 broken-in-place：`org.hibernate:hibernate-core`（旧 groupId）**无任何 BOM 管理**（Q4/SB4 只管 `org.hibernate.orm` 组），原样 pom 连 validate 都报 version missing（审查实测）；versionless spring-context/spring-orm 已被 BOM 链解析到 Spring 7.0.9 / spring-data 2026.0.1（spring-data-jpa 4.1.1）。另有三处独立缺陷：模块无 compiler `<proc>full</proc>` 配置（JDK23+ 默认禁 classpath 隐式注解处理，Lombok @Data 失效——姊妹模块 json/stream 均已配）；mybatis-plus 3.5.7 把 mybatis-spring 声明为 optional（3.3.2 时代传递可见，升级即丢）；`io.nop.log.core.LoggerConfigurator`（NopBoot.java）所需 nop-log-core 不在 nop-orm 传递路径（先于本升级即缺）。
- pom 现状：4 个 pin 属性——beetlsql 3.14.2-RELEASE（活，beetlsql dep 引用）、spring 5.2.8.RELEASE（**死属性**，零引用）、spring.data.jpa.version 2.3.3.RELEASE（**死属性**，dep 为 versionless）、hibernate 5.4.20.Final（仅 hibernate-entitymanager dep 引用）；另有 mybatis-plus 3.3.2、weed3 3.4.25 硬编码。
- javax.persistence 导入面 **4 文件**：jpa/SpringService.java + jpa/vo/JpaSysUser/JpaSysCustomer/JpaSysOrder.java。
- jpa 装配面 API 全部为 spring-orm 7 / spring-data-jpa 4 保留面：LocalContainerEntityManagerFactoryBean、HibernateJpaVendorAdapter、JpaTransactionManager、EnableJpaRepositories、JpaRepository/@Query/@Param、org.hibernate.jpa.HibernatePersistenceProvider（Hibernate 7 存在）。
- 版本选择（Central 实测校准）：mybatis-plus 选 3.5.7、beetlsql 选 3.34-RELEASE 为**保守钉**（审查已逐 API 验证编译兼容），非最新线（latest 3.5.17 / 3.40.2-RELEASE，未验证不追）；weed3 上游 2022-11 停更于 3.4.33（现钉 3.4.25 为 Java 8 字节码，JDK25 编译无障碍）。

## Goals

- 源码：4 文件 javax.persistence → jakarta.persistence。
- pom：死属性删除（spring.version、spring.data.jpa.version）；hibernate-entitymanager dep 删除（Hibernate 6+ 已并入 core，7.4 无此 artifact）；hibernate-core 模块内 pin 7.4.5.Final（分歧裁决，见 Rulings）；mybatis-plus 3.3.2 → 3.5.7；beetlsql → 3.34-RELEASE；weed3 保留 3.4.25 并注明停更。
- 模块 standalone 可 test-compile（`-f` 构建，reactor 状态保持注释不变）。

## Non-Goals

- 不重新加入 reactor（JMH 基准为按需运行工具，是否常态构建归 benchmark 治理另案）。
- 不跑 JMH 基准、不做性能对比（roadmap 交付面是栈一致 + 可构建）。
- 不动 Nop ORM 侧与其他 benchmark 模块。
- 不升级 weed3（上游停更，无可升级目标；保留原版本是唯一诚实选项）。

## Rulings

1. **hibernate-core：现代坐标 + 7.4.5.Final 模块内 pin**（审查实证修正）：`org.hibernate:hibernate-core` 旧坐标无任何 BOM 管理（Q4/SB4 只管 `org.hibernate.orm` 组），原样 pom validate 即失败——现状缺陷首先是 groupId 过时。改用现代坐标 `org.hibernate.orm:hibernate-core`：BOM 链分歧裁决器给出 Q4 8.0.0.Beta3（beta）vs SB4 7.4.5.Final。胜者 beta 对基准对比面有害，且 roadmap WI13 明文 "SB4 provides Hibernate 7.4.5; no Hibernate 8 GA exists"。按 WI10 政策"分歧胜者有害时显式钉"，pin 落本模块 pom。
1b. **jakarta.persistence-api 模块内 pin 3.2.0（GA）**：BOM 链胜者 Q4 4.0.0-M7（milestone）。hibernate-core 7.4.5 自身声明依赖 3.2.0——钉 3.2.0 使本模块 JPA API 与 Hibernate 7.4.5 自配对齐、全 GA。政策依据同 Ruling 1（milestone 胜者对基准面有害）。
1c. **编译三缺陷修复属本 WI defect 面**：验证门是全模块编译，三项缺陷（proc/full、mybatis-spring optional、nop-log-core 缺失）任意一项都拦门；其中 nop-log-core 缺失先于本升级存在，但按"验证门可达"原则纳入本计划（Fix 类）。
2. **hibernate-entitymanager 删除而非升版**：该 artifact 停在 Hibernate 5.x，6.x 起并入 hibernate-core，7.4 无对应坐标。
3. **reactor 状态不变**：roadmap 未要求重新入 reactor；standalone `-f` 构建 = 验证门。
4. **weed3 保留 3.4.25（偏离 roadmap 字面）**：roadmap 字面要求 weed3 也升到 "consistent with the new BOMs"，但上游 2022-11 停更于 3.4.33，无可升级目标；保留现版并注释停更事实，是对 roadmap 字面的显式偏离裁定。

## Execution Plan

### Phase 1 - 栈迁移

Status: completed
Targets: nop-benchmark/nop-benchmark-orm（pom + 3 java）

- Item Types: `Fix`（旧栈迁移 + 死属性清理）

- [x] 4 文件 javax.persistence → jakarta.persistence（SpringService + 3 VO）
- [x] pom：死属性 spring.version / spring.data.jpa.version 删除；hibernate-entitymanager dep 删除；hibernate-core 改 org.hibernate.orm:hibernate-core 7.4.5.Final pin（Ruling 1）；jakarta.persistence-api 3.2.0 pin（Ruling 1b）；mybatis-plus 3.5.7；beetlsql 3.34-RELEASE；weed3 停更注释（Ruling 4）
- [x] pom 补编译三缺陷：maven-compiler-plugin `<proc>full</proc>`（对齐 nop-benchmark-json/stream 写法）；org.mybatis:mybatis-spring 2.1.2；io.github.entropy-cloud:nop-log-core（无版本，nop-bom 管理）

Exit Criteria:

- [x] 全模块 `javax.persistence` 活引用零（4 文件全迁移）；pom 无死属性、无 entitymanager、无旧 groupId org.hibernate:hibernate-core
- [x] `./mvnw test-compile -f nop-benchmark/nop-benchmark-orm/pom.xml` BUILD SUCCESS（standalone，parent/BOM 链经本地 repo）
- [x] No owner-doc update required（模块不在 reactor，无平台契约面）
- [x] No new test required: 基准工具模块迁移，test-compile 即验证面（模块零 JUnit 测试）
- [x] `ai-dev/logs/2026/10-12.md` WI13 条目

### Phase 2 - 收口

Status: planned
Targets: roadmap、plan、log

- Item Types: `Proof`

- [ ] roadmap WI13 `todo` → `done`
- [ ] 白名单提交（nop-benchmark-orm pom+4 java、plan 2447、log、roadmap）
- [ ] 独立子 agent closure audit
- [ ] 三门：check-plan-checklist --strict / scan-hollow-implementations --module nop-benchmark --severity high / check-doc-links --strict
- [ ] 文本一致性核对后置 `Plan Status: completed`

Exit Criteria:

- [ ] roadmap done；commit hash 记录 Closure；审计证据写入；三门留痕

## Closure Gates

- [ ] 所有 in-scope confirmed live defects 已修复（broken-in-place 旧栈 = 本 WI defect 面，已迁移）
- [ ] 所有 in-scope confirmed contract drifts 已收敛（源码 namespace = 依赖栈 = BOM 基线一致）
- [ ] 行为/契约结果已达成：模块 standalone test-compile SUCCESS 于 Hibernate 7.4.5 + spring-data-jpa 4 栈
- [ ] 必要 focused verification 已完成（standalone test-compile 即本 WI 验证面）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [ ] 受影响的 owner docs 已同步（No owner-doc update required 已裁定）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：迁移真实生效（grep 零 javax.persistence + 实跑 test-compile），非注释式假迁移
- [ ] 构建门：standalone test-compile SUCCESS（Gate A 全量构建不适用——模块不在 reactor；最近一次全量 Gate A = WI12 的 398 SUCCESS 可作平台基线引用）
- [ ] Gate B：不适用（模块零测试，test-compile 即验证面）
- [ ] 代码规范：import 分组符合仓内惯例

## Deferred But Adjudicated

（无——weed3 停更保留是 Non-Goals 显式裁定，非 deferred）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: （收口时填写）
Completed: （收口时填写）

Closure Audit Evidence:

- Reviewer / Agent: （收口时填写）
- Evidence: （收口时填写）

Follow-up:

- 见 Non-Blocking Follow-ups（无）
