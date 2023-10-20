/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core;

import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.CheckResultBean;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.ExtensibleBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.IntRangeBean;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.PointBean;
import io.nop.api.core.beans.PropDefBean;
import io.nop.api.core.beans.ServiceCallBean;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.api.core.beans.VarMetaBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.beans.WeightedItem;
import io.nop.api.core.beans.graphql.CancelRequestBean;
import io.nop.api.core.beans.graphql.GraphQLConnection;
import io.nop.api.core.beans.graphql.GraphQLConnectionInput;
import io.nop.api.core.beans.graphql.GraphQLEdgeBean;
import io.nop.api.core.beans.graphql.GraphQLErrorBean;
import io.nop.api.core.beans.graphql.GraphQLNode;
import io.nop.api.core.beans.graphql.GraphQLPageInfo;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.graphql.GraphQLSourceLocation;
import io.nop.api.core.beans.query.GroupFieldBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.beans.query.QuerySourceBean;
import io.nop.api.core.beans.task.TaskStatusBean;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;

public class GenReflectConfig {
    @Test
    public void generate() {
        Class[] classes = new Class[]{
                io.nop.api.core.annotations.cache.Cache.class,
                io.nop.api.core.annotations.cache.CacheEvicts.class,
                io.nop.api.core.annotations.orm.SingleSession.class,
                io.nop.api.core.annotations.txn.TccMethod.class,
                io.nop.api.core.annotations.txn.TccTransactional.class,
                io.nop.api.core.annotations.txn.Transactional.class,
                io.nop.api.core.annotations.biz.BizAction.class,
                io.nop.api.core.annotations.biz.BizQuery.class,
                io.nop.api.core.annotations.biz.BizMutation.class,
                io.nop.api.core.annotations.biz.BizSubscription.class,

                ApiRequest.class,
                ApiRequest.class,
                ApiMessage.class,
                CheckResultBean.class,
                DictBean.class,
                DictOptionBean.class,
                ErrorBean.class,
                ExtensibleBean.class,
                FieldSelectionBean.class,
                FilterBeans.class,
                IntRangeBean.class,
                IntRangeBean.class,
                PageBean.class,
                LongRangeBean.class,
                PointBean.class,
                PropDefBean.class,
                ServiceCallBean.class,
                TreeBean.class,
                TreeResultBean.class,
                VarMetaBean.class,
                WebContentBean.class,
                WeightedItem.class,
                TaskStatusBean.class,
                GroupFieldBean.class,
                OrderFieldBean.class,
                QueryBean.class,
                QueryFieldBean.class,
                QuerySourceBean.class,
                CancelRequestBean.class,
                GraphQLConnection.class,
                GraphQLConnectionInput.class,
                GraphQLEdgeBean.class,
                GraphQLErrorBean.class,
                GraphQLNode.class,
                GraphQLPageInfo.class,
                GraphQLRequestBean.class,
                GraphQLResponseBean.class,
                GraphQLSourceLocation.class,
        };

        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0, n = classes.length; i < n; i++) {
            if(i != 0){
                sb.append(",\n");
            }
            sb.append("  {\n" +
                    "    \"name\": \""+classes[i].getName()+"\",\n" +
                    "    \"allPublicConstructors\": true,\n" +
                    "    \"allPublicMethods\": true\n" +
                    "  }");
        }
        sb.append("\n]");
        writeText(getReflectConfigFile(), sb.toString());
    }

    public static File getReflectConfigFile() {
        File file = getClassPathFile("META-INF/native-image/io.github.entropy-cloud/nop-api-core");
        File store = new File(file, "reflect-config.json");
        String path = store.getAbsolutePath().replace('\\','/');
        path = path.replace("/nop-api-core/target/classes/","/nop-api-core/src/main/resources/");
        return new File(path);
    }

    public static File getClassPathFile(String path) {
        URL url = GenReflectConfig.class.getClassLoader().getResource(path);
        String s = url.getFile();
        if (s == null)
            return null;
        File file = new File(s);
        return file;
    }

    public static void writeText(File file, String text) {
        Writer out = null;
        try {
            out = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            out.write(text);
            out.flush();
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            try {
                out.close();
            } catch (Exception e) {
            }
        }
    }
}
