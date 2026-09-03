/**
 * U8 参照查询的 ReferenceEngine 边界，负责查询条件、分页及参照结果的组织。
 * 保持只读查询语义，不复用推单或同步的写入流程，不承载具体 SQL。
 */
package com.ruoyi.integration.reference;
