package com.ruoyi.integration.sync.salesoutbound.link;

import com.ruoyi.integration.client.oa.OaProcessRef;

public interface OaProcessLinkRepository
{
    OaProcessLink findLatest(String taskCode, String businessKey);

    OaProcessLink createCreating(String taskCode, String businessKey, String u8Id, Long previousLinkId);

    void updateState(Long linkId, ProcessLinkState state);

    void activate(Long linkId, OaProcessRef process);
}
