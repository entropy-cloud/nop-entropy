# 判断内部类型

```expr
$.checkState('s' instanceof String)
```

# 判断import类型

````
import io.nop.api.core.beans.PageBean;

const page = new PageBean();
$.checkState(page instanceof PageBean)

$.checkState(page instanceof io.nop.api.core.beans.PageBean)
````