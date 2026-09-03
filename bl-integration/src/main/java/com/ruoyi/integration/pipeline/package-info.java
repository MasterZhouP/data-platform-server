/**
 * 集成流程编排边界。push 与 sync 分别组织各自方向的流程，彼此不直接依赖。
 * 参照查询独立于有写入副作用的流程；本包不预设统一执行器或接口签名。
 */
package com.ruoyi.integration.pipeline;
