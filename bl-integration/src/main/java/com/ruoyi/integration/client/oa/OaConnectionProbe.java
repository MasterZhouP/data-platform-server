package com.ruoyi.integration.client.oa;

import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration.datasource.oa", name = "enabled", havingValue = "true")
public class OaConnectionProbe
{
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OaConnectionProbe(@Qualifier("oaJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate)
    {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isReachable()
    {
        Integer value = jdbcTemplate.queryForObject("SELECT 1", Map.of(), Integer.class);
        return Integer.valueOf(1).equals(value);
    }
}
