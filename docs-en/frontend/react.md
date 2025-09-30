
# React

## Using useEffect

Typically use it only when you need to step outside React and initiate remote calls. You don’t need it for computations derived from variables in props or state.

1. Use useMemo to cache the results of expensive, complex computations.
2. Use the key prop to mark fundamentally different data records to avoid unintentionally sharing data, so you don’t need to reset data.

```
<Profile userId={userId} key={profileId} />
```

3. To issue a request when a component mounts, use `useEffect(fn, [])`
4. In the dev environment, useEffect will be triggered twice.
5. If a component’s internal state needs to be synchronized with its parent, lift the state up and let the parent manage it. For example:

```
<Toggle isOn={isOn} onChange={onChange} />
```

6. Use useSyncExternalStore when subscribing to external stores.


## State Store
zustand, see https://zhuanlan.zhihu.com/p/691233120

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

<!-- SOURCE_MD5:dd1d2b2daa6facc636e755499aeabd93-->
