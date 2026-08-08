package io.nop.auth.api.bind;

import java.util.List;

/**
 * Business facade for "a platform user has bound a channel identity" —
 * the single entry point for starting, completing, looking up, listing,
 * and removing channel bindings backed by {@code NopAuthExtLogin}.
 *
 * <p><b>Layering</b>: this interface lives in {@code nop-auth-api} (depends
 * only on {@code nop-api-core}) so any module can manage channel bindings
 * without depending on {@code nop-integration-api}. Its method signatures
 * use only {@code nop-auth-api} message types
 * ({@link BindStartResult}, {@link ChannelBindingInfo}); the
 * {@code nop-integration-api} {@code BindTicket} / {@code IChannelBindProvider}
 * types are translated by the {@code nop-auth-service} implementation
 * (design §3.4 ②) — keeps {@code nop-auth-api} dependency surface clean.
 *
 * <p><b>Effective-binding predicate</b>: a binding is "effective" iff
 * {@code verified=true AND delFlag=0}. {@link #findBinding} and
 * {@link #listBindings} only ever return effective bindings;
 * {@link #completeBinding} writes an effective binding; {@link #unbind}
 * flips {@code delFlag} to {@code 1} (logical delete) but the row stays.
 *
 * <p><b>Uniqueness</b>: the {@code (loginType, extId)} unique index
 * {@code UK_NOP_AUTH_EXT_LOGIN_TYPE_EXTID} (W0-1) guarantees at most one
 * row per channel-side identity, so an extId maps to exactly one platform
 * user. The implementation handles the three rebind cases (same-user
 * idempotent, same-user after unbind, cross-user after unbind) explicitly
 * rather than relying on the caller to pre-clean.
 */
public interface IChannelBindService {

    /**
     * Start a binding flow for a platform user on a channel. Delegates to
     * the channel's {@code IChannelBindProvider.createBindTicket} and
     * returns the QR payload for the front-end to render.
     *
     * @param channelType    the channel to bind (e.g. {@code "feishu"})
     * @param platformUserId the platform user starting the binding
     * @return non-null result carrying the QR payload, ticket id, and
     *         expiry; never returns a constant — the payload comes from
     *         the channel provider
     */
    BindStartResult startBinding(String channelType, String platformUserId);

    /**
     * Record an effective binding: write (or reuse — see below) a
     * {@code NopAuthExtLogin} row with {@code loginType} = the channel's
     * integer code (via {@code ChannelTypeCodes}), {@code extId} =
     * {@code extId}, {@code userId} = {@code platformUserId},
     * {@code verified=true}, {@code delFlag=0}.
     *
     * <p><b>Rebind adjudication</b> (design §3.4):
     * <ul>
     *   <li>Same {@code (loginType, extId)} already effective for the same
     *       {@code platformUserId} — idempotent: returns the existing
     *       binding without writing a new row.</li>
     *   <li>Same {@code (loginType, extId)} has a soft-deleted row owned
     *       by the same {@code platformUserId} (user unbound then rebound)
     *       — physically delete the soft-deleted row, then insert a new
     *       one.</li>
     *   <li>Same {@code (loginType, extId)} has a soft-deleted row owned
     *       by a different user (cross-user rebind; A unbound, B now binds)
     *       — physically delete A's soft-deleted row, then insert a new
     *       one for B (we never reuse another user's row).</li>
     * </ul>
     *
     * @param channelType    the channel being bound
     * @param platformUserId the platform user owning this binding
     * @param extId          the channel-side user identity (Feishu
     *                       {@code open_id}, ...)
     * @return non-null info for the effective binding after the call
     */
    ChannelBindingInfo completeBinding(String channelType, String platformUserId, String extId);

    /**
     * Reverse-lookup a binding by channel-side identity. Core of W4 scan
     * login: the channel callback arrives with only an {@code extId}, and
     * login needs the platform user that owns it.
     *
     * @param channelType the channel to look up
     * @param extId       the channel-side user identity
     * @return the effective binding if one exists, otherwise {@code null}
     *         (never throws for "not found")
     */
    ChannelBindingInfo findBinding(String channelType, String extId);

    /**
     * List all effective channel bindings for a platform user.
     *
     * @param userId the platform user id
     * @return non-null, possibly-empty list of effective bindings
     *         (verified=true AND delFlag=0); never {@code null}
     */
    List<ChannelBindingInfo> listBindings(String userId);

    /**
     * Logically delete a binding (soft-delete: {@code delFlag=1}). The row
     * stays in the table for audit, but no longer appears in
     * {@link #listBindings} / {@link #findBinding}.
     *
     * @param bindingId the {@code NopAuthExtLogin.sid} primary key of the
     *                  binding to remove
     */
    void unbind(String bindingId);
}
