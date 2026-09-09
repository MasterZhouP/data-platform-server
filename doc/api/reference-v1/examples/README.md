# 模拟样例使用说明

本目录均为模拟数据，不是实际 U8 查询结果或 OA SDK 返回。真实环境接入前，需按实际 SQL 类型验证并发布元数据。

| 文件 | 用途 |
| --- | --- |
| status.response.json | 平台连接及服务认证成功 |
| tasks.response.json | 可调用任务目录 |
| metadata.response.json | 完整字段、默认显示/筛选及能力 |
| query.request.json | 首次查询的请求体；请求 ID 另放请求头 |
| query.response.json | 完整字段、两条相同编码但换算率不同的模拟行 |
| query-empty.response.json | 无匹配记录 |
| query-version-conflict.response.json | 元数据版本已变化，HTTP 409 |
| query-timeout.response.json | 查询超时，HTTP 504 |
| unauthorized.response.json | 认证失败，HTTP 401 |
| plugin-binding.example.json | 插件内部配置示意，不是平台 HTTP 请求 |

同事可以将上级目录的 openapi.json 导入支持 OpenAPI 3.0 的接口工具，使用接口内嵌示例或本目录文件建立模拟响应。本文档包不包含已经启动的模拟服务，也没有创建任何外部服务账号。

样例中的字段 ID、组织代码、元数据版本及执行 ID 只供测试。保持编码字符串前导零，不按 cInvCode 合并这两行，不把模拟部门代码直接当作 OA 组织 ID。

没有附加的“回填成功回调”接口：插件选择整行、在 OA 内部按保存的映射回填，平台查询记录只说明查询执行结果。
