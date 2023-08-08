package io.nop.api.core.auth;

/**
 * 检查是否允许访问指定业务对象，或者业务对象上的某个属性。
 * <p>
 * 内部可能通过IActionAuthChecker和IDataAuthChecker实现。
 */
public interface IBizAuthChecker {
    void checkAuth(String bizObjName, String objId, String fieldName, ISecurityContext context);
}
