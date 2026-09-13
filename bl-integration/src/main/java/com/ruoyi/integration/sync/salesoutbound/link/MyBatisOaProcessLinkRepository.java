package com.ruoyi.integration.sync.salesoutbound.link;

import java.util.Date;
import com.ruoyi.integration.client.oa.OaProcessRef;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MyBatisOaProcessLinkRepository implements OaProcessLinkRepository
{
    private final OaProcessLinkMapper mapper;

    public MyBatisOaProcessLinkRepository(OaProcessLinkMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public OaProcessLink findLatest(String taskCode, String businessKey)
    {
        return mapper.selectLatest(taskCode, businessKey);
    }

    @Override
    @Transactional
    public OaProcessLink createCreating(String taskCode, String businessKey, String u8Id, Long previousLinkId)
    {
        OaProcessLink latest = mapper.selectLatestForUpdate(taskCode, businessKey);
        OaProcessLink link = new OaProcessLink();
        link.setTaskCode(taskCode);
        link.setBusinessKey(businessKey);
        link.setU8Id(u8Id);
        link.setState(ProcessLinkState.CREATING);
        link.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        link.setPreviousLinkId(previousLinkId);
        Date now = new Date();
        link.setCreateTime(now);
        link.setUpdateTime(now);
        if (mapper.insert(link) != 1)
        {
            throw new IllegalStateException("创建OA流程关联记录失败");
        }
        return link;
    }

    @Override
    @Transactional
    public void updateState(Long linkId, ProcessLinkState state)
    {
        if (mapper.updateState(linkId, state) != 1)
        {
            throw new IllegalStateException("更新OA流程关联状态失败: " + linkId);
        }
    }

    @Override
    @Transactional
    public void activate(Long linkId, OaProcessRef process)
    {
        if (mapper.activate(linkId, process) != 1)
        {
            throw new IllegalStateException("保存OA流程标识失败: " + linkId);
        }
    }
}
