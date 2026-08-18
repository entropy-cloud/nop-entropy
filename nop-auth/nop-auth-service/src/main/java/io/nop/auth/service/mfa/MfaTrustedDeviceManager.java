/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mfa;

import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.time.CoreMetrics;
import io.nop.auth.dao.entity.NopAuthMfaTrustedDevice;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.commons.crypto.HashHelper;
import io.nop.commons.util.StringHelper;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT;
import static io.nop.auth.service.NopAuthConfigs.CFG_AUTH_MFA_TRUSTED_DEVICE_TTL_DAYS;

/**
 * 可信设备（记住此设备）共享组件（W15-impl，设计 §六）：指纹计算 + 豁免判定 + 登记 +
 * 撤销矩阵的统一落点——{@code LoginServiceImpl}（豁免判定/登记）与
 * {@code NopAuthUserBizModel}（撤销/管理 API）共用，防两处判定漂移。
 * <ul>
 *   <li><b>指纹算法</b>（§6.3）：自定义头 {@code X-Nop-Mfa-Device-Id}（大小写不敏感读，
 *       前端生成持久化 UUID，非秘密）+ {@code User-Agent} + {@code Accept-Language} 三输入
 *       {@code |} 连接 SHA-256 hex；device-id 缺失返回 null（降级正常 MFA，非错误）。
 *       拒绝 canvas/硬件/行为指纹与 IP（§6.4 隐私合规）。</li>
 *   <li><b>豁免判定</b>：查（userId, deviceHash）未过期行 → 命中更新 lastUsedAt（<b>不续
 *       expireAt</b>，固定窗口）→ 豁免。仅密码类 loginType（1/2/3/5）；信道路径 headers=null
 *       结构性跳过（§6.1 结论 3）。策略 {@code allowTrustedDevice=false} 由调用方在进入前
 *       短路（AND 合并消费，W13 evaluator 复合结果）。</li>
 *   <li><b>登记</b>（mfaVerify 成功路径，completeLogin 之前纯 DB 写）：同 hash（含过期行）
 *       upsert 覆盖刷新（expireAt=now+ttl-days，不受 max-count 限制）；新行需未过期行数
 *       &lt; max-count 否则返回 false（满员提示，非错误）；并发同 hash 撞 (userId, deviceHash)
 *       唯一约束归一为 update。登记失败不阻断登录（log + audit，不吞异常）。</li>
 *   <li><b>撤销矩阵</b>（§6.3）：到期惰性失效（行保留审计）/ 自助移除（物理删除，本人数据
 *       限定）/ 因子变更（unbindMfa/confirmMfa 成功全量删除）/ resetUserMfa 全量删除/
 *       策略禁豁免不删行（判定跳过，放宽后恢复）。</li>
 * </ul>
 */
public class MfaTrustedDeviceManager {

    static final Logger LOG = LoggerFactory.getLogger(MfaTrustedDeviceManager.class);

    /** 前端生成并持久化的设备标识头（非秘密、仅命名器；登记与豁免两侧同一契约）。 */
    public static final String HEADER_DEVICE_ID = "X-Nop-Mfa-Device-Id";

    /** 登记失败/满员的显式原因（审计与测试断言用）。 */
    public static final String REASON_NO_DEVICE_ID = "no-device-id";
    public static final String REASON_MAX_COUNT = "max-count";
    public static final String REASON_REGISTER_FAIL = "register-fail";

    @Inject
    protected IDaoProvider daoProvider;

    /** 审计服务（登记/移除/因子变更全量撤销事件——userName 非空列，W13 教训）。 */
    @Inject
    @Nullable
    protected IAuditService auditService;

    // ===================== 指纹计算（设计 §6.3） =====================

    /**
     * 三输入 SHA-256 hex 指纹：{@code deviceId|User-Agent|Accept-Language}。
     * device-id 缺失返回 null（无 device-id 不豁免——降级正常 MFA，非错误）。
     * 头读取大小写不敏感（{@code extractClientIp} 双大小写先例）。
     */
    public static String fingerprint(Map<String, Object> requestHeaders) {
        if (requestHeaders == null || requestHeaders.isEmpty())
            return null;
        String deviceId = header(requestHeaders, HEADER_DEVICE_ID);
        if (StringHelper.isEmpty(deviceId))
            return null;
        String userAgent = header(requestHeaders, "User-Agent");
        String acceptLanguage = header(requestHeaders, "Accept-Language");
        String input = deviceId + "|" + (userAgent == null ? "" : userAgent)
                + "|" + (acceptLanguage == null ? "" : acceptLanguage);
        return StringHelper.bytesToHex(HashHelper.sha256(input.getBytes(java.nio.charset.StandardCharsets.UTF_8), null));
    }

    /** 大小写不敏感读头。 */
    static String header(Map<String, Object> headers, String name) {
        Object v = headers.get(name);
        if (v != null)
            return v.toString();
        for (Map.Entry<String, Object> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name))
                return e.getValue() == null ? null : e.getValue().toString();
        }
        return null;
    }

    // ===================== 豁免判定（checkMfaRequired 内插入分支） =====================

    /**
     * 豁免判定：存在（userId, deviceHash）未过期行 → 更新 lastUsedAt（不续 expireAt）返回
     * true。调用方前置：headers 非空 + 密码类 loginType + {@code policy.allowTrustedDevice}。
     */
    public boolean isExempted(String userId, String deviceHash) {
        NopAuthMfaTrustedDevice trusted = findByHash(userId, deviceHash);
        if (trusted == null)
            return false;
        Timestamp now = new Timestamp(CoreMetrics.currentTimeMillis());
        if (trusted.getExpireAt() == null || !trusted.getExpireAt().after(now))
            return false; // 过期行惰性失效（行保留审计）
        trusted.setLastUsedAt(now);
        dao().updateEntityDirectly(trusted);
        LOG.info("nop.auth.mfa-trusted-device-exempt:userId={}", userId);
        return true;
    }

    // ===================== 登记（mfaVerify 成功路径，completeLogin 之前） =====================

    /**
     * 登记可信设备（登记失败不阻断登录——log + audit，不吞异常）。
     * <ul>
     *   <li>同 hash（含过期行）upsert 覆盖刷新（expireAt=now+ttl-days，不受 max-count 限制）。</li>
     *   <li>新行需未过期行数 &lt; max-count，否则 false（满员提示；拒绝静默 LRU 淘汰，§6.4）。</li>
     *   <li>并发同 hash insert 撞 (userId, deviceHash) 唯一约束归一为 update（不外抛）。</li>
     * </ul>
     *
     * @return 登记原因（null = 未登记：无 device-id / 满员 / 失败）
     */
    public String register(String userId, Map<String, Object> requestHeaders) {
        String deviceHash = fingerprint(requestHeaders);
        if (deviceHash == null)
            return null; // 无 device-id：不登记（响应 false 提示）

        String tenantId = tenantOf(userId);
        NopAuthMfaTrustedDevice existing = findByHash(userId, deviceHash);
        if (existing != null) {
            // 同 hash（含过期行）复活/覆盖刷新——不受 max-count 限制
            touchRegister(existing, requestHeaders);
            auditRegister(userId, existing.getSid(), true, "refresh");
            return "refresh";
        }

        if (countUnexpired(userId) >= CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT.get()) {
            LOG.info("nop.auth.mfa-trusted-device-max-count:userId={},maxCount={}",
                    userId, CFG_AUTH_MFA_TRUSTED_DEVICE_MAX_COUNT.get());
            auditRegister(userId, null, false, REASON_MAX_COUNT);
            return null; // 满员：显式 false 提示（不静默 LRU 淘汰）
        }

        try {
            NopAuthMfaTrustedDevice row = dao().newEntity();
            row.setUserId(userId);
            row.setDeviceHash(deviceHash);
            row.setTenantId(tenantId);
            initRegister(row, requestHeaders);
            dao().saveEntityDirectly(row);
            auditRegister(userId, row.getSid(), true, "register");
            LOG.info("nop.auth.mfa-trusted-device-registered:userId={}", userId);
            return "register";
        } catch (Exception e) {
            // 并发同 hash insert 撞唯一约束 → 归一为 update（不外抛）；其余失败登记不阻断登录
            NopAuthMfaTrustedDevice concurrent = findByHash(userId, deviceHash);
            if (concurrent != null) {
                touchRegister(concurrent, requestHeaders);
                auditRegister(userId, concurrent.getSid(), true, "concurrent-normalized");
                return "concurrent-normalized";
            }
            LOG.error("nop.auth.mfa-trusted-device-register-fail:userId={}", userId, e);
            auditRegister(userId, null, false, REASON_REGISTER_FAIL);
            return null;
        }
    }

    private void initRegister(NopAuthMfaTrustedDevice row, Map<String, Object> headers) {
        Timestamp now = new Timestamp(CoreMetrics.currentTimeMillis());
        row.setExpireAt(fixedWindowEnd());
        row.setLastUsedAt(now);
        row.setDeviceName(defaultDeviceName(headers));
    }

    private void touchRegister(NopAuthMfaTrustedDevice row, Map<String, Object> headers) {
        Timestamp now = new Timestamp(CoreMetrics.currentTimeMillis());
        row.setExpireAt(fixedWindowEnd());
        row.setLastUsedAt(now);
        if (StringHelper.isEmpty(row.getDeviceName()))
            row.setDeviceName(defaultDeviceName(headers));
        dao().updateEntityDirectly(row);
    }

    /** 固定窗口终点：now + ttl-days（命中不续期——§6.4 拒绝滚动续期）。 */
    private static Timestamp fixedWindowEnd() {
        long ttlMs = CFG_AUTH_MFA_TRUSTED_DEVICE_TTL_DAYS.get() * 24L * 3600_000L;
        return new Timestamp(CoreMetrics.currentTimeMillis() + ttlMs);
    }

    /** 缺省设备命名：UA 摘要（截断 60 字符；UA 缺失回退 "Trusted Device"）。 */
    static String defaultDeviceName(Map<String, Object> headers) {
        String ua = header(headers, "User-Agent");
        if (StringHelper.isEmpty(ua))
            return "Trusted Device";
        return ua.length() > 60 ? ua.substring(0, 60) : ua;
    }

    // ===================== 撤销矩阵 + 管理 =====================

    /** 自助移除（物理删除，本人数据限定——越权/不存在归一"不存在"，返回是否删除）。 */
    public boolean removeBySid(String sid, String userId) {
        NopAuthMfaTrustedDevice row = StringHelper.isEmpty(sid) ? null : dao().getEntityById(sid);
        if (row == null || !userId.equals(row.getUserId()))
            return false;
        dao().deleteEntity(row);
        auditRevoke(userId, sid, "user-removed");
        return true;
    }

    /**
     * 管理端按 sid 物理删除（A2-followup-1 Phase 1 carve-out）：调用方（
     * {@code NopAuthMfaTrustedDeviceBizModel.delete}）负责 admin 校验；本方法补 revoke 族
     * 审计事件（reason=admin-removed，与自助移除/因子变更撤销同一事件面）。行不存在返回
     * false（归一"不存在"语义）。
     */
    public boolean removeBySidForAdmin(String sid) {
        NopAuthMfaTrustedDevice row = StringHelper.isEmpty(sid) ? null : dao().getEntityById(sid);
        if (row == null)
            return false;
        String userId = row.getUserId();
        dao().deleteEntity(row);
        auditRevoke(userId, sid, "admin-removed");
        return true;
    }

    /** 全量撤销（因子变更 unbindMfa/confirmMfa 换绑、resetUserMfa；策略禁不删行——本方法不被调用）。 */
    public int removeAllForUser(String userId, String reason) {
        int removed = 0;
        for (NopAuthMfaTrustedDevice row : listForUser(userId)) {
            dao().deleteEntity(row);
            removed++;
        }
        if (removed > 0)
            auditRevoke(userId, null, reason);
        return removed;
    }

    /** 该用户全部行（含过期——管理端展示 + 自助清理）。 */
    public List<NopAuthMfaTrustedDevice> listForUser(String userId) {
        NopAuthMfaTrustedDevice example = dao().newEntity();
        example.setUserId(userId);
        return dao().findAllByExample(example);
    }

    /** 未过期行计数（max-count 口径——仅计未过期行）。 */
    public long countUnexpired(String userId) {
        Timestamp now = new Timestamp(CoreMetrics.currentTimeMillis());
        return listForUser(userId).stream()
                .filter(r -> r.getExpireAt() != null && r.getExpireAt().after(now))
                .count();
    }

    // ===================== 内部辅助 =====================

    private NopAuthMfaTrustedDevice findByHash(String userId, String deviceHash) {
        NopAuthMfaTrustedDevice example = dao().newEntity();
        example.setUserId(userId);
        example.setDeviceHash(deviceHash);
        List<NopAuthMfaTrustedDevice> found = dao().findAllByExample(example);
        return found.isEmpty() ? null : found.get(0);
    }

    private String tenantOf(String userId) {
        NopAuthUser user = daoProvider.daoFor(NopAuthUser.class).getEntityById(userId);
        return user == null ? null : user.getTenantId();
    }

    private IEntityDao<NopAuthMfaTrustedDevice> dao() {
        return daoProvider.daoFor(NopAuthMfaTrustedDevice.class);
    }

    // ===================== 审计（userName 非空列——W13 教训） =====================

    private void auditRegister(String userId, String sid, boolean success, String detail) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation("mfa:trusted-device");
        audit.setDescription(success ? "mfa:trusted-device-registered" : "mfa:trusted-device-register-fail");
        audit.setResultStatus(success ? 200 : 400);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userId);
        audit.setUserName(userNameOf(userId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "trusted-device-register");
        data.put("sid", sid);
        data.put("detail", detail);
        audit.setRequestData(io.nop.core.lang.json.JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    private void auditRevoke(String userId, String sid, String reason) {
        if (auditService == null)
            return;
        AuditRequest audit = new AuditRequest();
        audit.setOperation("mfa:trusted-device");
        audit.setDescription("mfa:trusted-device-revoked");
        audit.setResultStatus(200);
        audit.setActionTime(new Timestamp(CoreMetrics.currentTimeMillis()));
        audit.setUserId(userId);
        audit.setUserName(userNameOf(userId));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", "trusted-device-revoke");
        data.put("sid", sid);
        data.put("reason", reason);
        audit.setRequestData(io.nop.core.lang.json.JsonTool.stringify(data));
        auditService.saveAudit(audit);
    }

    private String userNameOf(String userId) {
        NopAuthUser user = daoProvider.daoFor(NopAuthUser.class).getEntityById(userId);
        return user == null ? userId : (StringHelper.isEmpty(user.getUserName()) ? userId : user.getUserName());
    }
}
