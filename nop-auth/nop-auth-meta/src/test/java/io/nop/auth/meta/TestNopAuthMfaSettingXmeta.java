package io.nop.auth.meta;

import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W4 regression test: TOTP secret plaintext boundary.
 * <p>
 * The {@code secret} column stores an {@code AESTextCipher} ciphertext; it must never be
 * exposed at any xmeta/GraphQL/REST layer. The structural enforcement comes from two
 * cooperating sources (defense in depth, mirroring the W3 {@code NopCredential.data} /
 * {@code NopAiModel.apiKey} pattern):
 * <ol>
 *   <li>ORM source column {@code tagSet="masked,var,not-pub"} drives the generated base xmeta
 *       to mark the prop {@code published=false} + {@code internal=true}.</li>
 *   <li>The retention xmeta {@code NopAuthMfaSetting.xmeta} re-declares {@code published=false}
 *       so the boundary holds even if the ORM tagSet is later weakened.</li>
 * </ol>
 * The merged runtime xmeta (loaded here via {@code ResourceComponentManager}) is exactly what
 * {@code ObjMetaToGraphQLDefinition} consumes to build the GraphQL schema, so
 * {@code published=false} here == not present in the published GraphQL type.
 */
public class TestNopAuthMfaSettingXmeta {

    private static final String MERGED_XMETA = "/nop/auth/model/NopAuthMfaSetting/NopAuthMfaSetting.xmeta";
    private static final String BASE_XMETA = "/nop/auth/model/NopAuthMfaSetting/_NopAuthMfaSetting.xmeta";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private static IObjPropMeta loadProp(String path, String prop) {
        IObjMeta meta = (IObjMeta) ResourceComponentManager.instance().loadComponentModel(path);
        IObjPropMeta p = meta.getProp(prop);
        assertNotNull(p, prop + " prop must exist in " + path);
        return p;
    }

    @Test
    public void testGeneratedBaseXmetaRestrictsSecret() {
        IObjPropMeta prop = loadProp(BASE_XMETA, "secret");
        assertFalse(prop.isPublished(), "base xmeta must not publish secret");
        assertTrue(prop.isInternal(), "base xmeta must mark secret internal");
    }

    @Test
    public void testMergedXmetaRestrictsSecretAndBindToken() {
        IObjPropMeta secret = loadProp(MERGED_XMETA, "secret");
        assertFalse(secret.isPublished(), "merged xmeta must not publish secret");

        IObjPropMeta bindToken = loadProp(MERGED_XMETA, "bindToken");
        assertFalse(bindToken.isPublished(), "merged xmeta must not publish bindToken (one-time binding token)");
    }

    @Test
    public void testNormalPropsRemainExposed() {
        IObjPropMeta status = loadProp(MERGED_XMETA, "status");
        assertNotNull(status);
        assertTrue(status.isQueryable(), "non-secret props must stay queryable");

        IObjPropMeta mfaType = loadProp(MERGED_XMETA, "mfaType");
        assertNotNull(mfaType);
        assertTrue(mfaType.isQueryable(), "non-secret props must stay queryable");
    }
}
