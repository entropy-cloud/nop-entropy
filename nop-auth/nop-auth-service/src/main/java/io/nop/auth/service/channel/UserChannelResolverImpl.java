package io.nop.auth.service.channel;

import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.auth.dao.entity.NopAuthExtLogin;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.channel.ChannelBinding;
import io.nop.integration.api.channel.ChannelTypeCodes;
import io.nop.integration.api.channel.UserChannelResolver;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Default {@link UserChannelResolver} implementation. Reads
 * {@link NopAuthExtLogin} rows for a platform user, keeps only verified,
 * non-deleted channel bindings (via {@link ChannelTypeCodes}), and maps each
 * row into a channel-agnostic {@link ChannelBinding}.
 *
 * <p><b>Multi-binding ordering</b>: the no-arg {@link #resolve(String)}
 * overload returns bindings ordered by {@code lastLoginTime} descending
 * ("most recently active" first), so the first element is the default
 * outbound target. The channelType-specific overload ignores ordering since
 * at most one binding is expected per (userId, channelType).
 *
 * <p><b>No silent inclusion</b>: rows with {@code verified=false} or
 * {@code delFlag!=0} are excluded by the example query, and rows whose
 * {@code loginType} is not a channel type (password/SSO) are filtered out
 * explicitly — they never appear in the returned list.
 */
public class UserChannelResolverImpl implements UserChannelResolver {

    private static final Byte DEL_FLAG_ACTIVE = (byte) 0;

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public List<ChannelBinding> resolve(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Collections.emptyList();
        }
        NopAuthExtLogin example = newExample(userId);
        List<OrderFieldBean> orderBy = Collections.singletonList(OrderFieldBean.desc(
                NopAuthExtLogin.PROP_NAME_lastLoginTime));
        List<NopAuthExtLogin> rows = dao().findAllByExample(example, orderBy);

        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChannelBinding> bindings = new ArrayList<>(rows.size());
        for (NopAuthExtLogin row : rows) {
            ChannelBinding b = toBinding(row);
            if (b != null) {
                bindings.add(b);
            }
        }
        return bindings;
    }

    @Override
    public ChannelBinding resolve(String userId, String channelType) {
        if (userId == null || userId.isEmpty() || channelType == null) {
            return null;
        }
        int loginType = ChannelTypeCodes.loginType(channelType);
        if (loginType < 0) {
            // unknown channelType: no binding possible (explicit, not silent)
            return null;
        }
        NopAuthExtLogin example = newExample(userId);
        example.setLoginType(loginType);
        NopAuthExtLogin row = dao().findFirstByExample(example);
        if (row == null) {
            return null;
        }
        return toBinding(row);
    }

    private IEntityDao<NopAuthExtLogin> dao() {
        return daoProvider.daoFor(NopAuthExtLogin.class);
    }

    /**
     * Build an example entity matching active channel bindings for a user:
     * {@code userId} set, {@code verified=true}, {@code delFlag=0}.
     */
    private NopAuthExtLogin newExample(String userId) {
        NopAuthExtLogin example = new NopAuthExtLogin();
        example.setUserId(userId);
        example.setVerified(Boolean.TRUE);
        example.setDelFlag(DEL_FLAG_ACTIVE);
        return example;
    }

    /**
     * Map an ext-login row to a ChannelBinding. Returns {@code null} if the
     * row's loginType is not a channel type (password/SSO) — those rows are
     * filtered out rather than silently included.
     */
    private ChannelBinding toBinding(NopAuthExtLogin row) {
        Integer loginType = row.getLoginType();
        if (loginType == null) {
            return null;
        }
        String channelType = ChannelTypeCodes.channelType(loginType);
        if (channelType == null) {
            // not a channel login type (e.g. password=1, sso=10): skip
            return null;
        }
        return new ChannelBinding(row.getUserId(), channelType, row.getExtId());
    }
}
