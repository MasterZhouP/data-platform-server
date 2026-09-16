package com.ruoyi.integration.client.u8.config;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface U8GatewayMapper
{
    U8GatewayConnection findConnection(@Param("connectionKey") String connectionKey);
    List<U8GatewayConnection> listConnections();
    U8GatewayRevision findRevision(@Param("revisionId") String revisionId);
    U8GatewaySecret findSecret(@Param("secretId") String secretId);
    String findLastTest(@Param("revisionId") String revisionId);
    int insertConnection(U8GatewayConnection connection);
    int insertRevision(U8GatewayRevision revision);
    int insertSecret(U8GatewaySecret secret);
    int updateLastTest(@Param("revisionId") String revisionId, @Param("lastTestJson") String lastTestJson);
    int updateDraftPointer(@Param("connectionKey") String connectionKey, @Param("draftRevisionId") String draftRevisionId,
            @Param("expectedRowVersion") long expectedRowVersion);
    int activatePointer(@Param("connectionKey") String connectionKey, @Param("activeRevisionId") String activeRevisionId,
            @Param("expectedActiveId") String expectedActiveId);
    int disable(@Param("connectionKey") String connectionKey);
}
