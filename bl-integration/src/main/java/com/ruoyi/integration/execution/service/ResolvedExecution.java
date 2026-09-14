package com.ruoyi.integration.execution.service;

import com.ruoyi.integration.execution.domain.IntegrationExecution;
import com.ruoyi.integration.taskdefinition.PublishedTaskRevision;

/**
 * 将执行记录与其受理时锁定的任务修订组合起来。
 * 运行器只使用这里的修订，不会因管理员之后发布新版本而改变当前单据的处理路径。
 */
public record ResolvedExecution(IntegrationExecution execution, PublishedTaskRevision revision)
{
}
