# 为什么说百度AMIS框架是一个优秀的设计

[AMIS](https://aisuda.bce.baidu.com/amis/zh-CN/docs/index)是百度开源的一个前端低代码框架，公允的说，它是目前前端开源社区中设计最精良的低代码框架之一，代表了国内前端开源低代码领域的最高水平。AMIS采用JSON语法格式，内置了大量开箱即用的成熟组件，**可以很容易的被集成和扩展**。在实际使用层面，AMIS提供了较为丰富的在线文档，并且支持在线测试。根据我的观察，一般的后端开发人员通过自行摸索在较短的时间内就可以掌握AMIS的基本内容，上手搭建较为复杂的增删改查页面。

AMIS内置的组件主要是后台管理软件常用的组件，但是它的设计本身是一种通用设计，并不受限于目前它所支持的使用场景。在本文中，我将分析一下AMIS框架的一些设计要点，并介绍Nop平台中集成AMIS框架时所做的一些改进工作。

> AMIS的代码实现质量并不算高，特别是因为它的发展时间较长，导致内部实现中冗余代码较多，存在很大的可改进空间。最近一两年，百度投入了一定的资源对AMIS进行了比较大的代码重构，整体情况还在持续改进的过程中。本文主要是在概念设计层面所做的一些分析，不涉及到具体实现层面的代码。

## 一. Env: 环境抽象

AMIS与其他开源低代码框架的第一个显著的区别就是，它显式定义了[环境抽象Env](https://aisuda.bce.baidu.com/amis/zh-CN/docs/start/getting-started#%E6%8E%A7%E5%88%B6-amis-%E7%9A%84%E8%A1%8C%E4%B8%BA)，其中包含了输入、输出相关以及页面跳转相关的所有执行逻辑。这一设计极大降低了AMIS框架的集成难度，使得它可以很容易插入到其他的底层界面框架中。例如，在Nop平台中，我们使用Vue3.0开发主界面框架，通过Vue Router实现单页跳转，而在具体页面或者某个嵌入组件的实现上，我们可以使用基于React技术的AMIS来实现。

```javascript
 const env = {
      // 执行ajax请求
     fetcher(options) { ... },
     // 通过Router实现单页切换
     updateLocation(location,replace){ ... },
      // 打开新的浏览器窗口
     jumpTo(to,action){ ... },
      // 类似window.alert，弹出提示信息
     alert,
      // 类似window.confirm，弹出确认信息
     confirm,
     // 弹出通知消息和错误提示
     notify(type,msg)
 }
 // 根据json配置渲染得到页面
 renderAmis(
      // json格式的页面Schema描述
      jsonPage,
      // 传入额外的数据
      {
        data:{
          myVar: 123,
        }
      },
      // 传入环境对象
      env
    );
```

如果我们把AMIS的JSON页面定义看作是一种领域特定语言(DSL)，则**AMIS框架可以看作是负责解释执行这一DSL的虚拟机**。在AMIS内部需要执行AJAX调用或者弹出提示信息、跳转页面等操作的时候，它会调用env上的相应方法来实现，因此**env可以看作是具体负责执行输入、输出操作的某种虚拟化抽象接口**。

所有的输入、输出动作都被虚拟化之后，output = AmisPage(Input)，AMIS页面就可以被看作是某种影响范围受限的局部处理函数，很容易被编织到外部的业务处理流程中。

## 二. Api: 值与函数的对偶

对于远程服务调用的封装，AMIS框架提供了一个描述式的定义方式，即所谓的[Api对象](https://aisuda.bce.baidu.com/amis/zh-CN/docs/types/api)。

```typescript
interface ApiObject{
  /**
   * API 发送类型
   */
  method?: 'get' | 'post' | 'put' | 'delete' | 'patch' | 'jsonp';

  /**
   * API 发送目标地址
   */
  url: SchemaUrlPath;

  /**
   * 用来控制携带数据. 当key 为 `&` 值为 `$$` 时, 将所有原始数据打平设置到 data 中. 当值为 $$ 将所有原始数据赋值到对应的 key 中. 当值为 $ 打头时, 将变量值设置到 key 中.
   */
  data?: {
    [propName: string]: any;
  };

  /**
   * 发送体的格式
   */
  dataType?: 'json' | 'form-data' | 'form';

  /**
   * 如果是文件下载接口，请配置这个。
   */
  responseType?: 'blob';

  /**
   * 携带 headers，用法和 data 一样，可以用变量。
   */
  headers?: {
    [propName: string]: string | number;
  };

  /**
   * 设置发送条件。
   */
  sendOn?: SchemaExpression;

  /**
   * 默认都是追加模式，如果想完全替换把这个配置成 true
   */
  replaceData?: boolean;

  /**
   * 是否自动刷新，当 url 中的取值结果变化时，自动刷新数据。
   *
   * @default true
   */
  autoRefresh?: boolean;

  /**
   * 当开启自动刷新的时候，默认是 api 的 url 来自动跟踪变量变化的。
   * 如果你希望监控 url 外的变量，请配置 traceExpression。
   */
  trackExpression?: string;

  /**
   * 如果设置了值，同一个接口，相同参数，指定的时间（单位：ms）内请求将直接走缓存。
   */
  cache?: number;

  // 对提交到后台的请求数据进行格式变换
  requestAdaptor?: (api: ApiObject) => ApiObject;

  // 对后台返回的响应数据进行格式变换
  adaptor?: (payload: object, response: fetcherResult, api: ApiObject) => any;

  /**
   * 失败时不弹出提示信息
   */
  silent?: boolean

   /**
   * 当远程调用成功或者失败时，会到这里查找对应的消息对象，通过toast方式进行提示
   */
   messages?: { [name: string]: string }
}
```

Api对象是响应式的数据结构，当它的参数数据发生变化时会自动执行远程调用，返回的结果数据可以被转换及缓存。

传统上我们在前端编写代码一般是基于函数视角的，即事件触发后执行某个处理函数，如果要修改某个关联组件的值，我们就调用这个关联组件上的更新函数。例如:

```javascript
<select id="a" onchange="handleChange">
</select>
<select id="b">
</selection>

<script>
function handleChange(value){
    fetchRelatedOptions(value).then(result=>{
        getComponent('b').setOptions(result)
    })
}
</script>
```

在AMIS这种更加现代化的前端框架中，我们大多数时候是**采用数据视角**，即不把函数看作是事件发生时执行的动作，而**把函数看作是结果数据的某种动态产生器**，当某些条件发生的时候，结果数据会自动被更新，这个更新的过程就是被执行的函数。例如，在AMIS中实现两个下拉列表联动

```json
{
    "type": "form",
    "body":[
        {
           "type": "select",
           "name": "a",
           "options" : [ ...]
        },
        {
            "type": "select",
            "name": "b",
            "source": {
                "method" : "post",
                "url" : "/get-related-options",
                "data": {
                     "value": "${a}"
                 }
            }
        }
    ]
}
```

第一个下拉选择控件的name为a，表示它的选择值对应于上下文环境中的名称为a的变量，下拉选择控件可以看作是这一变量的查看器和修改器。另一个select控件的source属性对应于一个Api类型的对象，它通过数据绑定表达式监听了变量a的变化，当a发生变化的时候，会自动执行ajax调用获取到新的下拉选项列表。

如果我们**不只是关注单次事件触发的函数调用过程**，而是强调整体结构，**观察应用运行的完整过程**，我们会发现它的结构为 "...数据 --\>函数 --\> 数据 --\> 函数 --\> 数据 ..."。**这样一个信息传递网络具有两种对偶的观察视角。一种是"函数--\>数据--\>函数"，另一种是"数据--\> 函数--\> 数据"**。我们可以将信息流解释为函数调用其他函数，调用时传递函数参数，也可以解释为数据发生变化后触发响应函数产生新的数据。

> 具有副作用的void函数，本质上并不是没有返回值，而是改变了某些未被显式表达的状态变量。

如果对比一下Vue3.0的响应式设计，可以发现一些有意思的联系。AMIS的Api对象类似于Vue3.0中的ComputedRef类型，只是它触发的不是某个同步调用的前端函数，而是某个异步调用的远程服务。如果要在Vue3.0中实现类似Api对象的机制，我们可以对Vue做出如下扩展：

```javascript
interface Api<T> extends Ref<T>{
  /**
   * 构建Api时所使用的配置对象
   */
  readonly config: ApiConfig

  /**
   * 当前值，完全等价于vue的ref.value
   */
  readonly value: T | undefined

  /**
   * 是否输入参数已变，需要执行远程加载动作。如果当前处于active状态，则会自动发起加载动作.
   * 处于suspended状态时会记录shouldRefresh，但是不真正发起请求
   */
  readonly shouldRefresh: boolean

  /**
   * 是否远程数据加载动作正在执行过程中
   */
  readonly loading: boolean

  readonly loaded: boolean

  /**
   * 是否远程加载超时。超时的时候success设置为false
   */
  readonly timeouted: boolean

  /**
   * 是否已经成功获取值。如果请求超时，则设置的值是false
   */
  readonly success: boolean | undefined

  /**
   * 远程调用失败时的异常对象
   */
  readonly error: any

  suspend(): void

  resume(): void

  /**
   * 取消当前的加载操作
   */
  cancel(): void

  /**
   * 重新获取值. immediately表示跳过debounce设置。suspended状态下调用reload也会执行。
   */
  reload(immediately?: boolean): Promise<T|undefined>

  /**
   * 类似rxjs的接口。每次api的value变化时触发此函数
   */
  subscribe(
    next: (value: T) => void,
    error?: (err: any) => void,
    complete?: () => void
  ): StreamUnsubscribe

  /**
   * 应用转换函数后得到结果
   *
   * @param fn
   */
  transform<R>(fn: (v: T | undefined) => R): Ref<R>

    /**
     * 销毁本对象，它的值不再更新也不再有效
     */
    destroy(): void
}

interface ApiConfig {
  ... ApiObject中定义的属性和方法

  /**
   * 获取的值如何与此前的值进行合并，缺省情况下为替换，指定为append时作为数组合并
   */
  merger?: (v1: T | undefined, v2: T) => T

  /**
   * 在没有实际加载成功之前所设置的缺省值
   */
  defaultValue?: T

  /**
   * 大于0时表示定时去获取数据 ，单位为毫秒
   */
  pollInterval?: number

  /**
   * 缓存api的返回结果时所使用的key, 可以直接指定，也可以动态计算。如果不指定，则使用JSON.stringify(req)来作为key
   */
  cacheKey?: any | ((req: ApiRequest) => string)

  /**
   * 当值大于0时，会缓存获取的数据一段时间，此后如果参数值相同，则不会重复获取
   */
  cacheTimeout?: number

  cacheStorage?: string

  /**
   * 当值大于0时，后台请求超过指定时间后认为是超时，会自动取消调用并抛出超时异常
   */
  completeTimeout?: number

  connectTimeout?: number

  /**
   * 对于流式数据，一段时间内没有数据，认为超时
   */
  idleTimeout?: number

  /**
   * 当从后台成功获取到值时回调此函数
   */
  onSuccess?: (value: T) => void

  /**
   * 无论是超时、后台异常或者手工取消，只要是没有正常获取到值，就会回调此函数
   */
  onFailure?: (err: any) => void

  /**
   * 获取数据的过程中可能会返回进度信息
   */
  onProgress?: (progress: any) => void

  onStreamComplete?: () => void

  /**
   * 延迟处理的时间，单位为毫秒
   */
  debounce?: number

  /**
   * 限流等待时间，单位为毫秒。throttle和debounce的区别在于debounce的起始点会不断向后推移
   */
  throttle?: number

  /**
   * 是否是流式数据，不断返回值
   */
  stream?: boolean

  /**
   * 是否API创建后主动执行加载动作，缺省值为false。当设置为false时，只有当实际访问值的时候才触发加载动作。也即是第一次加载是否自动触发
   */
  eager?: boolean

  /**
   * 如果设置为true, 则创建后自动处于suspended状态，除非主动调用reload()动作否则不会自动执行数据加载
   */
  suspended?: boolean

  /**
   * 远程加载出错后执行的可选异常处理
   */
  fallback?: (err: any, api: Api<T>) => any

  /**
   * 加载出错时的重试次数，缺省为0
   */
  retries?: number

  retryDelay?: number

  maxRetryDelay?: number
}
```

如果不把Api看作是一种函数调用机制，而是看作一种响应式的数据流对象（Stream），则一些程序控制可以很自然的加入到这个概念中，例如debounce延迟触发、retry重试、pollInterval定时刷新，cancel/suspend控制等。作为Ref的一种扩展机制，除了具有value值以外，Api对象可以具有loading、status、error等属性，从而将远程调用过程的全部信息状态化。在这种情况下，Api对象的作用基本上就与React Hook中的[useSWR机制](https://juejin.cn/post/6943397563114455048)类似了。

AMIS的Api对象并没有直接提供流式数据支持，但是它提供了一种专用的[Service容器](https://aisuda.bce.baidu.com/amis/zh-CN/components/service)，可以起到类似的作用。

```javascript
 {
    "type": "service",
    "api": "/amis/api/mock2/page/initData",
    "body": {
      "type": "panel",
      "title": "$title",
      "body": "api返回的结果是一个Map，其中的属性date的值为 ${date}"
    }
  }
```

Service的接口定义为

```javascript
interface ServiceSchema{
  /**
   * 指定为 Service 数据拉取控件。
   */
  type: 'service';

  /**
   * 页面初始化的时候，可以设置一个 API 让其取拉取，发送数据会携带当前 data 数据（包含地址栏参数），获取得数据会合并到 data 中，供组件内使用。
   */
  api?: SchemaApi;

  /**
   * WebScocket 地址，用于实时获取数据
   */
  ws?: string;

  /**
   * 通过调用外部函数来获取数据
   */
  dataProvider?: ComposedDataProvider;

  /**
   * 内容区域
   */
  body?: SchemaCollection;

  /**
   * @deprecated 改成 api 的 sendOn。
   *    从这个设计变动可以看出AMIS中Api对象的规范化也是逐步进行的。
   */
  fetchOn?: SchemaExpression;

  /**
   * 是否默认就拉取？
   */
  initFetch?: boolean;

  /**
   * 是否默认就拉取？通过表达式来决定.
   *
   * @deprecated 改成 api 的 sendOn。
   */
  initFetchOn?: SchemaExpression;

  /**
   * 用来获取远程 Schema 的 api
   */
  schemaApi?: SchemaApi;

  /**
   * 是否默认加载 schemaApi
   */
  initFetchSchema?: boolean;

  /**
   * 用表达式来配置。
   * @deprecated 改成 api 的 sendOn。
   */
  initFetchSchemaOn?: SchemaExpression;

  /**
   * 是否轮询拉取
   */
  interval?: number;

  /**
   * 是否静默拉取
   */
  silentPolling?: boolean;

  /**
   * 关闭轮询的条件。
   */
  stopAutoRefreshWhen?: SchemaExpression;

  messages?: SchemaMessage;

  name?: SchemaName;
}
// ServiceStore中增加了fetching, error等状态变量信息。
const ServiceStore = iRendererStore
  .named('ServiceStore')
  .props({
    msg: '',
    error: false,
    fetching: false,
    saving: false,
    busying: false,
    checking: false,
    initializing: false,
    schema: types.optional(types.frozen(), null),
    schemaKey: ''
  })
```

Service支持轮询拉取数据或者通过websocket返回流式数据。

```javascript
{
  "type": "service",
  "ws": {
    "url": "ws://localhost:8777?name=${name}",
    "data": {
      "name": "${name}"
    }
  },
  "body": {
    "label": "名称",
    "type": "static",
    "name": "returnVar"
  }
}
```

可以只设置 ws，通过 ws 来获取所有数据，也可以同时设置 api 和 ws，让 api 用于获取全部数据，而 ws 用于获取实时更新的数据。

**Service拉取到的数据兄弟节点就可以读取到，在这个意义上说，把Service看作容器概念是一种历史遗留的错误，本质上它是一个更灵活组织的、支持流式数据的Api对象**。

> 除了用于动态获取数据之外，Service的另一个作用是可以动态获取页面片段定义，用返回的schema定义来生成页面，但是根据本文中的分析，获取data与获取schema的功能本质上是不相关的，最好是分离设计。

除了Api对象之外，AMIS还提供了一种轻量级的、类似Vue3.0中computed类型的动态计算机制：formula组件。

```javascript
      {
        "type": "input-text",
        "name": "b",
        "label": "B"
      },
      {
        "type": "formula",
        "name": "b",
        "formula": "''",
        "condition": "${radios}",
        "initSet": false
      }
```

以上的例子是formula的一个有趣应用。两个控件的名称都是b，但是formula控件带有一个条件，它表示当radios变量变化的时候，会执行公式，设置变量b的值为空。formula控件的作用相当于是radios选项变化时对变量b所执行的一种重置动作。

formula组件只能写嵌入表达式，无法进行更复杂的函数抽象。因为Api本质上是比computed更高阶的数据类型，所以我们在某种程度上也可以用Api来模拟computed机制。例如

```javascript
{
    url: "@action:myFunc",
    data: {
       a: "${a}" 
    }
}
```

`@action:myFunc`是Nop平台中基于AMIS的Env抽象对AMIS平台所作的一种扩展函数调用机制。上面的Api定义表示当变量a发生变化的时候会触发上下文环境中的myFunc函数，在myFunc中我们在前台执行一些处理逻辑，并不一定需要发起远程调用。

在1.4.0版本之后，Service容器可以通过dataProvider属性来设置数据加载函数，利用它也可以模拟部分模拟Vue中computed属性的功能。

```javascript
{
    "type": "service",
    "data": {
      "x": 123
    },
    "dataProvider": "const timer = setInterval(() => { setData({:data.x, date: new Date().toString()}) }, 1000); return () => { clearInterval(timer) }",
    "body": {
      "type": "tpl",
      "tpl": "现在是：${date},${x}"
    }
}
```

dataProvider函数中可以通过data属性来访问data参数数据，通过调用setData来更新store中的数据。

在一种理想的响应式前端框架中，我们可以期待绑定组件的属性值时，可以使用静态值，动态表达式，响应式Ref引用，或者具有状态跟踪功能的异步Api对象（流数据）。但是，目前在AMIS的实现中我们做不到这一点，所以select控件设置静态配置的选项列表时使用options属性，而如果需要动态获取选项列表时需要使用一个单独定义的source属性。这也意味着控件的哪些属性支持异步Api调用目前是需要逐个控件单独去实现的。

## 三. 数据链: 状态树与词法作用域

在Vue3.0之前，Vuex框架为vue组件提供了整个应用层面的单一状态树管理。

```javascript
const Counter = {
  template: `<div>{{ count }}</div>`,
  computed: {
    count () {
      return this.$store.state.count
    }
  }
}
```

从今天的角度上看，Vuex的设计明显过于曲折。当我们需要在组件中使用store中的某个变量的时候，为什么非要从this指针绕一下，把变量从store中拉下来，再包装为对象上的某个computed属性呢？为什么不可以直接使用store上的变量呢？

在Vue3.0中，[Pinia框架](https://pinia.web3doc.top/introduction.html)取代了Vuex的生态位，它的做法更加贴近人们的直觉。

```javascript
const store = useStore();
const { count } = storeToRefs(store)
// 可以直接使用store变量，也可以从store中解构出ref变量后再使用
return () => <div> {mainStore.count} --- {count} </div>
```

结合jsx语法，我们可以直接通过Javascript变量名访问到对应状态变量。**变量是按照Javascript中的词法作用域一层层向上查找**，如果存在多个同名的变量，则实际找到的是最近层级的变量。

AMIS中的所谓[数据链概念](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/datascope-and-datachain)，本质上就是由AMIS框架所负责维护的一组词法作用域。当我们在AMIS的DSL语法中为组件设置name属性时，它表示将本控件与当前数据域中的同名变量绑定在一起（双向数据绑定）。使用表达式语法时，表达式中的变量也是在数据域中进行解析。因此这里的所谓数据域可以看作是DSL语法中的词法作用域。AMIS中规定了如下查找规则：

1. 首先会先尝试在当前组件的数据域中寻找变量，当成功找到变量时，通过数据映射完成渲染，停止寻找过程；
2. 当在当前数据域中没有找到变量时，则向上寻找，在父组件的数据域中，重复步骤`1`和`2`；
3. 一直寻找，直到顶级节点，也就是`page`节点，寻找过程结束。
4. 但是如果 url 中有参数，还会继续向上查找这层，所以很多时候配置中可以直接 `${id}` 取地址栏参数。

Vuex中维护了所谓的单一状态树，将整个应用的状态变量作为一颗单根树进行管理，这是从Redux框架继承来的一个概念。但在实际使用过程中，往往我们并没有把树形结构的这种层级关系很好的利用起来。而在AMIS中，只有少数几个容器组件会创建新的数据域(除了最顶层的 Page，还有 CRUD、Dialog、IFrame、Form、Service 等)。当这些容器组件嵌套组合为页面的时候，会把自己的Store自动挂载到全局的StoreTree结构中，整个页面很自然的构成一颗StateTree。然后**借助于数据链查找机制**，我们在编写业务逻辑的时候**可以只使用最相关的局部变量名，从而使得逻辑表达得到简化**。例如，在表格控件的某个单元格中，数据域首先是数据行，可以直接通过变量名访问到其他列。

```json
{
    "type": "crud",
    "api": "/amis/api/mock2/sample",
    "columns": [
      {
        "name": "version",
        "label": "Engine version"
      },
      {
        "label": "Next Version",
        "type": "tpl",
        "tpl": "${version+1}"
      }
    ]
  }
```

AMIS中专门提供了一个[Combo组件](https://aisuda.bce.baidu.com/amis/zh-CN/components/form/combo)，用于充分利用对象数据的层次结构，实现复杂对象结构的编辑和展现。

为了更精细的控制数据链上的信息传递，AMIS提供了如下机制

1. canAccessSuperData属性可以控制不访问父级数据域中的数据

2. 通过重置data中的数据为\_\_undefined来覆盖父级数据域中的值。
   
   ```javascript
   {
    "type": "dialog",
    "title": "新增",
    "data": {
         "status": "__undefined"
    },
    ...
   }
   ```

## 四. 表单：验证与联动

表单字段模型相当于是在原有业务字段值的基础上，增加一些额外的功能，例如：

1. 字段标签label

2. 字段是否有效validated，是否正在验证validating

3. 字段是否必填required

4. 字段是否可见 visible

5. 字段录入控件是否失去焦点 focus/blur

6. 字段录入是否被禁用 disabled

7. 验证失败时显示的错误提示 error

8. 字段值是否已经被修改，修改前的值是什么

9. 字段提示文本remark，表单项描述desc等辅助录入的提示信息

AMIS和其他前端框架的做法类似，都是通过一个FormItem高阶包装控件来实现对普通控件的增强。

```javascript
class MyControl extends React.Component {
  render() {
    const {value, onChange} = this.props;

    // 可以通过 this.props.data 获取当前数据域中的所有的数据
    // 通过this.props.onBulkChange({a: 1, b: 2}) 来更新其他字段的值
    return (
      <div>
        <p>这个是个自定义组件</p>
        <p>当前值：{value}</p>
        <a
          className="btn btn-default"
          onClick={() => onChange(Math.round(Math.random() * 10000))}
        >
          随机修改
        </a>
      </div>
    );
  }
}

FormItem({
  type: 'my-control',
})(MyControl)
```

经过FormItem包装并注册到Renderer工厂中之后，在表单中就可以通过`type:"my-control"`来使用该控件并获取到label、remark等附加显示支持。

借助于AMIS强大的数据链管理和Api对象抽象，AMIS表单对验证和联动的支持非常直观和简单。例如，对于异步验证，只需要配置控件的validateApi属性，通过api的sendOn配置可以进一步控制验证发起条件。

```javascript
{
    "label": "email",
    "type": "input-text",
     "name": "email",
     "validateApi": "/amis/api/mock2/form/formitemSuccess",
     "required": true
}
```

AMIS内置了一个属性名称约定：所有类似visibleOn/requiredOn这样的满足xxxOn模式的属性都会作为表达式被执行，执行结果作为最终的visible/required等属性传递给组件。

借助于响应式的数据绑定表达式，无需编写事件监听函数，即可实现相当复杂的表单内控件联动。例如

```javascript
      {
        "name": "idIsNumber",
        "type": "switch",
        "label": "id 是数字类型"
      },
      {
        "name": "id",
        "type": "input-text",
        "label": "id",
        "visibleOn": "${!idIsNumber}",
        "validations": {
          "isEmail": true
        }
      },
      {
        "name": "id",
        "type": "input-number",
        "label": "id",
        "visibleOn": "${idIsNumber}",
        "validations": {
          "isNumeric": true
        }
      }
```

我们可以在表单中放置多个同名的控件，然后借助于visibleOn表达式进行条件切换，从而实现在不同的条件下执行不同的校验规则的功能。**在数据驱动的前端模型中，数据不再是从属于具体的控件，而是由与界面控件无关的store进行管理，因此多个控件可以共享同样的数据**。

> AMIS内置还识别另外一些后缀约定，例如valueExpr表示该字段是表达式类型，实际对应于value属性等。但是目前xxExpr后缀的表达方式应该已经被废弃，在AMIS的文档中没有相关的介绍。现在应该是倾向于直接识别是否存在前后缀来判断是否表达式语法，例如`value: "${a.b.c}"`会被自动识别为表达式。在Nop平台中我们一直贯彻[前缀引导语法](https://zhuanlan.zhihu.com/p/548314138)的设计思想，通过增加特定的值前缀来标识不同的值类型，从而可以保持对象整体结构不变，**将值级别的变化限定在值的表达范围内，而不用上升到对象级别**（不用增加新的属性）。按照这一设计思想，AMIS中visibleOn/disabledOn等约定也是多余的，完全可以改为全部使用表达式约定，但考虑到兼容性问题，AMIS大概不会再做这个修改。目前AMIS框架在很多地方缺少全局统一的处理机制，表达式识别和处理一般是下放给每个控件独立进行，所以在实际使用中可能存在隐秘的不一致的地方。

**AMIS继承并发扬了DOM模型的优秀设计：所有控件都具有name和id，可以按照name和id进行查找定位**。AMIS很巧妙的引入了所谓target的概念，将name直接作为描述式的定位坐标，从而进一步挖掘了name的价值。例如，借助于target，AMIS中可以实现跨表单的联动。

```javascript
 {
      "title": "查询条件",
      "type": "form",
      "target": "my_crud",
      "body": [
        {
          "type": "input-text",
          "name": "keywords",
          "label": "关键字："
        }
      ],
      "submitText": "搜索"
    },
    {
      "type": "crud",
      "name": "my_crud",
      "api": "/amis/api/mock2/sample",
      ...
    }
}    
```

通过设置form的target为其他控件的名称，可以将form表单的数据作为参数提交到指定控件。

另外一种常见用法是触发target控件上的reload方法，此时可以传递指定的参数

```javascript
  {
          "type": "action",
          "actionType": "reload",
          "label": "发送到 form2",
          "target": "form2?name=${name}&email=${email}"
 }
```

可以一次性刷新多个目标控件

```javascript
{
  "type": "action",
  "actionType": "reload",
  "label": "刷新目标组件",
  "target": "target1,target2"
}
```

AMIS对组件的reload语义进行了规范化，所以Formula组件、Service组件、Select组件等都支持reload语义，它们会执行某种缺省的刷新行为，比如Service组件会重新执行Api调用，Select组件会重新加载sourcce选项列表等。

一个很自然的扩展是支持调用目标组件上的任意方法，而不仅仅是缺省的reload方法。AMIS在1.7.0版本以后开始支持更灵活的组件方法调用机制：

```javascript
// 设置表单的值
{
    "actionType": "setValue",
    "componentId": "myForm",
    "args": {
       "value": "${globalData}"
    }
 },
// 切换tab页选项
{
     "actionType": "changeActiveKey",
     "componentId": "tabs-change-receiver",
     "args": {
        "activeKey": 2
      }
}
```

如果设计一种定位机制，最简单的要求应该是三点：

1. 绝对定位方式

2. 相对定位方式

3. 相对定位的复合方式

在AMIS中，id是用于定位的绝对坐标，getComponentById(id)总会先找到root节点，然后再从root节点一层层向下查找。name是用于定位的相对坐标，getComponentByName(name)是查找当前scope中具有指定名称的组件。**当找不到的时候, AMIS会调用parent.getComponentByName(name)继续查找,所以有可能找到其他分支上的节点**。 getComponentByName支持name属性的复合, a.b.c表示先按照name=a查找到组件，然后再在其中查找name=b的子组件，依此类推。

在AMIS最新的action触发设计中，componentId只支持id绝对定义，而不支持按照name进行相对定位，这应该是设计上的一种倒退。没有相对定位，在我们对多个页面进行组合的时候，很容易出现冲突的情况。如果使用完全无意义的uuid，则会难以支持手工编写，从DSL退化为可视化编辑器的附属物。

## 五. Action: 动作触发与编配

[Action行为按钮](https://aisuda.bce.baidu.com/amis/zh-CN/components/action)是触发页面行为的最常见的方式。AMIS中的Action组件和外部的容器组件相互配合，内置了大量前端页面模型相关的知识，有效减少了一般应用场景中的配置工作量。
首先，Action组件内置了常见的处理工作流：

1. 首先弹出confirmText提示，询问是否需要执行
2. 如果是在表单中，会检查required配置指定的字段是否通过校验
3. 检查requiredSelected批量操作条件是否满足
4. 执行操作，启动countDown禁止倒计时，
5. 如果配置了messages，则成功或者失败时会使用toast方式显示对应提示信息
6. 操作成功后如果配置了feedback窗口，则弹出反馈窗口，对返回结果进行展示
7. 操作成功后如果配置了redirect，则跳转到指定页面
8. 操作执行成功后根据reload配置刷新指定控件
9. 如果配置了close，且按钮在对话框中，则尝试关闭对话框。

Action触发采用的是类似事件冒泡的处理方式：**当本组件的handleAction函数不处理的时候，会调用父组件传入的onAction函数进行处理**。AMIS的表单控件和对话框控件都识别一些标准动作，例如reset/reload/submit/clear/confirm/cancel/close等，因此当事件传播到这些容器组件的时候会自动执行缺省行为。在下面的例子中，点击按钮A的时候会自动关闭当前页面所在的对话框，点击按钮B的时候会重置当前页面中按照缺省定位规则可以找到的表单

```javascript
{
    "type":"action",
    "label":"Button A",
    "actionType": "close"
},
{
    "type":"action",
    "label":"Button B",
    "actionType": "reset"
}
```

Action也可以用于触发对话框的弹出，基本调用结构为

```javascript
{
    "type":"action",
    "actionType":"dialog",
    "dialog": {
        "data": {
          // 通过data段向表单传递初始化数据，缺省情况下对话框继承了原页面的数据链，
          // 可以直接访问到原页面中的数据
        },
        "body":{
          // 对话框中的具体内容。在对话框中仍可以通过组件id等访问到原页面中的组件
        }
    }
}
```

例如

```javascript
{
  "type": "page",
  "body": {
    "type": "form",
    "body": [
      {
        "type": "input-text",
        "label": "data",
        "name": "myData",
        "id": "myData",
        "addOn": {
          "type": "action",
          "label": "设置",
          "actionType": "dialog",
          "dialog": {
            "actions": [],
            "data": {
              "myValue": "${myData}"
            },
            "body": {
              "type": "form",
              "body": [
                {
                  "type": "input-text",
                  "label": "myValue",
                  "name": "myValue"
                },
                {
                  "label": "修改",
                  "type": "action",
                  "close": true,
                  "onEvent": {
                    "click": {
                      "actions": [
                        {
                          "componentId": "myData",
                          "actionType": "setValue",
                          "args": {
                            "value": "${myValue}"
                          }
                        }
                      ]
                    }
                  }
                }
              ]
            }
          }
        }
      }
    ]
  }
}
```

上面的例子中演示了在一个输入框旁增加一个附加按钮\[设置\]，点击后弹出一个表单，在表单中录入信息后，点击表单中的按钮将把信息复制到下面的输入框中，并自动关闭对话框。

无论在框架中内置多么复杂的动作处理流，对于变换莫测的业务需求而言都是不充分的。AMIS在1.7.0版本之后增加了[事件动作](https://aisuda.bce.baidu.com/amis/zh-CN/docs/concepts/event-action)机制，允许在一次事件响应的过程中执行多个自定义的动作，而且**这些动作可以并行、串行、异步执行，可以执行循环和分支选择，具有前后依赖关系，相当于是实现了一个小型的逻辑流编配系统**。

事件动作的基本调用结构为：

```javascript
 "onEvent": {
    "click": { // 监听事件
      "actions": [ // 执行的动作列表
        {
          "actionType": "toast", // 执行toast提示动作
          "args": { // 动作参数
            "msgType": "info",
            "msg": "${__rendererData|json}"
          },
           "expression": "expression === \"okk\"" // 满足条件才会执行,
           "stopPropagation":false, // 是否阻止执行下一个action
           "preventDefault":false, // 是否阻止执行控件缺省的事件响应函数
           "outputVar": "" // 如果动作有返回结果，这里可以指定输出变量名
        },
        // 后续动作
      ]
    },
```

执行动作时，可以通过`${event.data}`获取事件对象的数据、通过`${__rendererData}`获取组件当前数据域。

AMIS提供了actionType=loop/break/continue/switch/parallel等多种流程控制指令。后续动作会自动等待前一个动作执行完毕后再执行。http 请求动作执行结束后，后面的动作可以通过 `${responseResult}`或`${{outputVar}}`来获取请求响应结果。

## 六. Nop平台中的扩展

Nop平台是基于可逆计算原理从零开始构建的、面向DSL开发的新一代低代码开发平台。它的前端可以使用任何基于JSON格式或者XML格式的渲染层。此前我考察过国外的[Appsmith](https://www.appsmith.com/)，阿里的[LowCodeEngine](https://lowcode-engine.cn/index)等技术，但是最后还是选择AMIS作为示例，因为集成其他的技术都需要更多的工作，也受到更多的限制。

## 6.1 XML与JSON的自动转化

在手工编写和阅读的时候，XML格式相对于JSON格式是有一定优势的，特别是集成外部模板引擎用于动态生成的时候。Nop平台为AMIS增加了XML格式的语法表达形式，可以按照简单的几条规则实现XML和JSON之间的双向转换。具体规则如下:

1. type属性对应于标签名

2. 简单类型的属性对应于XML的属性名

3. 复杂类型的属性对应于XML的子节点

4. 如果是列表类型，则在节点上标注`j:list=true`

5. body属性会被特殊识别，不用明确标注j:list

例如：

```json
{
        "type": "operation",
        "label": "操作",
        "buttons": [
          {
            "label": "详情",
            "type": "button",
            "level": "link",
            "actionType": "dialog",
            "dialog": {
              "title": "查看详情",
              "body": {
                "type": "form",
                "body": [
                  {
                    "type": "input-text",
                    "name": "browser",
                    "label": "Browser"
                  },
                  {
                    "type": "control",
                    "label": "grade",
                    "body": {
                      "type": "tag",
                      "label": "${grade}",
                      "displayMode": "normal",
                      "color": "active"
                    }
                  }
                ]
              }
            }
          }
```

对应于XML格式

```xml
<operation label="操作">
   <buttons j:list="true">
     <button label="详情" level="link" actionType="dialog">
        <dialog titl="查看详情">
            <body>
              <input-text name="browser" label="Browser" />
              <control label="grade">
                <body>
                  <tag label="${grade}" displayMode="normal" color="active" />
                </body>
              </control>
            </body>
        </dialog>
     </button>
   </buttons>
</operation>
```

Nop平台中的XPL模板语言为动态生成XML提供了诸多简化帮助，例如

```xml
<button xpl:if="xxx" label="${'$'}{grade}" icon="${icon}">
</button>
```

作为模板运行时xpl:if表示条件表达式，只有表达式返回true时整个节点才会被生成。所有XML属性生成的时候，如果属性值为null，则它将被自动忽略，不会输出到最终的结果中。借助于这一null属性过滤机制，我们可以很简单的控制哪些属性会被生成。

AMIS的语法设计相对比较规整，转成XML之后很接近于普通的HTML或者Vue模板。相比之下，LowCodeEngine的DSL设计更像是针对领域对象的一种序列化协议，而不是一种便于手工编写和阅读的DSL语言。

> AMIS早期版本的DSL设计中也存在着大量不一致的地方，比如容器控件的内容部分，有时叫做children，有时叫做controls，有时叫做content，最近重构后才普遍改成了body。

## 6.2 可逆计算分解

Nop平台基于可逆计算理论针对JSON和XML实现了通用的分解合并机制，可以按照通用的规则将很大的JSON文件分解为多个小型文件，相当于是为AMIS补充了某种模块组织语法。最常用的是两个语法，x:extends用于表示继承外部的某个文件，x:gen-extends表示动态生成可以被继承的JSON对象。

```yaml
x:gen-extends: |
  <web:GenPage view="NopAuthDept.view.xml" page="main" xpl:lib="/nop/web/xlib/web.xlib" />

body:
   name: crud-grid
   bulk-actions:
       - type: action
         id: test-button
         label: 'Test'
         actionType: dialog
         dialog:
            "x:extends": test.page.yaml
            "title": "Test Dialog"
```

以上示例表示，首先根据NopAuthDept.view.xml的配置动态生成一个CRUD页面，然后再在批量操作按钮区增加一个Test按钮，点击这个按钮的时候会弹出一个对话框，对话框的实现代码是复用已有的test.page.yaml文件。title属性会覆盖`x:extends`继承的内容，将对话框的标题设置为`Test Dialog`。

`x:extends`相当于是某种在Tree结构上执行的，类似面向对象的继承操作的通用操作符。

对于任意的JSON格式的外部文件，我们只需要将普通的JSON文件的加载函数修改为Nop平台所提供的ResourceLoader调用即可自动获得可逆计算所定义的分解、合并操作，并支持编译期元编程，允许在编译期进行一系列复杂的结构变换。

具体介绍参见

    [从张量积看低代码平台的设计](https://zhuanlan.zhihu.com/p/531474176)

## 6.4 Action模块化

AMIS的DSL本身只支持编写嵌入在页面中的JS片段代码，并不直接支持引入外部编写的JS函数。Nop平台为AMIS引入了一个`xui:import`属性，允许引入外部的JS库，把其中的函数作为事件响应函数来使用。

> 这一机制是通用的，可以用于集成其他的低代码引擎

```
type: page
xui:import: /nop/auth/pages/DemoPage/demo.lib.js
body:
  type: form
  api:
    url: "@action:demo.testAction"
    data:
      a: 1
```

以上示例表示，我们导入一个demo.lib.js库，然后通过demo.testAction引用其中的函数。

`url: "@action:demo.testAction"`这一语法是我们在AMIS的环境抽象基础上所提供的一个action触发机制。它通过拦截AMIS的fetcher调用，识别`@action:`前缀，然后映射到已加载的JS函数上，调用时传入data指定的参数。

脚本库的代码存放在demo.lib.xjs中（注意后缀名是xjs而不是js，我们会通过graalvm-js脚本引擎调用rollup打包工具将xjs转换为js文件，并打包成SystemJs模块结构）。

```javascript
/* @x:gen-extends:
  <!--这里可以用XPL模板语言来生成js代码 -->
 */
import { ajaxFetch} from '@nop/utils'

import {myAction} from './sub.lib.js'

import {myAction2} from './parts/sub2.lib.js'

import {ajaxRequest} from '@nop/utils'

export function testAction(options, page){
    page.env.alert("xx");
    ajaxFetch(options)
    ajaxRequest(options)
    myAction(options,page)
    myAction2(options,page)

    return Promise.resolve({
        status: 200 ,
        data: {
            status: 0
        }
    })
}
```

xjs文件可以按照普通的ESM模块文件的格式进行编写。我们通过在注释区增加`@x:gen-extends`为它增加了编译期动态生成的能力（这一能力在工作流编辑器的动态生成中会使用）。

export的函数是暴露给外部调用的接口函数。import调用会被转化为SystemJs的dependency。这里有一个特殊处理，对于/parts/目录下的文件，我们会调用rollup把它的代码和主文件的代码打包在一起，即parts下的文件认为是内部实现文件，不会暴露为外部可访问的js库。
打包后生成的结果参见文件 [demo.lib.js](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-auth/nop-auth-app/_dump/nop-app/nop/auth/pages/DemoPage/demo.lib.js)

除了action调用之外，外部库函数可以用在一切允许嵌入js脚本的地方，为此我们提供了另一个前缀`@fn:`，使用它的时候需要明确传递函数参数（action的函数参数已经约定为options,page）。

```javascript
"onClick":"@fn:demo.myListener(event,props)"
```

重新思考一下onClick的调用过程，我们会发现根据函数名查找到函数实现体的过程很类似于DOM组件的事件冒泡处理过程。事件冒泡时传递的是事件名，逐层向上查找，找到响应函数后处理。AMIS的action响应处理过程是由每个组件检查自己的handleAction是否可以处理对应的actionType，如果不能处理，则调用父组件传入的onAction来进行处理。

**如果我们直接约定向上传递的事件名就是函数名，则事件冒泡处理的过程可以被看作是一个在词法作用域中解析函数名的过程**。在不同层级引入的xui:import相当于是创建了不同的词法作用域，我们总是在最近的词法作用域中查找对应的函数，如果未找到，则继续向上在父作用域中查找。

面向对象技术在GUI开发领域一直占据主导地位，它的核心精华思想其实就是 ComponentTree + StateTree + ActionTree这三者之间的组织关系。组件构成组件树，一般情况下这是和源码可以一一对应的静态结构，一旦构造成功，可以在内存中稳定存在，多次复用，而事件和数据在这颗组件树上穿行不息。业务状态信息也构成一颗状态树，然后组件可以从状态树中拉取数据。组件树与状态树之间不一定是逐层对应的，但上下级关系保持稳定。当事件发生的时候，事件会沿着ActionTree向上冒泡，由某一层的处理器负责处理。理论上说，ActionTree的Tree结构不一定与ComponentTree相一致，但是考虑到我们思考上的稳定性和便利性，我们一般会希望使用编译期就能够确定的词法作用域，而不是某种受运行时状态影响的动态作用域，这样Action就会和某一级的Component联系起来, 而且上下级顺序与组件树保持一致（并不一定逐层对应）。

## 6.5 GraphQL简化

GraphQL总是需要指定返回字段列表，但是对于一个低代码平台来说，表单中具有哪些字段是一件可以根据模型分析得到的事情，所以我们可以根据表单模型自动推定它需要哪些字段而不需要手工指定。

Nop平台为AMIS增加了一个扩展，使得我们可以通过如下语法构造GraphQL请求

```javascript
url: "@graphql:NopAuthUser__get/{@formSelection}?id=$id"
```

具体介绍可以参见我此前的文章 [低代码平台中的GraphQL引擎](https://zhuanlan.zhihu.com/p/589565334)

## 6.6 多语言国际化

AMIS的JSON格式，可以很容易的被读取和处理。因此很多结构变换工作完全可以脱离AMIS框架，由后端进行统一的处理。
Nop平台中对JSON提供了统一的i18n字符串替换机制，它规定了如下两种方式：

1. 使用前缀引导语法识别并替换所有具有`@i18n:`的值

2. 为每个需要被国际化的key，增加对应的`@i18n:key`属性
   例如
   
   ```javascript
   {
   label: "@i18n:common.batchDelete|批量删除"
   }
   或者
   {
   label: "批量删除"
   "@i18n:label" : "common.batchDelete"
   }
   ```

## 6.7 权限控制

Nop平台规定了`xui:roles`和`xui:permissions`等权限相关的属性，在接收到JSON格式的页面数据之后，会自动验证权限属性是否满足，并删除所有不满足权限要求的节点。这一处理过程在JSON结构上进行，不涉及到任何前端框架特有的知识。

## 6.7 Vue组件集成

AMIS底层是基于React技术开发，而Nop平台的前端主要基于Vue3.0技术开发，为了便于集成第三方的vue组件, Nop平台提供了一个通用的包装组件。在AMIS的配置文件中我们可以这样使用

```javascript
{
  "type": "vue-form-item",
  "vueComponent": "Vue组件名",
  "props": {
    传给vue组件的属性
  }
}
```

## 总结

百度AMIS框架是一个设计精巧，集成难度很低的低代码前端框架。Nop平台在AMIS框架的基础上做了一定的改进和扩展，为一些常见问题提供了相应的解决方案。 Nop平台对于AMIS的封装代码已经上传到gitee
[nop-chaos](https://gitee.com/canonical-entropy/nop-chaos.git)
对AMIS集成感兴趣的同学可以参考。

Nop平台的开源地址：

- gitee: [canonical-entropy/nop-entropy](https://gitee.com/canonical-entropy/nop-entropy)
- github: [entropy-cloud/nop-entropy](https://github.com/entropy-cloud/nop-entropy)
- 开发示例：[docs/tutorial/tutorial.md](https://gitee.com/canonical-entropy/nop-entropy/blob/master/docs/tutorial/tutorial.md)
- [可逆计算原理和Nop平台介绍及答疑\_哔哩哔哩\_bilibili](https://www.bilibili.com/video/BV1u84y1w7kX/)
