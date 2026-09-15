package com.ruoyi.integration.client.oa.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OaRestMapper {
    OaRestConnection findConnection(@Param("connectionKey") String connectionKey);

    List<OaRestConnection> listConnections();

    OaRestRevision findRevision(@Param("revisionId") String revisionId);

    OaRestSecret findSecret(@Param("secretId") String secretId);

    String findLastTest(@Param("revisionId") String revisionId);

    int insertConnection(OaRestConnection connection);

    int insertRevision(OaRestRevision revision);

    int insertSecret(OaRestSecret secret);

    int updateLastTest(@Param("revisionId") String revisionId, @Param("lastTestJson") String lastTestJson);

    int updateDraftPointer(@Param("connectionKey") String connectionKey,
                           @Param("draftRevisionId") String draftRevisionId,
                           @Param("expectedRowVersion") long expectedRowVersion);

    int activatePointer(@Param("connectionKey") String connectionKey,
                        @Param("activeRevisionId") String activeRevisionId,
                        @Param("expectedActiveId") String expectedActiveId);

    int disable(@Param("connectionKey") String connectionKey);
}
