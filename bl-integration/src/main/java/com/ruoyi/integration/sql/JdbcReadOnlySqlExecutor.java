package com.ruoyi.integration.sql;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.configuration.ConfigurationException;
import com.ruoyi.integration.datasource.runtime.DatasourceLease;
import com.ruoyi.integration.datasource.runtime.DatasourceRegistry;
import com.ruoyi.integration.oatou8.config.ResultCardinality;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Shared read-only query boundary for OA-to-U8 data preparation and U8 result lookup.
 * The executor never accepts a JDBC URL or statement type from task JSON; it selects a registered read-only datasource.
 */
public class JdbcReadOnlySqlExecutor implements ReadOnlySqlExecutor
{
    private static final int MAX_ROWS = 1000;

    private final Map<String, NamedParameterJdbcTemplate> templates;
    private final DatasourceRegistry datasourceRegistry;
    private final ObjectMapper json;

    public JdbcReadOnlySqlExecutor(Map<String, NamedParameterJdbcTemplate> templates)
    {
        this(templates, new ObjectMapper());
    }

    public JdbcReadOnlySqlExecutor(Map<String, NamedParameterJdbcTemplate> templates, ObjectMapper json)
    {
        this.templates = Map.copyOf(templates);
        this.datasourceRegistry = null;
        this.json = json;
    }

    /**
     * Creates an executor backed by the managed datasource registry. Each query obtains a fixed pool revision
     * for its own duration, matching the lease semantics used by reference queries.
     */
    public JdbcReadOnlySqlExecutor(DatasourceRegistry datasourceRegistry, ObjectMapper json)
    {
        this.templates = Map.of();
        this.datasourceRegistry = datasourceRegistry;
        this.json = json;
    }

    @Override
    public ReadOnlySqlResult execute(String datasourceKey, String sql, ResultCardinality cardinality,
            Map<String, Object> parameters)
    {
        if (datasourceRegistry != null)
        {
            return executeManaged(datasourceKey, sql, cardinality, parameters);
        }
        NamedParameterJdbcTemplate template = templates.get(datasourceKey);
        if (template == null)
        {
            throw new SqlExecutionException("DATASOURCE_UNAVAILABLE", "声明的数据源不可用: " + datasourceKey);
        }
        return execute(template, sql, cardinality, parameters);
    }

    private ReadOnlySqlResult executeManaged(String datasourceKey, String sql, ResultCardinality cardinality,
            Map<String, Object> parameters)
    {
        try (DatasourceLease lease = datasourceRegistry.acquire(datasourceKey))
        {
            return execute(new NamedParameterJdbcTemplate(lease.dataSource()), sql, cardinality, parameters);
        }
        catch (ConfigurationException unavailable)
        {
            throw new SqlExecutionException(unavailable.code(), unavailable.getMessage());
        }
    }

    private ReadOnlySqlResult execute(NamedParameterJdbcTemplate template, String sql, ResultCardinality cardinality,
            Map<String, Object> parameters)
    {
        try
        {
            List<ObjectNode> rows = template.query(sql, new MapSqlParameterSource(parameters), rowMapper());
            if (rows.size() > MAX_ROWS)
            {
                throw new SqlExecutionException("QUERY_ROW_LIMIT_EXCEEDED", "只读查询结果超过平台行数上限");
            }
            return normalize(cardinality, rows);
        }
        catch (SqlExecutionException ex)
        {
            throw ex;
        }
        catch (RuntimeException ex)
        {
            throw new SqlExecutionException("READ_QUERY_FAILED", "只读查询执行失败");
        }
    }

    private RowMapper<ObjectNode> rowMapper()
    {
        return (resultSet, rowNumber) -> {
            ResultSetMetaData metadata = resultSet.getMetaData();
            ObjectNode row = json.createObjectNode();
            for (int index = 1; index <= metadata.getColumnCount(); index++)
            {
                String name = metadata.getColumnLabel(index);
                Object value = resultSet.getObject(index);
                row.set(name, value == null ? NullNode.getInstance() : json.valueToTree(value));
            }
            return row;
        };
    }

    private ReadOnlySqlResult normalize(ResultCardinality cardinality, List<ObjectNode> rows)
    {
        return switch (cardinality)
        {
            case LIST -> new ReadOnlySqlResult(cardinality, list(rows));
            case ONE -> one(rows);
            case SCALAR -> scalar(rows);
        };
    }

    private ArrayNode list(List<ObjectNode> rows)
    {
        ArrayNode values = json.createArrayNode();
        rows.forEach(values::add);
        return values;
    }

    private ReadOnlySqlResult one(List<ObjectNode> rows)
    {
        if (rows.size() > 1)
        {
            throw new SqlExecutionException("RESULT_CARDINALITY_MISMATCH", "查询应返回一行，但实际返回多行");
        }
        return new ReadOnlySqlResult(ResultCardinality.ONE, rows.isEmpty() ? NullNode.getInstance() : rows.get(0));
    }

    private ReadOnlySqlResult scalar(List<ObjectNode> rows)
    {
        if (rows.size() > 1 || (!rows.isEmpty() && rows.get(0).size() != 1))
        {
            throw new SqlExecutionException("RESULT_CARDINALITY_MISMATCH", "查询应返回一个标量值");
        }
        if (rows.isEmpty())
        {
            return new ReadOnlySqlResult(ResultCardinality.SCALAR, NullNode.getInstance());
        }
        return new ReadOnlySqlResult(ResultCardinality.SCALAR, rows.get(0).elements().next());
    }
}
