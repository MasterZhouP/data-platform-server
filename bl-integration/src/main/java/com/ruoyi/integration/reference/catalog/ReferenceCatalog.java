package com.ruoyi.integration.reference.catalog;

import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.integration.reference.model.ReferenceException;
import com.ruoyi.integration.reference.model.ReferenceTask;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class ReferenceCatalog {
    private final ReferenceTaskMapper mapper;
    private final ObjectMapper json;

    public ReferenceCatalog(ReferenceTaskMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public List<ReferenceTask> list() {
        return mapper.list().stream().map(this::decode).toList();
    }

    public ReferenceTask get(String taskCode) {
        ReferenceTaskRow row = mapper.find(taskCode);
        // Defend exact identifier semantics even if an older DB uses a case-insensitive collation.
        if (row == null || !row.taskCode().equals(taskCode)) {
            throw new ReferenceException("TASK_NOT_FOUND", 404, "参照任务不存在", false);
        }
        return decode(row);
    }

    public ReferenceTask create(ReferenceTask input) {
        ReferenceMetadataValidator.validate(input);
        ReferenceTask task = versioned(input);
        try {
            mapper.insert(encode(task));
        } catch (DuplicateKeyException ex) {
            throw new ReferenceException("INVALID_ARGUMENT", 409, "任务编码已存在", false);
        }
        return task;
    }

    public ReferenceTask save(String taskCode, ReferenceTask input) {
        ReferenceMetadataValidator.validate(input);
        if (!taskCode.equals(input.taskCode())) {
            throw new ReferenceException("INVALID_ARGUMENT", 400, "任务编码不能修改", false);
        }
        get(taskCode);
        String expectedVersion = input.metadata().path("metadataVersion").asText();
        ReferenceTask task = versioned(input);
        if (mapper.update(encode(task), expectedVersion) != 1) {
            throw new ReferenceException("METADATA_VERSION_MISMATCH", 409, "配置已更新，请刷新后再保存", false);
        }
        return task;
    }

    private ReferenceTask versioned(ReferenceTask input) {
        ObjectNode metadata = input.metadata().deepCopy();
        metadata.put("metadataVersion", UUID.randomUUID().toString());
        metadata.put("taskCode", input.taskCode());
        metadata.put("taskName", input.taskName());
        return new ReferenceTask(input.taskCode(), input.taskName(), input.enabled(), input.datasourceKey(), input.sqlResource(), metadata);
    }

    private ReferenceTaskRow encode(ReferenceTask task) {
        return new ReferenceTaskRow(task.taskCode(), task.taskName(), task.enabled(), task.datasourceKey(), task.sqlResource(),
            task.metadata().toString(), task.metadata().path("metadataVersion").asText());
    }

    private ReferenceTask decode(ReferenceTaskRow row) {
        try {
            ObjectNode metadata = (ObjectNode) json.readTree(row.metadataJson());
            if (!row.metadataVersion().equals(metadata.path("metadataVersion").asText())) {
                throw new IllegalStateException("Persisted metadata version mismatch");
            }
            return new ReferenceTask(row.taskCode(), row.taskName(), row.enabled(), row.datasourceKey(), row.sqlResource(), metadata);
        } catch (Exception ex) {
            throw new ReferenceException("SERVICE_UNAVAILABLE", 503, "参照配置无法读取，请联系管理员", false);
        }
    }
}
