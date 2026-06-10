// ====== Delta BizModel: 继承平台模块的 BizModel 并扩展 ======
package demo.delta.biz;

import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.core.context.IServiceContext;

public class DemoAuthUserBizModel extends NopAuthUserBizModel {

    @Override
    protected void defaultPrepareUpdate(EntityData<NopAuthUser> data, IServiceContext ctx) {
        super.defaultPrepareUpdate(data, ctx);
        // 在平台逻辑基础上追加自定义处理
    }

    @BizQuery
    public String customAction(@Name("arg") String arg) {
        return "result:" + arg;
    }
}

// 注册方式: 在 _delta/default/nop/auth/beans/auth-service.beans.xml 中
// <bean id="io.nop.auth.service.entity.NopAuthUserBizModel"
//       class="demo.delta.biz.DemoAuthUserBizModel"/>
// 使用 x:extends="super" 继承原模块的其他 bean 配置
