package io.nop.integration.api.channel;

/**
 * Authoritative mapping between the {@code loginType} integer code stored in
 * {@code nop_auth_ext_login} (and declared in the {@code auth/login-type}
 * dictionary) and the {@code channelType} string identifier returned by
 * transport-layer {@code IChannelConnector.getChannelType()}.
 *
 * <p>This class is the single source of truth for the int&#8596;String mapping.
 * Both the resolver (in {@code nop-auth-service}, reads the int from the DB)
 * and the connectors (return the string from {@code getChannelType()}) agree
 * through these constants, so a binding's {@link ChannelBinding#getChannelType()}
 * always matches the connector registry key.
 *
 * <p>The numeric codes mirror the {@code auth/login-type} dictionary allocated
 * by W0: {@code 20}=飞书, {@code 21}=钉钉, {@code 22}=企微, {@code 23}=Webhook.
 * Codes {@code 1}=密码 and {@code 10}=单点 predate the channel integration and
 * are not channel bindings, so they have no channelType string.
 */
public final class ChannelTypeCodes {

    public static final int LOGIN_TYPE_FEISHU = 20;
    public static final int LOGIN_TYPE_DINGTALK = 21;
    public static final int LOGIN_TYPE_WECOM = 22;
    public static final int LOGIN_TYPE_WEBHOOK = 23;

    public static final String CHANNEL_TYPE_FEISHU = "feishu";
    public static final String CHANNEL_TYPE_DINGTALK = "dingtalk";
    public static final String CHANNEL_TYPE_WECOM = "wecom";
    public static final String CHANNEL_TYPE_WEBHOOK = "webhook";

    private ChannelTypeCodes() {
    }

    /**
     * Map a {@code loginType} int (from {@code nop_auth_ext_login}) to the
     * channelType string used by connectors. Returns {@code null} for
     * non-channel login types (password / SSO) or unknown codes — callers
     * treat null as "not a channel binding" and skip it explicitly.
     */
    public static String channelType(int loginType) {
        switch (loginType) {
            case LOGIN_TYPE_FEISHU:
                return CHANNEL_TYPE_FEISHU;
            case LOGIN_TYPE_DINGTALK:
                return CHANNEL_TYPE_DINGTALK;
            case LOGIN_TYPE_WECOM:
                return CHANNEL_TYPE_WECOM;
            case LOGIN_TYPE_WEBHOOK:
                return CHANNEL_TYPE_WEBHOOK;
            default:
                return null;
        }
    }

    /**
     * Inverse mapping: channelType string &#8594; loginType int. Returns
     * {@code -1} for unknown channel types (no silent default).
     */
    public static int loginType(String channelType) {
        if (channelType == null) {
            return -1;
        }
        switch (channelType) {
            case CHANNEL_TYPE_FEISHU:
                return LOGIN_TYPE_FEISHU;
            case CHANNEL_TYPE_DINGTALK:
                return LOGIN_TYPE_DINGTALK;
            case CHANNEL_TYPE_WECOM:
                return LOGIN_TYPE_WECOM;
            case CHANNEL_TYPE_WEBHOOK:
                return LOGIN_TYPE_WEBHOOK;
            default:
                return -1;
        }
    }

    /**
     * Whether a loginType int represents a channel binding (as opposed to
     * password / SSO login). Used by the resolver to filter non-channel rows.
     */
    public static boolean isChannelLoginType(int loginType) {
        return channelType(loginType) != null;
    }
}
