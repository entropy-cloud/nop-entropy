/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.enc;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nop.commons.CommonConfigs;
import io.nop.commons.bytes.Bytes;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;
import io.nop.orm.IOrmEntity;
import io.nop.orm.OrmConfigs;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import io.nop.orm.model.OrmModelConstants;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_ENC_HELPER_COLUMN_READONLY;

/**
 * 可检索加密：enc 主列的辅助列（assisted/like）值派生与只读守护。
 * 契约见 ai-dev/design/nop-orm-enc-assisted-columns.md：
 * assisted 列 = hex(HMAC-SHA256(key, normalize(plain)))，定长 64；
 * like 列 = 逐字符 hex(width)(HMAC-SHA256(key, salt || c)) 拼接，长度 = width * len(plain)。
 * 主列在内存中为明文（加密发生在 binder 层），派生始终基于明文。
 */
public class EncAssistedHelper {
    private static final Logger LOG = LoggerFactory.getLogger(EncAssistedHelper.class);

    private static final EncAssistedHelper _instance = new EncAssistedHelper();

    public static EncAssistedHelper instance() {
        return _instance;
    }

    /**
     * 新实体保存入队前调用。此时框架尚未派生，辅助列任何已初始化状态都只能是用户赋值，
     * 显式失败（新建实体的属性赋值不进入 dirty 集合，不能复用 dirty 判定）。
     */
    public void onSave(IEntityModel entityModel, IOrmEntity entity) {
        List<Binding> bindings = resolveBindings(entityModel);
        if (bindings.isEmpty())
            return;

        for (Binding binding : bindings) {
            if (binding.assistedPropId > 0 && entity.orm_propInited(binding.assistedPropId))
                throw readOnlyError(binding.assistedName);
            if (binding.likePropId > 0 && entity.orm_propInited(binding.likePropId))
                throw readOnlyError(binding.likeName);
        }

        derive(entity, bindings);
    }

    /**
     * 已托管实体更新入队前调用。用户对辅助列的赋值必然在 dirty 集合中（load 后赋值记录 oldValues），
     * 显式失败；随后派生经 orm_propValue 写入使辅助列进入 UPDATE 变更列集合。
     */
    public void onUpdate(IEntityModel entityModel, IOrmEntity entity) {
        List<Binding> bindings = resolveBindings(entityModel);
        if (bindings.isEmpty())
            return;

        for (Binding binding : bindings) {
            if (binding.assistedPropId > 0 && entity.orm_dirtyPropIds().contains(binding.assistedPropId))
                throw readOnlyError(binding.assistedName);
            if (binding.likePropId > 0 && entity.orm_dirtyPropIds().contains(binding.likePropId))
                throw readOnlyError(binding.likeName);
        }

        derive(entity, bindings);
    }

    private List<Binding> resolveBindings(IEntityModel entityModel) {
        List<Binding> bindings = new ArrayList<>(2);
        for (IColumnModel col : entityModel.getColumns()) {
            if (!col.containsTag(OrmModelConstants.TAG_ENC))
                continue;
            IColumnModel assisted = resolveHelper(entityModel, col, OrmModelConstants.EXT_ENC_ASSISTED);
            IColumnModel like = resolveHelper(entityModel, col, OrmModelConstants.EXT_ENC_LIKE);
            if (assisted != null || like != null)
                bindings.add(new Binding(col.getPropId(), assisted, like));
        }
        return bindings;
    }

    private IColumnModel resolveHelper(IEntityModel entityModel, IColumnModel master, String extName) {
        Object value = master.prop_get(extName);
        if (value == null)
            return null;
        // 引用合法性已在模型加载期校验（OrmModelInitializer.checkEncAssisted），此处直接解析
        return entityModel.requireColumn(value.toString());
    }

    private void derive(IOrmEntity entity, List<Binding> bindings) {
        for (Binding binding : bindings) {
            Object masterValue = entity.orm_propValue(binding.masterPropId);
            String plain = masterValue == null ? null : masterValue.toString();

            if (binding.assistedPropId > 0)
                entity.orm_propValue(binding.assistedPropId, plain == null ? null : assistedDigest(plain));

            if (binding.likePropId > 0)
                entity.orm_propValue(binding.likePropId, plain == null ? null : likeDigest(plain));
        }
    }

    private NopException readOnlyError(String helperPropName) {
        return new NopException(ERR_ORM_ENC_HELPER_COLUMN_READONLY)
                .param(ARG_PROP_NAME, helperPropName);
    }

    private static final class Binding {
        final int masterPropId;
        final int assistedPropId;
        final int likePropId;
        final String assistedName;
        final String likeName;

        Binding(int masterPropId, IColumnModel assisted, IColumnModel like) {
            this.masterPropId = masterPropId;
            this.assistedPropId = assisted == null ? 0 : assisted.getPropId();
            this.likePropId = like == null ? 0 : like.getPropId();
            this.assistedName = assisted == null ? null : assisted.getName();
            this.likeName = like == null ? null : like.getName();
        }
    }

    /**
     * 确定性等值摘要：hex(HMAC-SHA256(key, trim(plain)))，64 字符。
     */
    public String assistedDigest(String plain) {
        byte[] mac = HashHelper.hmacSha256(normalize(plain).getBytes(StandardCharsets.UTF_8), key());
        return Bytes.toHex(mac);
    }

    /**
     * 模糊摘要：逐字符 hex(width) 摘要拼接，长度 = width * len(plain)。
     */
    public String likeDigest(String plain) {
        String normalized = normalize(plain);
        int width = likeDigestWidth();
        byte[] key = key();
        String salt = StringHelper.toString(CommonConfigs.CFG_CRYPT_DEFAULT_SALT.get(), "");
        StringBuilder sb = new StringBuilder(width * normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            byte[] input = (salt + normalized.charAt(i)).getBytes(StandardCharsets.UTF_8);
            byte[] mac = HashHelper.hmacSha256(input, key);
            sb.append(Bytes.toHex(mac), 0, width);
        }
        return sb.toString();
    }

    private String normalize(String plain) {
        return plain == null ? null : plain.trim();
    }

    private byte[] key() {
        String key = OrmConfigs.CFG_ORM_ENC_ASSISTED_KEY.get();
        if (StringHelper.isEmpty(key))
            key = CommonConfigs.CFG_CRYPT_DEFAULT_ENC_KEY.get();
        if (StringHelper.isEmpty(key)) {
            warnEmptyKeyOnce();
            // HMAC 要求非空 key：未配置时退回内建缺省（仅保证可运行与确定性，不提供机密性）
            key = "nop-entropy-enc-assisted-default-key";
        }
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private volatile boolean emptyKeyWarned;

    private void warnEmptyKeyOnce() {
        if (emptyKeyWarned)
            return;
        emptyKeyWarned = true;
        LOG.warn("nop.orm.enc-assisted.empty-key:assisted/like digests are derived with a built-in default key;"
                + " configure nop.orm.enc-assisted-key or nop.crypt.default-enc-key for production use");
    }

    private int likeDigestWidth() {
        return OrmConfigs.CFG_ORM_ENC_LIKE_DIGEST_WIDTH.get();
    }
}
