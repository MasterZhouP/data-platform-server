package com.ruoyi.web.controller.integration.reference;

import javax.sql.DataSource;
import com.ruoyi.integration.reference.engine.ReferenceDataSources;
import com.ruoyi.integration.reference.model.ReferenceException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;

@Configuration
@MapperScan(basePackages = "com.ruoyi.integration.reference.catalog", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class ReferenceRuntimeConfig
{
    @Bean
    public ReferenceDataSources referenceDataSources(@Qualifier("u8DataSource") ObjectProvider<DataSource> u8)
    {
        return key -> {
            if (!"u8".equals(key)) throw new ReferenceException("DATASOURCE_UNAVAILABLE", 503, "参照数据源尚未配置");
            DataSource source = u8.getIfAvailable();
            if (source == null) throw new ReferenceException("DATASOURCE_UNAVAILABLE", 503, "U8 测试数据源尚未启用，请先完成连接配置");
            return source;
        };
    }
}
