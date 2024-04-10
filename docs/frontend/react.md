# React

## useEffect的使用

一般仅在跳出react代码，发起远程调用时使用。根据props或者state中的变量进行计算没必要使用。

1. 缓存耗时的复杂计算结果时，使用useMemo。
2. 通过key属性标记本质上不同的数据记录，避免无意间共享数据，也就不需要重置数据

```
<Profile userId={userId} key={profileId} />
```

3. 组件mount时发起请求，可以使用`useEffect(fn,[])`
4. dev环境下useEffect会被触发两次
5. 组件内状态如果需要和父组件同步，则通过状态提升交由父组件管理。例如

```
<Toggle isOn={isOn} onChange={onChange} />
```

6. 订阅外部存储时使用useSyncExternalStore


## 状态库
zustand，参考 https://zhuanlan.zhihu.com/p/691233120

```javascript
import { useShallow } from 'zustand/react/shallow';
...
 const { theme, setTheme } = useConfigStore(
    useShallow(state => ({
      theme: state.theme,
      setTheme: state.setTheme,
    }))
  );
```
