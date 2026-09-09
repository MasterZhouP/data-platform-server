package com.ruoyi.integration.reference.catalog;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReferenceTaskMapper {
    List<ReferenceTaskRow> list();
    ReferenceTaskRow find(@Param("taskCode") String taskCode);
    int insert(ReferenceTaskRow row);
    int update(@Param("row") ReferenceTaskRow row, @Param("expectedVersion") String expectedVersion);
}
