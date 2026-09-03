/**
 * 集成任务定义及触发上下文的公共边界，不负责具体调度器或业务处理实现。
 * 区分任务定义与单次执行；不替代 bl-quartz 的调度职责，不反向依赖核心引擎。
 */
package com.ruoyi.integration.task;
