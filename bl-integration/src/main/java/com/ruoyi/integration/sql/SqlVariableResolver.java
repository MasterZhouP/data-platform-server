package com.ruoyi.integration.sql;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ruoyi.integration.oatou8.ExecutionVariableContext;

/** Resolves declared parameter aliases without allowing SQL to navigate arbitrary runtime objects. */
public class SqlVariableResolver
{
    public Map<String, Object> resolve(Map<String, String> bindings, ExecutionVariableContext context)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        bindings.forEach((parameter, variable) -> {
            ExecutionVariableContext.VariableValue value = context.lookup(variable);
            if (!value.defined())
            {
                throw new SqlExecutionException("SQL_VARIABLE_NOT_FOUND", "SQL参数引用的任务变量不存在: " + variable);
            }
            if (!value.scalar())
            {
                throw new SqlExecutionException("SQL_VARIABLE_NOT_SCALAR", "SQL参数只能绑定标量任务变量: " + variable);
            }
            values.put(parameter, value.value());
        });
        return Map.copyOf(values);
    }
}
