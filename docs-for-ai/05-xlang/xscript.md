# XScript脚本语言

## 概述

XScript是Nop平台的脚本语言，基于JavaScript/TypeScript语法，提供了丰富的内置函数、数据查询、对象操作、数据库访问等能力。XScript可以在XPL模板中使用，也可以在BizModel方法中直接调用。

**位置**：BizModel方法中直接使用，或在XPL模板的`<c:script>`节点中使用

**核心特性**：
- JavaScript/TypeScript语法：易于学习和使用
- 与Java深度集成：数据库访问、缓存、事务等
- 编译期表达式：通过`#{expr}`形式表示编译期执行的宏
- 支持调用XPL标签：通过`xpl()`函数在脚本中调用XPL标签
- 扩展方法：可以为Java对象注册扩展方法，如`str.$capitalize()`

## 与JavaScript的区别

XScript简化了JavaScript中一些复杂的特性：

- **去除了类定义**：没有`class`和`prototype`相关部分
- **只允许使用Java类型**：不能新建类型，只能使用Java中已存在的类型
- **只使用null**：不使用`undefined`
- **去除了异步语法**：没有`generator`和`async`语法
- **修改了import语法**：仅支持导入类和标签库，**不支持通配符**
- **严格相等**：去除了`==`，只使用`===`，禁止类型转换
- **变量声明**：只使用`let`声明变量，不使用`var`
- **集合创建**：Map和List直接用`{}`和`[]`创建，类似JavaScript语法

## 基本语法

 ### 1. 变量定义

**重要**：XScript 只使用 `let` 声明变量，不使用 `var`。Map 和 List 直接用 `{}` 和 `[]` 创建，类似 JavaScript 语法。

```javascript
// 字符串变量
let userName = "张三";

// 数字变量
let age = 25;

// 布尔变量
let isActive = true;
let isAdmin = false;

// 对象变量（Map）
let user = {
    id: "001",
    name: "张三",
    email: "zhangsan@example.com"
};

// 数组变量（List）
let users = [
    {id: "001", name: "张三", status: 1},
    {id: "002", name: "李四", status: 1}
];

// 空集合
let roles = [];
let permissions = {};

// ❌ 错误用法
let userName = "张三";      // 不要使用 var
let list = new ArrayList();  // 使用 [] 即可
let map = new HashMap();      // 使用 {} 即可
```

### 2. 条件语句

```javascript
// if-else
if (isActive) {
    userName = "正常用户";
} else {
    userName = "禁用用户";
}

// switch-case
switch (status) {
    case 1:
        userName = "正常";
        break;
    case 2:
        userName = "已禁用";
        break;
    case 3:
        userName = "已锁定";
        break;
    default:
        userName = "未知状态";
}

// 逻辑运算
if (age >= 18 && age <= 60) {
    isAdult = true;
}
```

### 3. 循环语句

```javascript
// for循环
for (let i = 0; i < users.length; i++) {
    let user = users[i];
    console.log(user.name);
}

// for-in循环（遍历数组元素）
for (let user of users) {
    console.log(user.name);
}

// for-in循环（遍历对象属性）
for (let key in user) {
    console.log(key + ": " + user[key]);
}

// while循环
let i = 0;
while (i < 10) {
    i++;
    console.log("计数：" + i);
}
```

### 4. 函数定义

```javascript
// 定义函数
function calculatePrice(item) {
    return item.price * item.quantity;
}

function isEmail(str) {
    return /^[^\s@]+@\.[^\s@]+\.[^\s@]+$/.test(str);
}

function formatDate(date) {
    return StringHelper.format(date, 'yyyy-MM-dd');
}

// 带参数函数
function applyDiscount(total, discount) {
    if (total > 1000 && discount > 0) {
        total = total * (1 - discount / 100);
    }
    return total;
}

// 箭头函数
let calculatePrice = (item) => item.price * item.quantity;
```

## 全局变量

XScript提供了一些常用的全局变量，所有变量名都以`$`开头：

| 变量名       | 描述                                  |
|--------------|-------------------------------------|
| `$context`    | 对应于ContextProvider.currentContext() |
| `$scope`      | 当前运行时的IEvalScope                 |
| `$out`        | 当前运行时的IEvalOutput                |
| `$beanProvider`| 当前运行时的IEvalScope所关联的IBeanProvider |
| `$`           | 对应于Guard类                          |
| `$JSON`       | 对应于JsonTool类                       |
| `$Math`       | 对应于MathHelper类                     |
| `$String`     | 对应于StringHelper类                   |
| `$Date`       | 对应于DateHelper类                     |
| `_`           | 对应于Underscore类                     |
 | `$config`     | 对应于AppConfig类                      |
 
 ## Import 语句

XScript 支持 `import` 语句导入 Java 类和标签库，但有一些限制：

### 导入规则

```javascript
// ✅ 正确：导入具体的类
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdsl.DslModelHelper;
import io.nop.auth.dao.entity.NopAuthResource;

// ❌ 错误：不支持通配符
import java.util.*;  // 编译错误
import io.nop.core.resource.*;  // 编译错误

// ✅ 正确：导入多个类时，每个类单独导入
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdsl.DslModelHelper;
```

### 导入 XPL 标签库

```javascript
// 在 XPL 模板中导入标签库
<c:import from="/nop/core/core.xlib"/>
<c:import from="/test/my.xlib"/>

// 使用导入的标签库
<c:if test="${x < 10}">小于10</c:if>
<my:HelloTag name="World" xpl:lib="/test/my.xlib"/>
```

### 注意事项

1. **不支持通配符**：必须指定具体的类名
2. **导入顺序**：建议按类型分组导入（java.* → 第三方 → io.nop.*）
3. **重复导入**：可以导入多次，不会报错
4. **作用域**：导入在整个脚本文件中有效
 
 ## 编译期表达式

编译期表达式在编译时执行，结果会成为抽象语法树的一部分：

```javascript
// 编译期执行的表达式
let x = #{ a.f(3) };

// 编译期调用XPL标签
let y = xpl('my:MyTag', {a:1, b:x+3});
```

## 调用XPL标签

XScript可以通过`xpl()`函数调用XPL标签，支持三种调用形式：

```javascript
// 形式1：模板字符串形式
result = xpl `<my:MyTag a='${1}' b='${x+3}' />`;

// 形式2：对象参数形式
result = xpl('my:MyTag', {a:1, b:x+3});

// 形式3：位置参数形式
result = xpl('my:MyTag', 1, x+3);
```

## 内置对象和函数

### 1. StringHelper字符串工具

```javascript
// 基本判断
StringHelper.isEmpty(str)
StringHelper.isBlank(str)
StringHelper.isNotEmpty(str)
StringHelper.isEmail(str)

// 基本操作
StringHelper.contains(str, subStr)
StringHelper.startsWith(str, prefix)
StringHelper.endsWith(str, suffix)
StringHelper.substring(str, start, end)
StringHelper.replace(str, oldStr, newStr)
StringHelper.toLowerCase(str)
StringHelper.toUpperCase(str)
StringHelper.trim(str)

// 格式化
StringHelper.format(pattern, ...args...)
StringHelper.join(list, separator)

// 扩展方法
str.$capitalize()
str.$firstPart('.')
str.$lastPart('.')
str.$lowerCase()
str.$upperCase()
```

### 2. DateHelper日期工具

```javascript
// 日期操作
DateHelper.today()
DateHelper.addDays(date, days)
DateHelper.addMonths(date, months)
DateHelper.addYears(date, years)

// 格式化
DateHelper.format(date, pattern)
DateHelper.parse(str, pattern)

// 判断
DateHelper.isDate(date)
DateHelper.isBefore(date1, date2)
DateHelper.isAfter(date1, date2)
DateHelper.dateDiff(date1, date2)
```

### 3. MathHelper数学工具

```javascript
// 数学运算
MathHelper.abs(num)
MathHelper.max(a, b)
MathHelper.min(a, b)
MathHelper.add(a, b)
MathHelper.subtract(a, b)
MathHelper.multiply(a, b)
MathHelper.divide(a, b)

// 四舍五入
MathHelper.round(num, scale)
MathHelper.ceil(num)
MathHelper.floor(num)

// 随机数
MathHelper.randomInt(min, max)
MathHelper.random()
```

### 4. JsonTool JSON工具

```javascript
// JSON转换
JsonTool.parse(jsonStr)
JsonTool.stringify(obj, pretty)
JsonTool.toJson(obj)
JsonTool.toPrettyJson(obj)
JsonTool.parseObject(jsonStr, type)
JsonTool.toJsonObject(obj)
JsonTool.toMap(json)
JsonTool.parseArray(jsonStr)
```

### 5. BeanTool Bean工具

```javascript
// 属性操作
BeanTool.getProperty(bean, propName)
BeanTool.setProperty(bean, propName, value)
BeanTool.copyProperties(src, dest)
BeanTool.merge(target, sources)
BeanTool.deepCopy(obj)
BeanTool.clone(obj)
```

## 数据库访问

### 1. IEntityDao 使用

```javascript
// 获取DAO
let userDao = dao();
let userId = "001";
let user = userDao.getEntityById(userId);

// 查询
let users = userDao.findAllByExample(
    User.setStatus(1)
);

// 保存
userDao.saveEntity(user);

// 删除
userDao.deleteEntity(user);

// 批量操作
let userIds = ["001", "002", "003"];
let users = userDao.batchGetEntitiesByIds(userIds);
```

### 2. QueryBean 查询构建

```javascript
// 构建查询
let query = new QueryBean();
query.setFilter(FilterBeans.eq("status", 1));
query.setOrderField("createTime", false);

// 执行查询
let page = userDao.findPageByQuery(query, 0, 20);
```

### 3. FilterBeans 过滤条件构建

```javascript
// 基本查询
let filter1 = FilterBeans.eq("status", 1);
let filter2 = FilterBeans.ge("createTime", "2024-01-01");
let filter3 = FilterBeans.contains("userName", "张");
let filter4 = FilterBeans.in("id", ["001", "002"]);

// 复合条件
let filter5 = FilterBeans.and(filter1, filter2);
let filter6 = FilterBeans.or(filter3, filter4);

// 设置过滤条件
query.setFilter(filter5);
```

## 事务管理

### 1. 声明式事务

```javascript
// 在BizModel中使用事务
@Transactional
public void updateUser(String userId, String newStatus) {
    txn(() -> {
        let user = dao().requireEntityById(userId);
        user.setStatus(newStatus);
        dao().saveEntity(user);
    });
}
```

### 2. 编程式事务

```javascript
// 使用ITransactionTemplate
@Inject
let txnTemplate;

public void transferOrder(String fromId, String toId) {
    // 主事务
    txnTemplate.runInTransaction(null, TransactionPropagation.REQUIRED, txn -> {
        let fromOrder = dao().requireEntityById(fromId);
        let toOrder = dao().requireEntityById(toId);

        // 更新订单状态
        fromOrder.setStatus("TRANSFERRED");
        toOrder.setStatus("PENDING");
        dao().saveEntity(fromOrder);
        dao().saveEntity(toOrder);

        // 记录转移记录
        let record = new TransferRecord();
        record.setFromId(fromId);
        record.setToId(toId);
        transferDao.saveEntity(record);
    });
}
```

### 3. 事务传播

```javascript
// REQUIRED：默认，加入当前事务，无则新建
@Transactional(propagation = Propagation.REQUIRED)

// REQUIRES_NEW：总是新建事务
@Transactional(propagation = Propagation.REQUIRES_NEW)

// MANDATORY：必须在现有事务中执行，否则抛异常
@Transactional(propagation = Propagation.MANDATORY)

// NOT_SUPPORTED：以非事务方式执行
@Transactional(propagation = Propagation.NOT_SUPPORTED)
```

## 实际应用

### 1. 计算订单金额

```javascript
public void calculateOrderPrice(String orderId) {
    let order = dao().requireEntityById(orderId);
    let total = 0;

    // 计算订单项金额
    for (let i = 0; i < order.items.length; i++) {
        let item = order.items[i];
        total = total + item.price * item.quantity;

        // 应用折扣
        if (order.coupon) {
            total = applyDiscount(total, order.coupon.discount);
        }
    }

    order.setTotalAmount(total);
    dao().saveEntity(order);
}
```

### 2. 用户状态判断

```javascript
public String getUserStatusLabel(Integer status) {
    switch (status) {
        case 1:
            return "正常";
        case 2:
            return "已禁用";
        case 3:
            return "已锁定";
        default:
            return "未知状态";
    }
}
```

### 3. 复杂条件查询

```javascript
public List<User> searchUsers(String keyword, Integer status) {
    let query = new QueryBean();

    // 添加过滤条件
    if (StringHelper.isNotEmpty(keyword)) {
        query.addFilter(FilterBeans.or(
            FilterBeans.contains("userName", keyword),
            FilterBeans.contains("email", keyword)
        ));
    }
    if (status != null) {
        query.addFilter(FilterBeans.eq("status", status));
    }

    return dao().findAllByQuery(query);
}
```

### 4. 订单处理流程

```javascript
public void processOrder(String orderId) {
    txn(() -> {
        let order = dao().requireEntityById(orderId);

        // 验证订单状态
        if (order.status != 0) {
            throw new NopException("订单状态不正确");
        }

        // 获取订单项
        let items = orderService.getItems(orderId);

        // 检查库存
        let stockMap = inventoryService.batchCheckStock(
            items.map(item -> item.productId).collect(Collectors.toList())
        );

        // 扣除所有库存是否足够
        let allStockEnough = stockMap.values().stream().allMatch(stock -> stock > 0);
        if (!allStockEnough) {
            throw new NopException("库存不足");
        }

        // 扣减库存
        inventoryService.batchReduceStock(
            items.map(item -> item.productId).collect(Collectors.toList())
        );

        // 更新订单状态
        order.setStatus(2); // 处理中
        dao().saveEntity(order);

        // 记录处理日志
        log.info("订单" + orderId + "处理开始");
    });
}
```

## 最佳实践

### 1. 错误处理

```javascript
// 使用try-catch
try {
    // 可能失败的代码
    userDao.saveEntity(user);
} catch (e) {
    log.error("保存用户失败: " + userId, e);

    // 包装异常
    throw new NopException("保存用户失败", e)
        .param("userId", userId)
        .param("userName", user.userName);
}
```

### 2. 日志记录

```javascript
// 使用日志记录重要操作
log.info("用户" + userId + "登录成功");
log.warn("用户" + userId + "权限不足");
log.error("处理订单" + orderId + "失败");
```

### 3. 参数验证

```javascript
// 参数非空检查
if (StringHelper.isEmpty(userId)) {
    throw new NopException("用户ID不能为空");
}

// 邮箱验证
if (!StringHelper.isEmail(email)) {
    throw new NopException("邮箱格式不正确");
}

// 长度检查
if (userName.length > 50) {
    throw new NopException("用户名长度不能超过50");
}
```

### 4. 性能优化

```javascript
// 使用分页查询，避免一次性加载大量数据
let page = userDao.findPageByQuery(query, 0, 20);

// 使用批量操作
let items = dao().batchGetEntitiesByIds(page.items.map(item => item.id));

// 使用缓存
let user = userDao.getEntityById(userId); // 内置缓存
```

### 5. 类型安全

```javascript
// 虽然XScript是动态类型语言，但尽量保持类型一致性
let userName = "张三"; // 字符串
let age = 25;          // 数字
let isActive = true;   // 布尔
```

## 注意事项

### 1. 事务边界
- 事务边界尽可能小
- 避免在事务中执行耗时操作（IO、网络调用等）
- 合理使用事务传播级别

### 2. 异常处理
- 不要吞掉异常，抛出有意义的异常信息
- 使用NopException统一异常
- 记录详细的错误日志

### 3. 并发控制
- 注意并发事务和数据一致性问题
- 使用乐观锁或悲观锁控制并发
- 批量操作考虑使用批量方法

### 4. 资源管理
- 使用try-with-resources确保资源释放
- 避免内存泄漏

### 5. 性能优化
- 合理使用分页和查询字段选择
- 使用批量操作减少数据库往返
- 考虑添加索引优化查询性能
