package io.nop.auth.dao.delegate;

import io.nop.api.core.auth.IUserDelegateService;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static io.nop.auth.api.AuthApiConstants.DELEGATE_SCOPE_ALL;

public class DaoUserDelegateService implements IUserDelegateService {
    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

//    private IEntityDao<NopAuthUserSubstitution> delegateDao() {
//        return daoProvider.daoFor(NopAuthUserSubstitution.class);
//    }

    protected NopAuthUser requireUser(String userId) {
        return daoProvider.daoFor(NopAuthUser.class).requireEntityById(userId);
    }

    @Override
    public boolean canDelegate(String userId, String ownerId, String scope) {
        return getDelegateOwnerIds(userId, scope).contains(ownerId);
    }

    @Override
    public Set<String> getDelegateOwnerIds(String userId, String scope) {
        Set<String> ownerIds = new TreeSet<>();

        NopAuthUser user = requireUser(userId);
        LocalDateTime now = CoreMetrics.currentDateTime();

        user.getSubstitutionMappings().forEach(substitution -> {
            if (substitution.isValid(now)) {
                if (Objects.equals(substitution.getWorkScope(), scope) || DELEGATE_SCOPE_ALL.equals(substitution.getWorkScope())) {
                    ownerIds.add(substitution.getSubstitutedUserId());
                }
            }
        });
        return ownerIds;
    }
}