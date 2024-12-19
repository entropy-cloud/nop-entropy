/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.LogLevel;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.DaoProvider;
import io.nop.demo.annotations.SendEmail;
import io.nop.demo.domain.Material;
import io.nop.demo.domain.ProcessCard;
import io.nop.demo.domain.ProductionOrder;
import io.nop.log.core.LoggerConfigurator;
import io.nop.xlang.filter.BizValidatorHelper;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@BizModel("Demo")
public class DemoBizModel {

//    @Inject
//    RedisDataSource redisDataSource;

    @PostConstruct
    public void init() {
        //    Guard.notNull(redisDataSource, "redisDataSource");
    }

    @BizQuery
    public void changeLogLevel(@Name("logLevel") LogLevel logLevel, @Name("loggerName") String loggerName) {
        LoggerConfigurator.instance().changeLogLevel(loggerName, logLevel);
    }


    @BizQuery
    public String testValidate(@Name("orderId") String orderId,
                               @Name("materialId") String materialId,
                               IServiceContext context) {
        ProcessCard entity = loadProcessCard(orderId, materialId);
        Map<String, Object> vars = prepareVars(entity);
        BizValidatorHelper.runValidator("/nop/demo/validator/process-card.validator.xml",
                vars, context);
        return "Valid";
    }

    public ProcessCard loadProcessCard(String orderId, String materialId) {
        ProcessCard entity = new ProcessCard();
        ProductionOrder order = new ProductionOrder();
        order.setOrderId(orderId);
        entity.setProductionOrder(order);

        entity.setMaterialId(materialId);
        return entity;
    }

    private Map<String, Object> prepareVars(ProcessCard card) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("entity", card);
        vars.put("firstProductionOrder", card.getProductionOrder());
        Material material = new Material();
        material.setMaterialId("2");
        vars.put("firstMaterial", material);
        return vars;
    }

    @BizQuery
    public MyUserPageBean findUsers() {
        List<NopAuthUser> users = DaoProvider.instance().daoFor(NopAuthUser.class).findAll();
        MyUserPageBean ret = new MyUserPageBean();
        ret.setData(users);
        ret.setTotalCount(users.size());
        return ret;
    }

    @BizQuery
    @SendEmail
    public void testMethod1(@RequestBean MyRequest request) {
        System.out.println("doSomething");
    }
}
