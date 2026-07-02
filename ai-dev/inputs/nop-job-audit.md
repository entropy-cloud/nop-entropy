1. 大量的状态判断和封装，需要抽取到Helper类中。比如如下方法
     private boolean isCancelableStatus(Integer fireStatus) {
        return fireStatus != null
                && (fireStatus == _NopJobCoreConstants.FIRE_STATUS_WAITING
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_DISPATCHING
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_RUNNING);
    }

    private boolean isRerunnableStatus(Integer fireStatus) {
        return fireStatus != null
                && (fireStatus == _NopJobCoreConstants.FIRE_STATUS_SUCCESS
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_FAILED
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_TIMEOUT
                || fireStatus == _NopJobCoreConstants.FIRE_STATUS_CANCELED);
    }

 另外状态作为整数是排序的，可以按照区间来判断。参见workflow中的处理。
 
2. addPartitionFilter增加到QueryBean类上。

     private void addPartitionFilter(QueryBean query, IntRangeSet partitions) {
        if (partitions == null || partitions.isEmpty()) {
            return;
        }

        List<TreeBean> rangeFilters = new ArrayList<>();
        for (IntRangeBean range : partitions.getRanges()) {
            rangeFilters.add(FilterBeans.between(PROP_NAME_partitionIndex, range.getOffset(), range.getLast()));
        }
        query.addFilter(FilterBeans.or(rangeFilters));
    }


3. updateTime/updateBy等应该由系统自动维护，不要手工设置。时间也是固定使用CoreMetrics上的时间。 业务层面需要的过滤字段应该单独定义，不要占用这两个字段。业务字段的时间可以采用数据库时间，但是最好是统一有一个接口来处理，避免写死依赖。

4. findAll的所有调用都要检查，一般是分批获取，参见lessons中的处理。

5. tryUpdateManyWithVersionCheck返回的结果是真正更新了的，这些记录才能使用。更新不成功的不应该使用。或者判断非readonly的才能继续下一步处理。

6. 以下方法抽取到DateHelper中，还有类似的可抽取的公共函数也放到各自的Helper中。Nop平台定义了DateHelper, StringHelper，FileHelper等公共帮助函数的汇聚类。

     private Long calculateDuration(Timestamp startTime, Timestamp endTime) {
        if (startTime == null || endTime == null) {
            return null;
        }
        return Math.max(endTime.getTime() - startTime.getTime(), 0L);
    }

7. 调用failFireWithoutSchedule这样的函数不能直接从ErrorCode获取Description，需要通过ErrorMessageManager绕一下执行国际化。

8. 不要有  long now = System.currentTimeMillis(); 这种调用。

9. JsonComponent上直接具有getValue函数，不用先读取Map再读取值

10. NopJobTask上是否应该补充completed, nextScheduleTime等字段，避免解析Json？

11. 明确有问题的，则需要代码中有一定的注释
来自 *AR-65 (adversarial review finding)*，记录在：
- 源头：ai-dev/audits/2026-06-04-adversarial-review-nop-job-r8/01-open-findings.md:350-371
- 修复计划：ai-dev/archived/2026-06/110-nop-job-r8-and-deep-audit-remediation.md:132
- 架构基线：ai-dev/design/nop-job/01-architecture-baseline.md:324-342
核心原因有三：
1. 计数器一致性：NopJobSchedule 的 activeFireCount/fireCount/totalFireCount 只在 IJobFireStore 的事务方法中维护，直接 delete 绕过这些方法导致计数器永久漂移。
2. 引擎生命周期绕过：删除一个 RUNNING 状态的 fire 不会触发 JobCompletionProcessor，activeFireCount 永远不减。
3. 架构原则：Fire 应通过领域命令（cancelFire、rerunFire）管理，而非直接 CRUD。

activeFireCount有没有必要维护？是否根据数据自己去统计，避免出现这种问题？

12. baseline = scheduleDao().requireEntityById(schedule.getJobScheduleId()); 并不会重新获取，如果缓存中已经存在的话。存在refresh函数用于刷新。或者evict后再get。
16. 如果只是累加，一般优先考虑使用mapper执行eql语句来更新，而不是tryUpdateWithVersionCheck
 1. 更新规则真的是“当前值 + delta”
2. 不依赖复杂领域校验
3. 不要求基于旧状态做条件分支
4. 不需要 ORM 实体生命周期钩子来参与这次更新		

13. batchLoadFire根据id列表批量获取，Dao上没有现成的方法吗

14. 现在IOrmEntityDao增加了 boolean tryUpdateWithVersionCheck(T entity) 方法，不需要用列表那个方法了。

15. 以下代码是怎么回事？怎么还有自己parse的情况？

 Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
        if (scheduleParams != null) {
            return scheduleParams;
        }

        if (schedule.getJobParams() != null && !schedule.getJobParams().isEmpty()) {
            Map<String, Object> parsed = JsonTool.parseMap(schedule.getJobParams());
            if (parsed != null) {
                return parsed;
            }
        }
		
