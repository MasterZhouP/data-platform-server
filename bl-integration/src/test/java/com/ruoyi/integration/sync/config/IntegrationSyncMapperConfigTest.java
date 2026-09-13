package com.ruoyi.integration.sync.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import com.ruoyi.integration.sync.salesoutbound.link.OaProcessLinkMapper;
import com.ruoyi.integration.sync.schedule.SyncCursorMapper;
import com.ruoyi.integration.sync.schedule.SyncEventMapper;

class IntegrationSyncMapperConfigTest
{
    @Test
    void explicitlyScansBothIntegrationMapperPackages()
    {
        MapperScan scan = IntegrationSyncMapperConfig.class.getAnnotation(MapperScan.class);
        List<String> packages = List.of(scan.basePackages());

        assertTrue(packages.contains(OaProcessLinkMapper.class.getPackageName()));
        assertTrue(packages.contains(SyncCursorMapper.class.getPackageName()));
        assertTrue(OaProcessLinkMapper.class.isAnnotationPresent(Mapper.class));
        assertTrue(SyncCursorMapper.class.isAnnotationPresent(Mapper.class));
        assertTrue(SyncEventMapper.class.isAnnotationPresent(Mapper.class));
    }
}
