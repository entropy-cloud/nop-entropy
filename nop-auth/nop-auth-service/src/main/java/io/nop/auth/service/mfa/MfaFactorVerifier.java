/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.core.mfa.store.CodeVerifyResult;
import io.nop.auth.core.mfa.store.SmsCodeStore;
import io.nop.auth.core.totp.TOTPAuthenticator;
import io.nop.auth.dao.entity.NopAuthMfaSetting;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.sql.Timestamp;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TOTP_WINDOW_SKEW;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_SMS;
import static io.nop.auth.service.NopAuthConstants.MFA_TYPE_TOTP;
import static io.nop.auth.service.NopAuthConstants.SMS_KEY_MFA;
import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_SMS_CODE_EXPIRED;

/**
 * 共享因子校验组件（设计 §3.1 结论 5 / §5.3.0 #5/#6 收敛对象）：登录级
 * （{@code verifySecondFactorAndComplete}）、绑定级（confirmMfa/unbindMfa）、操作级
 * （{@code mfaVerifyOperation}）三处因子校验收敛于此。
 * <ul>
 *   <li><b>组件契约裁定</b>：返回校验结果布尔语义；失败计数与 MFA_FAIL/CHALLENGE_EXPIRED
 *       错误码留在调用方（与既有两调用点语义等价）。SMS 码已失效（EXPIRED）在两处既有
 *       调用点行为完全一致（抛 {@code ERR_AUTH_SMS_CODE_EXPIRED}、不计数），按等价重构
 *       收敛进组件；TOTP authenticator 缺失/secret 为空按绑定级口径返回 false（登录级原
 *       路径同样落到调用方 MFA_FAIL + 失败计数，错误码等价）。</li>
 *   <li><b>TOTP 防重放窗口统一推进内聚</b>（调用方不可选）：任何场景验证成功都更新
 *       {@code lastVerifiedWindow}/{@code lastVerifiedAt}——防同一 30s 窗口码跨场景重放
 *       （如先过操作级再过登录级）。全部调用点收敛后本组件是唯一推进路径。</li>
 *   <li><b>未知 mfaType fail-closed</b>：返回 false（不扩散为可验证因子；登录级调用方
 *       另有作废 challenge 语义）。</li>
 *   <li>恢复码分支不入组件（登录级专用，留在 LoginServiceImpl）。</li>
 *   <li>W14/W15 新因子只改本组件与白名单（设计 §5.3.0 清单），操作级自动受益。</li>
 * </ul>
 */
public class MfaFactorVerifier {

    @Inject
    @Nullable
    protected TOTPAuthenticator totpAuthenticator;

    @Inject
    @Nullable
    protected SmsCodeStore smsCodeStore;

    @Inject
    protected IDaoProvider daoProvider;

    /**
     * 校验第二因子。
     *
     * @param setting  用户 MFA setting（TOTP 分支读 secret + lastVerifiedWindow；SMS 分支读 userId）
     * @param mfaType  因子类型（以 challenge/调用上下文的 mfaType 为准，与 setting.mfaType
     *                 的一致性校验属调用方复核语义）
     * @param code     用户输入的验证码
     * @return 校验是否通过；TOTP 成功时窗口推进副作用内聚于此
     */
    public boolean verify(NopAuthMfaSetting setting, String mfaType, String code) {
        if (setting == null)
            return false;

        if (MFA_TYPE_TOTP.equals(mfaType)) {
            if (totpAuthenticator == null || StringHelper.isEmpty(setting.getSecret()))
                return false;
            // 确保 skew 与配置一致（一期 verifyTotp 口径）
            totpAuthenticator.setSkew(CFG_AUTH_MFA_TOTP_WINDOW_SKEW.get());
            long lastWindow = setting.getLastVerifiedWindow() == null ? -1L : setting.getLastVerifiedWindow();
            long window = totpAuthenticator.verify(setting.getSecret(), code, lastWindow);
            if (window < 0)
                return false;
            // 防重放：更新 lastVerifiedWindow（当前窗口严格大于历史才通过，已由 verify 保证）。
            // 组件内聚推进（调用方不可选）——唯一推进路径，防跨场景窗口码重放
            setting.setLastVerifiedWindow(window);
            setting.setLastVerifiedAt(new Timestamp(CoreMetrics.currentTimeMillis()));
            daoForSetting().updateEntityDirectly(setting);
            return true;
        }

        if (MFA_TYPE_SMS.equals(mfaType)) {
            if (smsCodeStore == null)
                return false;
            CodeVerifyResult r = smsCodeStore.verify(SMS_KEY_MFA + setting.getUserId(), code);
            if (r == CodeVerifyResult.EXPIRED) {
                // 两处既有调用点行为一致：EXPIRED 抛错（不进失败计数），等价重构收敛进组件
                throw new NopException(ERR_AUTH_SMS_CODE_EXPIRED);
            }
            return r == CodeVerifyResult.VALID;
        }

        // 未知 mfaType：fail-closed（白名单外值不扩散为可验证因子）
        return false;
    }

    private IEntityDao<NopAuthMfaSetting> daoForSetting() {
        return daoProvider.daoFor(NopAuthMfaSetting.class);
    }
}
