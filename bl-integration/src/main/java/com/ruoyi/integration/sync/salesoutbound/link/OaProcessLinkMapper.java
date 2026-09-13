package com.ruoyi.integration.sync.salesoutbound.link;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.integration.client.oa.OaProcessRef;

@Mapper
public interface OaProcessLinkMapper
{
    OaProcessLink selectLatest(@Param("taskCode") String taskCode, @Param("businessKey") String businessKey);

    OaProcessLink selectLatestForUpdate(@Param("taskCode") String taskCode,
            @Param("businessKey") String businessKey);

    int insert(OaProcessLink link);

    int updateState(@Param("linkId") Long linkId, @Param("state") ProcessLinkState state);

    int activate(@Param("linkId") Long linkId, @Param("process") OaProcessRef process);
}
