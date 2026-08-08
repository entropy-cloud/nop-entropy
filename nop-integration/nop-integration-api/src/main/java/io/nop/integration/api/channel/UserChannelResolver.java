package io.nop.integration.api.channel;

import java.util.List;

/**
 * Resolves which external channels a platform user has bound, and the
 * channel-native address for each binding. The business message layer uses
 * this to route an outbound message — keyed only by {@code userId} — to the
 * right channel address, keeping the caller ignorant of all channel-protocol
 * detail.
 *
 * <p>Data source: {@code nop_auth_ext_login} rows where {@code verified=true}
 * and {@code delFlag=0}; {@code loginType} maps to {@code channelType} via
 * {@link ChannelTypeCodes}, and {@code extId} is the channel-native address.
 *
 * <p>Multi-binding policy: a user may bind several channels. The no-arg
 * {@link #resolve(String)} overload returns bindings ordered by
 * "most recently active" ({@code lastLoginTime} descending), so the first
 * element is the default outbound target. The
 * {@link #resolve(String, String)} overload lets the caller force a specific
 * channel.
 */
public interface UserChannelResolver {

    /**
     * Return all verified channel bindings for the user, ordered by
     * last-login time descending (most-recently-active first). Returns an
     * empty list (never {@code null}) when the user has no binding.
     *
     * @param userId the platform user id; must not be null
     * @return ordered bindings; empty list if none
     */
    List<ChannelBinding> resolve(String userId);

    /**
     * Return the user's binding for a specific channel type, or {@code null}
     * if the user has no binding on that channel.
     *
     * @param userId      the platform user id; must not be null
     * @param channelType the channel type (e.g. {@link ChannelTypeCodes#CHANNEL_TYPE_FEISHU})
     * @return the matching binding, or {@code null} if none
     */
    ChannelBinding resolve(String userId, String channelType);
}
