package io.nop.auth.service.channel;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.bind.BindStartResult;
import io.nop.auth.api.bind.ChannelBindingInfo;
import io.nop.auth.api.bind.IChannelBindService;
import io.nop.auth.dao.entity.NopAuthExtLogin;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.integration.api.bind.BindTicket;
import io.nop.integration.api.bind.IChannelBindProvider;
import io.nop.integration.api.channel.ChannelTypeCodes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nop.api.core.ApiErrors.ERR_CHECK_INVALID_ARGUMENT;

/**
 * Default {@link IChannelBindService} implementation. Owns the
 * {@code NopAuthExtLogin} write path for channel bindings and bridges
 * between the {@code nop-integration-api} {@link IChannelBindProvider}
 * protocol (channel-specific) and the {@code nop-auth-api} message types
 * (channel-agnostic, exposed to callers).
 *
 * <p><b>Effective-binding predicate</b>: a row counts as a binding iff
 * {@code verified=true AND delFlag=0}. All read methods apply this filter
 * via the example query (verified=true, delFlag=0); {@code completeBinding}
 * writes rows with both set; {@code unbind} only flips {@code delFlag} to
 * {@code 1}.
 *
 * <p><b>Rebind adjudication</b> (design §3.4): because the
 * {@code (loginType, extId)} unique index is unconditional (rows with
 * {@code delFlag=1} still occupy the slot), {@code completeBinding} must
 * handle three cases explicitly rather than relying on the caller to
 * pre-clean:
 * <ol>
 *   <li><b>Same user, active row</b> — return the existing binding
 *       unchanged (idempotent rescan).</li>
 *   <li><b>Same user, soft-deleted row</b> (user unbound then rebound the
 *       same channel identity) — physically delete the soft-deleted row,
 *       then insert a fresh row.</li>
 *   <li><b>Cross-user, soft-deleted row</b> (user A unbound the extId, user
 *       B is now binding it) — physically delete A's soft-deleted row, then
 *       insert a fresh row for B. We never reuse another user's row
 *       (preserves createdBy/createTime audit semantics).</li>
 * </ol>
 *
 * <p>Cases (2) and (3) rely on {@code example.orm_disableLogicalDelete(true)}
 * so the example query returns rows regardless of {@code delFlag} —
 * {@code findAllByExample} otherwise hides soft-deleted rows.
 *
 * <p><b>Provider wiring</b>: providers are collected via
 * {@code <ioc:collect-beans by-type="...IChannelBindProvider"/>} in
 * {@code auth-service.beans.xml}. With no provider registered (current
 * state — concrete providers arrive in W5-2), the collection is empty and
 * {@link #startBinding} throws explicitly (no silent null return); tests
 * inject a stub provider programmatically.
 */
public class ChannelBindServiceImpl implements IChannelBindService {

    private static final Byte DEL_FLAG_ACTIVE = (byte) 0;
    private static final Byte DEL_FLAG_DELETED = (byte) 1;

    private final Map<String, IChannelBindProvider> providersByType = new LinkedHashMap<>();

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    /**
     * Collect {@link IChannelBindProvider} beans from the IoC container
     * (wired via {@code <ioc:collect-beans by-type>} in
     * {@code auth-service.beans.xml}). Each provider is keyed by its
     * {@link IChannelBindProvider#getChannelType()}. Duplicates are
     * rejected. An empty/null collection is legitimate when no concrete
     * provider is deployed yet.
     */
    public void setChannelBindProviders(Collection<IChannelBindProvider> providers) {
        this.providersByType.clear();
        if (providers != null) {
            for (IChannelBindProvider p : providers) {
                registerProvider(p);
            }
        }
    }

    public void registerProvider(IChannelBindProvider provider) {
        if (provider == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelBindServiceImpl.registerProvider: provider must not be null");
        }
        String type = provider.getChannelType();
        if (type == null || type.isEmpty()) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("msg",
                    "ChannelBindServiceImpl.registerProvider: provider.getChannelType() must not be null/empty");
        }
        if (providersByType.containsKey(type)) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", type).param("msg",
                    "ChannelBindServiceImpl.registerProvider: duplicate provider for channelType=" + type);
        }
        providersByType.put(type, provider);
    }

    @Override
    public BindStartResult startBinding(String channelType, String platformUserId) {
        IChannelBindProvider provider = requireProvider(channelType);
        BindTicket ticket = provider.createBindTicket(channelType, platformUserId);
        if (ticket == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", channelType).param("msg",
                    "IChannelBindProvider.createBindTicket returned null for channelType=" + channelType);
        }
        return toBindStartResult(ticket);
    }

    @Override
    public ChannelBindingInfo completeBinding(String channelType, String platformUserId, String extId) {
        int loginType = requireLoginType(channelType);

        // (a) Active binding for this (loginType, extId): idempotent rescan
        //     by the same user — return the existing row unchanged. If the
        //     active row belongs to a different user, that is a real conflict
        //     (the unique index prevents it from happening via normal flow)
        //     — surface it rather than silently overwriting.
        NopAuthExtLogin activeExample = newExampleAny();
        activeExample.setLoginType(loginType);
        activeExample.setExtId(extId);
        activeExample.setVerified(Boolean.TRUE);
        activeExample.setDelFlag(DEL_FLAG_ACTIVE);
        NopAuthExtLogin active = dao().findFirstByExample(activeExample);
        if (active != null) {
            if (!Objects.equals(active.getUserId(), platformUserId)) {
                throw new NopException(ERR_CHECK_INVALID_ARGUMENT)
                        .param("channelType", channelType)
                        .param("extId", extId)
                        .param("existingUserId", active.getUserId())
                        .param("requestUserId", platformUserId)
                        .param("msg", "extId is already bound to another user");
            }
            return toInfo(active, channelType);
        }

        // (b)/(c) Soft-deleted row for this (loginType, extId): physically
        //   delete it before inserting the new binding. disableLogicalDelete
        //   on the example bypasses findAllByExample's auto-filter that
        //   would otherwise hide delFlag!=0 rows. We do not reuse the
        //   soft-deleted row, even if it belongs to the same user — the
        //   audit fields (createdBy/createTime) of a fresh row carry the
        //   current binding's provenance, not the prior binding's.
        NopAuthExtLogin deletedExample = newExampleAny();
        deletedExample.setLoginType(loginType);
        deletedExample.setExtId(extId);
        deletedExample.orm_disableLogicalDelete(true);
        List<NopAuthExtLogin> deletedRows = dao().findAllByExample(deletedExample);
        for (NopAuthExtLogin row : deletedRows) {
            // Physical delete: bypass the session's logical-delete handling
            // so the row is actually removed from the table and frees the
            // unique-index slot for the new insert.
            dao().deleteEntityDirectly(row);
        }

        NopAuthExtLogin entity = dao().newEntity();
        entity.setUserId(platformUserId);
        entity.setLoginType(loginType);
        entity.setExtId(extId);
        entity.setVerified(Boolean.TRUE);
        entity.setDelFlag(DEL_FLAG_ACTIVE);
        dao().saveEntity(entity);

        return toInfo(entity, channelType);
    }

    @Override
    public ChannelBindingInfo findBinding(String channelType, String extId) {
        int loginType = requireLoginType(channelType);
        NopAuthExtLogin example = newActiveExample();
        example.setLoginType(loginType);
        example.setExtId(extId);
        NopAuthExtLogin row = dao().findFirstByExample(example);
        if (row == null) {
            return null;
        }
        return toInfo(row, channelType);
    }

    @Override
    public List<ChannelBindingInfo> listBindings(String userId) {
        if (userId == null || userId.isEmpty()) {
            return Collections.emptyList();
        }
        NopAuthExtLogin example = newActiveExample();
        example.setUserId(userId);
        List<NopAuthExtLogin> rows = dao().findAllByExample(example);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChannelBindingInfo> result = new ArrayList<>(rows.size());
        for (NopAuthExtLogin row : rows) {
            ChannelBindingInfo info = toInfo(row, null);
            if (info != null) {
                result.add(info);
            }
        }
        return result;
    }

    @Override
    public void unbind(String bindingId) {
        if (bindingId == null || bindingId.isEmpty()) {
            return;
        }
        NopAuthExtLogin row = dao().getEntityById(bindingId);
        if (row == null) {
            return;
        }
        row.setDelFlag(DEL_FLAG_DELETED);
        dao().updateEntity(row);
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * Translate a {@link BindTicket} into the {@code nop-auth-api}
     * {@link BindStartResult}. Only copies fields — no protocol coupling to
     * the channel provider's internal ticket state.
     */
    private BindStartResult toBindStartResult(BindTicket ticket) {
        BindStartResult result = new BindStartResult();
        result.setTicketId(ticket.getTicketId());
        result.setQrPayload(ticket.getQrPayload());
        result.setExpiresAt(ticket.getExpiresAt());
        return result;
    }

    /**
     * Translate a {@code NopAuthExtLogin} row into the
     * {@code nop-auth-api} {@link ChannelBindingInfo}. The channelType
     * argument is reused when the caller already knows it (saves a
     * {@link ChannelTypeCodes#channelType(int)} lookup); when null, the
     * loginType is mapped via {@link ChannelTypeCodes}.
     *
     * <p>Returns {@code null} if the row's loginType is not a channel type
     * (password/SSO) — those rows are filtered out of listBindings rather
     * than silently included.
     */
    private ChannelBindingInfo toInfo(NopAuthExtLogin row, String knownChannelType) {
        String channelType = knownChannelType;
        if (channelType == null) {
            Integer loginType = row.getLoginType();
            if (loginType == null) {
                return null;
            }
            channelType = ChannelTypeCodes.channelType(loginType);
            if (channelType == null) {
                return null;
            }
        }
        ChannelBindingInfo info = new ChannelBindingInfo();
        info.setBindingId(row.getSid());
        info.setChannelType(channelType);
        info.setExtId(row.getExtId());
        info.setPlatformUserId(row.getUserId());
        info.setBoundAt(row.getCreateTime());
        return info;
    }

    /**
     * Build an example entity that matches only effective bindings
     * ({@code verified=true AND delFlag=0}). The standard example-query
     * auto-filter for logical-delete entities keeps {@code delFlag=1} rows
     * out, and we additionally pin {@code verified=true} so an unverified
     * half-bound row never leaks through.
     */
    private NopAuthExtLogin newActiveExample() {
        NopAuthExtLogin example = new NopAuthExtLogin();
        example.setVerified(Boolean.TRUE);
        example.setDelFlag(DEL_FLAG_ACTIVE);
        return example;
    }

    /**
     * Build an example entity with no implicit filters. Used as the base
     * for the soft-deleted-row scan in {@link #completeBinding}, where the
     * caller will set {@code orm_disableLogicalDelete(true)} explicitly.
     */
    private NopAuthExtLogin newExampleAny() {
        return new NopAuthExtLogin();
    }

    private int requireLoginType(String channelType) {
        int loginType = ChannelTypeCodes.loginType(channelType);
        if (loginType < 0) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", channelType).param("msg",
                    "completeBinding: unknown channelType=" + channelType);
        }
        return loginType;
    }

    private IChannelBindProvider requireProvider(String channelType) {
        IChannelBindProvider provider = providersByType.get(channelType);
        if (provider == null) {
            throw new NopException(ERR_CHECK_INVALID_ARGUMENT).param("channelType", channelType).param("msg",
                    "no IChannelBindProvider registered for channelType=" + channelType);
        }
        return provider;
    }

    private IEntityDao<NopAuthExtLogin> dao() {
        return daoProvider.daoFor(NopAuthExtLogin.class);
    }
}
