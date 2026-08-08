package io.nop.ai.gateway.channel;

import io.nop.ai.dao.entity.NopAiChannelSession;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;

import java.sql.Timestamp;

import static io.nop.api.core.ApiErrors.ERR_CHECK_INVALID_ARGUMENT;

/**
 * Default {@link IChannelSessionStore} backed by the {@code NopAiChannelSession}
 * ORM entity (generated from {@code model/nop-ai.orm.xml}). Uses a globally
 * injected {@link IDaoProvider} (the {@code nopDaoProvider} bean) to access
 * the entity DAO and {@link IOrmTemplate} to scope write transactions.
 *
 * <p>Anti-hollow guarantees:
 * <ul>
 *   <li>{@code findByChannel} miss → returns {@code null} (explicit not-found),
 *       never a silent empty entity</li>
 *   <li>{@code saveMapping} → real {@code saveEntity} persistence</li>
 *   <li>{@code updateLastActive} → real load+{@code updateEntity}; a miss throws
 *       rather than silently no-op'ing</li>
 * </ul>
 */
public class ChannelSessionStoreImpl implements IChannelSessionStore {

    private static final String ARG_MSG = "msg";

    @Inject
    protected IDaoProvider daoProvider;

    @Inject
    protected IOrmTemplate ormTemplate;

    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    public void setOrmTemplate(IOrmTemplate ormTemplate) {
        this.ormTemplate = ormTemplate;
    }

    private IEntityDao<NopAiChannelSession> dao() {
        if (daoProvider == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                    "ChannelSessionStoreImpl: daoProvider not configured");
        }
        return daoProvider.daoFor(NopAiChannelSession.class);
    }

    @Override
    public ChannelSession findByChannel(final String channelType, final String channelId) {
        requireKey(channelType, channelId);
        if (ormTemplate != null) {
            return ormTemplate.runInSession(s -> doFind(channelType, channelId));
        }
        return doFind(channelType, channelId);
    }

    @Override
    public void saveMapping(final String channelType, final String channelId,
                            final String sessionId, final String agentName) {
        requireKey(channelType, channelId);
        if (sessionId == null || sessionId.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                    "ChannelSessionStoreImpl.saveMapping: sessionId must come from engine ack, got null/empty");
        }
        if (agentName == null || agentName.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                    "ChannelSessionStoreImpl.saveMapping: agentName must not be null/empty");
        }

        Runnable save = () -> {
            NopAiChannelSession entity = dao().newEntity();
            entity.setChannelType(channelType);
            entity.setChannelId(channelId);
            entity.setSessionId(sessionId);
            entity.setAgentName(agentName);
            entity.setLastActiveAt(new Timestamp(System.currentTimeMillis()));
            dao().saveEntity(entity);
        };
        if (ormTemplate != null) {
            ormTemplate.runInSession(s -> {
                save.run();
                return null;
            });
        } else {
            save.run();
        }
    }

    @Override
    public void updateLastActive(final String channelType, final String channelId) {
        requireKey(channelType, channelId);
        if (ormTemplate == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                    "ChannelSessionStoreImpl: ormTemplate not configured (required for updateLastActive)");
        }
        // load + modify + update must share one OrmSession for dirty-tracking
        ormTemplate.runInSession(s -> {
            NopAiChannelSession entity = dao().findFirstByQuery(byChannelQuery(channelType, channelId));
            if (entity == null) {
                throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                        "ChannelSessionStoreImpl.updateLastActive: no mapping for channelType="
                                + channelType + ", channelId=" + channelId);
            }
            entity.setLastActiveAt(new Timestamp(System.currentTimeMillis()));
            dao().updateEntity(entity);
            return null;
        });
    }

    private ChannelSession doFind(String channelType, String channelId) {
        NopAiChannelSession entity = dao().findFirstByQuery(byChannelQuery(channelType, channelId));
        return entity == null ? null : toDto(entity);
    }

    private static QueryBean byChannelQuery(String channelType, String channelId) {
        QueryBean query = new QueryBean();
        TreeBean filter = FilterBeans.and(
                FilterBeans.eq("channelType", channelType),
                FilterBeans.eq("channelId", channelId));
        query.setFilter(filter);
        return query;
    }

    private static ChannelSession toDto(NopAiChannelSession e) {
        return new ChannelSession(
                e.getChannelType(),
                e.getChannelId(),
                e.getSessionId(),
                e.getAgentName(),
                e.getCreateTime(),
                e.getLastActiveAt());
    }

    private static void requireKey(String channelType, String channelId) {
        if (channelType == null || channelType.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                    "ChannelSessionStoreImpl: channelType must not be null/empty");
        }
        if (channelId == null || channelId.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param(ARG_MSG,
                    "ChannelSessionStoreImpl: channelId must not be null/empty");
        }
    }
}
