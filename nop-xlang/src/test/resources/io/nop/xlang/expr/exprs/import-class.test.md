# 1. 导入常量类

````expr
import io.nop.api.core.ApiConstants;
import io.nop.api.core.ApiConstants as Const;

$.checkEquals('nop-version',ApiConstants.HEADER_VERSION);
$.checkEquals('nop-version',Const.HEADER_VERSION);
````

# 2. 新建对象

````expr
import io.nop.api.core.beans.PageBean;

const bean = new PageBean();
bean.setLimit(3);
$.checkEquals(3,bean.limit);
````

# 3. 静态方法

````expr
import io.nop.api.core.config.AppConfig;
logInfo("appName={}",AppConfig.appName());
````

# 4. 导入内置类

````expr
import java.util.Map;
import io.nop.core.lang.json.JsonTool;

const data = JsonTool.parseBeanFromText("{a:1}",Map);
````