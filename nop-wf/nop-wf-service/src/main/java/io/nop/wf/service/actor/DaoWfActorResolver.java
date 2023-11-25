package io.nop.wf.service.actor;

import io.nop.auth.dao.entity.NopAuthDept;
import io.nop.auth.dao.entity.NopAuthGroup;
import io.nop.auth.dao.entity.NopAuthRole;
import io.nop.auth.dao.entity.NopAuthUser;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.wf.api.actor.IWfActor;
import io.nop.wf.api.actor.IWfActorResolver;
import io.nop.wf.api.actor.WfActorBean;
import io.nop.wf.api.actor.WfUserActorBean;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DaoWfActorResolver implements IWfActorResolver {
    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    protected IEntityDao<NopAuthUser> userDao() {
        return daoProvider.daoFor(NopAuthUser.class);
    }

    protected IEntityDao<NopAuthDept> deptDao() {
        return daoProvider.daoFor(NopAuthDept.class);
    }

    protected IEntityDao<NopAuthGroup> groupDao() {
        return daoProvider.daoFor(NopAuthGroup.class);
    }

    protected IEntityDao<NopAuthRole> roleDao() {
        return daoProvider.daoFor(NopAuthRole.class);
    }

    @Override
    public IWfActor resolveUser(String userId) {
        NopAuthUser user = userDao().getEntityById(userId);
        if (user == null) {
            return null;
        }
        return buildUserActor(user);
    }

    public IWfActor resolveDept(String deptId) {
        NopAuthDept dept = deptDao().getEntityById(deptId);
        if (dept == null)
            return null;

        WfActorBean actor = new WfActorBean();
        actor.setActorType(IWfActor.ACTOR_TYPE_DEPT);
        actor.setActorId(dept.getDeptId());
        actor.setActorName(dept.getDeptName());
        actor.setDeptId(dept.getDeptId());
        actor.setUsersLoader(() -> buildUserActors(getDeptUsers(dept)));
        return actor;
    }

    protected List<WfUserActorBean> buildUserActors(Collection<NopAuthUser> users) {
        return users.stream().map(this::buildUserActor).collect(Collectors.toList());
    }

    protected WfUserActorBean buildUserActor(NopAuthUser user) {
        WfUserActorBean actor = new WfUserActorBean();
        actor.setActorId(user.getUserId());
        actor.setActorName(user.getNickName());
        actor.setDeptId(user.getDeptId());
        return actor;
    }

    protected Collection<NopAuthUser> getDeptUsers(NopAuthDept dept) {
        return dept.getDeptUsers();
    }

    protected Collection<NopAuthUser> getGroupUsers(NopAuthGroup group) {
        return group.getRelatedUserList();
    }

    protected Collection<NopAuthUser> getRoleUsers(NopAuthRole role) {
        return role.getRelatedUserList();
    }

    public IWfActor resolveGroup(String groupId, String deptId) {
        NopAuthGroup group = groupDao().getEntityById(groupId);
        if (group == null)
            return null;

        WfActorBean actor = new WfActorBean();
        actor.setActorType(IWfActor.ACTOR_TYPE_GROUP);
        actor.setActorId(group.getGroupId());
        actor.setActorName(group.getName());
        actor.setDeptId(deptId);
        actor.setUsersLoader(() -> buildUserActors(getGroupUsers(group)));
        return actor;
    }

    public IWfActor resolveRole(String roleId, String deptId) {
        NopAuthRole role = roleDao().getEntityById(roleId);
        if (role == null)
            return null;

        WfActorBean actor = new WfActorBean();
        actor.setActorType(IWfActor.ACTOR_TYPE_ROLE);
        actor.setActorId(role.getRoleId());
        actor.setActorName(role.getRoleName());
        actor.setDeptId(deptId);
        actor.setUsersLoader(() -> buildUserActors(getRoleUsers(role)));
        return actor;
    }

    @Override
    public IWfActor resolveActor(String actorType, String actorId, String deptId) {
        if (IWfActor.ACTOR_TYPE_USER.equals(actorType))
            return resolveUser(actorId);

        if (IWfActor.ACTOR_TYPE_DEPT.equals(actorType))
            return resolveDept(actorId);

        if (IWfActor.ACTOR_TYPE_GROUP.equals(actorType))
            return resolveGroup(actorId, deptId);

        if (IWfActor.ACTOR_TYPE_ROLE.equals(actorType))
            return resolveRole(actorId, deptId);

        return null;
    }
}
