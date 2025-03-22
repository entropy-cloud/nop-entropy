# React

## useEffect Usage

Generally, useEffect should not be used unless you are triggering remote calls or actions when the component leaves the React tree. Calculations based on props or state variables do not require the use of useEffect.

1. Use `useMemo` for caching expensive calculations.
2. Use the `key` property to mark fundamentally different data records to avoid unintended data sharing, thus avoiding the need to reset data.

```xml
<Profile userId={userId} key={profileId} />
```

3. When a component mounts, you can use `useEffect(fn, [])`.
4. In development environment, useEffect will be triggered twice.
5. If the state inside the component needs to be synchronized with the parent component, manage it through state lifting to the parent.

```xml
<Toggle isOn={isOn} onChange={onChange} />
```

6. Use `useSyncExternalStore` when syncing with external storage.

## State Library

zustand, refer to https://zhuanlan.zhihu.com/p/691233120

```javascript
import { useShallow } from 'zustand/react/shallow';
...
 const { theme, setTheme } = useConfigStore(
    useShallow(state => ({
      theme: state.theme,
      setTheme: state.setTheme,
    })),
  );
```
