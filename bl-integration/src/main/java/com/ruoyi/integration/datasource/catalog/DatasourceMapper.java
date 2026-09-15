package com.ruoyi.integration.datasource.catalog;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DatasourceMapper {
    DatasourceRow findCatalog(@Param("datasourceKey") String datasourceKey);

    List<DatasourceRow> listCatalog();

    DatasourceRevision findRevision(@Param("revisionId") String revisionId);

    String findLastTest(@Param("revisionId") String revisionId);

    DatasourceSecretRow findSecret(@Param("secretId") String secretId);

    int insertCatalog(DatasourceRow row);

    int insertRevision(DatasourceRevision revision);

    int insertSecret(DatasourceSecretRow secret);

    int updateLastTest(@Param("revisionId") String revisionId, @Param("lastTestJson") String lastTestJson);

    int updateDraftPointer(@Param("datasourceKey") String datasourceKey,
                           @Param("draftRevisionId") String draftRevisionId,
                           @Param("expectedRowVersion") long expectedRowVersion);

    int activatePointer(@Param("datasourceKey") String datasourceKey,
                        @Param("activeRevisionId") String activeRevisionId,
                        @Param("expectedActiveId") String expectedActiveId);

    int disable(@Param("datasourceKey") String datasourceKey);
}
