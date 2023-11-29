package io.nop.wf.dao.delegate;

import io.nop.api.core.beans.query.QueryBean;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.core.NopWfCoreConstants;
import io.nop.wf.core.delegate.IUserDelegateService;
import io.nop.wf.dao.entity.NopWfUserDelegate;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.api.core.beans.FilterBeans.eq;
import static io.nop.api.core.beans.FilterBeans.or;

public class DaoUserDelegateService implements IUserDelegateService {
    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    private IEntityDao<NopWfUserDelegate> delegateDao() {
        return daoProvider.daoFor(NopWfUserDelegate.class);
    }

    @Override
    public boolean canDelegate(String userId, String ownerId, String scope) {
        QueryBean query = new QueryBean();
        query.addFilter(eq(NopWfUserDelegate.PROP_NAME_delegateId, userId));
        query.addFilter(eq(NopWfUserDelegate.PROP_NAME_userId, ownerId));
        if (StringHelper.isEmpty(scope)) {
            query.addFilter(eq(NopWfUserDelegate.PROP_NAME_delegateScope, NopWfCoreConstants.DELEGATE_SCOPE_ALL));
        } else {
            query.addFilter(or(
                    eq(NopWfUserDelegate.PROP_NAME_delegateScope, NopWfCoreConstants.DELEGATE_SCOPE_ALL),
                    eq(NopWfUserDelegate.PROP_NAME_delegateScope, scope)
            ));
        }

        return delegateDao().existsByQuery(query);
    }

    @Override
    public Set<String> getDelegateOwnerIds(String userId, String scope) {
        QueryBean query = new QueryBean();
        query.addFilter(eq(NopWfUserDelegate.PROP_NAME_delegateId, userId));
        if (StringHelper.isEmpty(scope)) {
            query.addFilter(eq(NopWfUserDelegate.PROP_NAME_delegateScope, NopWfCoreConstants.DELEGATE_SCOPE_ALL));
        } else {
            query.addFilter(or(
                    eq(NopWfUserDelegate.PROP_NAME_delegateScope, NopWfCoreConstants.DELEGATE_SCOPE_ALL),
                    eq(NopWfUserDelegate.PROP_NAME_delegateScope, scope)
            ));
        }

        List<NopWfUserDelegate> list = delegateDao().findAllByQuery(query);
        return list.stream().map(NopWfUserDelegate::getUserId).collect(Collectors.toSet());
    }
}