/**
 * OA 到 U8 的 PushPipeline 边界，供推单、推凭证和审核共用执行编排。
 * 编排通过公共能力读取数据、调用客户端并记录执行结果，不内嵌具体业务 SQL 或协议实现。
 */
package com.ruoyi.integration.pipeline.push;
