package com.ruoyi.integration.datasource.catalog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DatasourceMapper {
    DatasourceRow findCatalog(@Param("datasourceKey") String datasourceKey);

    DatasourceRevision findRevision(@Param("revisionId") String revisionId);

    int insertCatalog(DatasourceRow row);

    int insertRevision(DatasourceRevision revision);

    int updateDraftPointer(@Param("datasourceKey") String datasourceKey,
                           @Param("draftRevisionId") String draftRevisionId,
                           @Param("expectedRowVersion") long expectedRowVersion);
}
