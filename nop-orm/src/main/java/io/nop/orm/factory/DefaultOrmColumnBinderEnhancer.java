/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.factory;

import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.core.lang.sql.binder.EncodedDataParameterBinder;
import io.nop.core.lang.sql.binder.IDataParameterBinder;
import io.nop.orm.IOrmColumnBinderEnhancer;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;

public class DefaultOrmColumnBinderEnhancer implements IOrmColumnBinderEnhancer {
    private ITextCipher textCipher = new AESTextCipher();

    public void setTextCipher(ITextCipher cipher) {
        this.textCipher = cipher;
    }

    @Override
    public IDataParameterBinder enhanceBinder(IEntityModel entityModel, IColumnModel col,
                                              IDataParameterBinder defaultBinder) {
        if (col.containsTag(OrmConstants.TAG_ENC)) {
            return new EncodedDataParameterBinder(defaultBinder, textCipher);
        }
        return defaultBinder;
    }
}
