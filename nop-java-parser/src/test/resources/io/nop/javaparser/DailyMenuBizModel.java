import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.RequestBean;
import io.nop.api.core.annotations.biz.Mock;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.dao.api.IEntityDao;
import io.nop.api.core.beans.FilterBeans;
import java.time.LocalDate;
import java.util.List;

@BizModel("DailyMenu")
public class DailyMenuBizModel extends CrudBizModel<DailyMenu> {

    public DailyMenuBizModel() {
        super(DailyMenu.class);
    }

    @BizMutation
    public DailyMenu generateDailyMenu(@RequestBean GenerateDailyMenuRequest request) {
        // Step 1: 验证前置条件
        validatePreconditions(request);

        // Step 2: 检查是否已存在相同日期的菜单
        checkExistingMenu(request);

        // Step 3: 获取基础菜单
        BaseMenu baseMenu = getBaseMenu(request.getId());

        // Step 4: 创建每日菜单
        DailyMenu dailyMenu = createDailyMenu(request, baseMenu);

        // Step 5: 计算并设置预计物资消耗
        calculateMaterialConsumption(dailyMenu);

        return dailyMenu;
    }

    private void validatePreconditions(GenerateDailyMenuRequest request) {
        // 检查当前日期是否为结算日
        if (isSettlementDate(request.getDate())) {
            throw new RuntimeException("当前日期为结算日，不能生成菜单");
        }

        // 检查基础菜单是否存在
        if (request.getId() == null) {
            throw new RuntimeException("基础菜单ID不能为空");
        }
    }

    private void checkExistingMenu(GenerateDailyMenuRequest request) {
        QueryBean query = new QueryBean();
        query.addFilterCondition(FilterBeans.eq("date", request.getDate()));
        query.addFilter(FilterBeans.eq("companyId", request.getCompanyId()));

        List<DailyMenu> existingMenus = entityDao().findAllByQuery(query);
        if (!existingMenus.isEmpty()) {
            throw new RuntimeException("该日期菜单已存在");
        }
    }

    private BaseMenu getBaseMenu(Long baseMenuId) {
        IEntityDao<BaseMenu> baseMenuDao = orm().daoFor(BaseMenu.class);
        BaseMenu baseMenu = baseMenuDao.getEntityById(baseMenuId);
        if (baseMenu == null) {
            throw new RuntimeException("基础菜单不存在");
        }
        return baseMenu;
    }

    private DailyMenu createDailyMenu(GenerateDailyMenuRequest request, BaseMenu baseMenu) {
        DailyMenu dailyMenu = new DailyMenu();
        dailyMenu.setDate(request.getDate());
        dailyMenu.setMenuId(request.getId());
        dailyMenu.setCompanyId(request.getCompanyId());
        dailyMenu.setStatus("draft");
        dailyMenu.setMenu(baseMenu);

        return entityDao().saveEntity(dailyMenu);
    }

    @Mock
    private void calculateMaterialConsumption(DailyMenu dailyMenu) {
        // @TODO 计算预计物资消耗的逻辑
        // 1. 根据基础菜单中的配料列表计算总消耗量
        // 2. 考虑预计就餐人数对消耗量的影响
        // 3. 可能需要生成库存出库记录
    }

    @Mock
    private boolean isSettlementDate(LocalDate date) {
        // @TODO 判断给定日期是否为结算日的逻辑
        return false;
    }

    // 辅助方法
    private IEntityDao<DailyMenu> entityDao() {
        return orm().daoFor(DailyMenu.class);
    }
}