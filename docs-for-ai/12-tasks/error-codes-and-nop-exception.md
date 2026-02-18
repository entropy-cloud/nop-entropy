# 错误码与 NopException（业务错误的标准写法）

## 适用场景

- 参数校验失败、业务规则冲突、权限不足等需要返回稳定错误码

## AI 决策提示

- ✅ 业务错误：用 `NopException` + ErrorCode，并通过 `.param(...)` 附带上下文参数
- ❌ 避免：直接抛 `RuntimeException("中文消息")`

## 最小闭环

下面是一组来自仓库的真实写法（工作流模块），包含：

- 在 `*Errors` 接口里统一定义 `ErrorCode`
- 在业务代码里 `throw new NopException(ERR_XXX).param(...)` 传参

### 定义 ErrorCode（示例）

参考类：`io.nop.wf.service.NopWfErrors`

```java
package io.nop.wf.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopWfErrors {
    String ARG_ACTOR_TYPE = "actorType";
    String ARG_ACTOR_NAME = "actorName";
    String ARG_ACTOR_ID = "actorId";

    ErrorCode ERR_WF_NULL_ACTOR = define("nop.err.wf.null-actor", "参与者不允许为空");

    ErrorCode ERR_WF_ACTOR_NOT_USER = define("nop.err.wf.actor-not-user",
        "参与者[{actorName}]必须是用户类型，实际是:{actorType}", ARG_ACTOR_TYPE, ARG_ACTOR_NAME, ARG_ACTOR_ID);

    ErrorCode ERR_WF_UNKNOWN_ACTOR_TYPE =
        define("nop.err.wf.unknown-actor-type", "未知的参与者类型：{actorType}",
            ARG_ACTOR_TYPE, ARG_ACTOR_ID);
}
```

### 抛出 NopException 并传参（示例）

参考类：`io.nop.wf.service.actor.DaoWfActorResolver`

```java
import io.nop.api.core.exceptions.NopException;

import static io.nop.wf.service.NopWfErrors.ARG_ACTOR_ID;
import static io.nop.wf.service.NopWfErrors.ARG_ACTOR_NAME;
import static io.nop.wf.service.NopWfErrors.ARG_ACTOR_TYPE;
import static io.nop.wf.service.NopWfErrors.ERR_WF_ACTOR_NOT_USER;
import static io.nop.wf.service.NopWfErrors.ERR_WF_NULL_ACTOR;
import static io.nop.wf.service.NopWfErrors.ERR_WF_UNKNOWN_ACTOR_TYPE;

// ...

throw new NopException(ERR_WF_UNKNOWN_ACTOR_TYPE)
    .param(ARG_ACTOR_TYPE, actorType)
    .param(ARG_ACTOR_ID, actorId);

// ...

if (actor == null)
    throw new NopException(ERR_WF_NULL_ACTOR);

// ...

throw new NopException(ERR_WF_ACTOR_NOT_USER)
    .param(ARG_ACTOR_TYPE, actor.getActorType())
    .param(ARG_ACTOR_ID, actor.getActorId())
    .param(ARG_ACTOR_NAME, actor.getActorName());
```

## 相关类

- `io.nop.api.core.exceptions.ErrorCode`
- `io.nop.api.core.exceptions.NopException`
- 示例：`io.nop.wf.service.NopWfErrors`
- 示例：`io.nop.wf.service.actor.DaoWfActorResolver`
