package com.ruoyi.integration.taskdefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TaskDefinitionResolverTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void resolvesEnabledTaskWithItsActivePublishedRevision() throws Exception
    {
        InMemoryRepository repository = new InMemoryRepository();
        repository.add("OA_EXPENSE_VOUCHER", true, TaskType.OA_TO_U8, 18L, "sha256-a");

        PublishedTaskRevision resolved = new TaskDefinitionResolver(repository)
                .resolvePublished("OA_EXPENSE_VOUCHER");

        assertEquals("OA_EXPENSE_VOUCHER", resolved.taskCode());
        assertEquals(18L, resolved.revisionId());
        assertEquals(TaskType.OA_TO_U8, resolved.taskType());
        assertEquals("sha256-a", resolved.checksum());
    }

    @Test
    void refusesDraftOnlyDisabledAndCaseMismatchedTaskCodes() throws Exception
    {
        InMemoryRepository repository = new InMemoryRepository();
        repository.add("OA_DRAFT", true, TaskType.OA_TO_U8, null, null);
        repository.add("OA_DISABLED", false, TaskType.OA_TO_U8, 19L, "sha256-b");
        repository.add("OA_CASE", true, TaskType.OA_TO_U8, 20L, "sha256-c");
        TaskDefinitionResolver resolver = new TaskDefinitionResolver(repository);

        assertThrows(TaskDefinitionNotFoundException.class, () -> resolver.resolvePublished("OA_DRAFT"));
        assertThrows(TaskDisabledException.class, () -> resolver.resolvePublished("OA_DISABLED"));
        assertThrows(TaskDefinitionNotFoundException.class, () -> resolver.resolvePublished("oa_case"));
    }

    @Test
    void resolvesThePinnedRevisionAfterTheTaskIsDisabledOrTheRevisionIsArchived() throws Exception
    {
        InMemoryRepository repository = new InMemoryRepository();
        repository.add("OA_EXPENSE_VOUCHER", true, TaskType.OA_TO_U8, 18L, "sha256-a");
        repository.disable("OA_EXPENSE_VOUCHER");
        repository.archiveRevision(18L);

        PublishedTaskRevision resolved = new TaskDefinitionResolver(repository)
                .resolvePinned("OA_EXPENSE_VOUCHER", 18L, "sha256-a");

        assertEquals(18L, resolved.revisionId());
        assertEquals(TaskType.OA_TO_U8, resolved.taskType());
    }

    @Test
    void identifiesCatalogTasksEvenWhenTheyDoNotYetHaveAPublishedRevision() throws Exception
    {
        InMemoryRepository repository = new InMemoryRepository();
        repository.add("OA_DRAFT", true, TaskType.OA_TO_U8, null, null);
        TaskDefinitionResolver resolver = new TaskDefinitionResolver(repository);

        assertTrue(resolver.isCatalogTask("OA_DRAFT"));
        assertFalse(resolver.isCatalogTask("U8_TO_OA_LEGACY"));
    }

    private final class InMemoryRepository implements TaskDefinitionRepository
    {
        private final Map<String, IntegrationTaskDefinition> tasks = new LinkedHashMap<>();
        private final Map<Long, TaskRevision> revisions = new LinkedHashMap<>();

        void add(String taskCode, boolean enabled, TaskType type, Long activeRevisionId, String checksum) throws Exception
        {
            tasks.put(taskCode, new IntegrationTaskDefinition(taskCode, taskCode, type, enabled,
                    activeRevisionId, null, 1L));
            if (activeRevisionId != null)
            {
                revisions.put(activeRevisionId, new TaskRevision(activeRevisionId, taskCode, 1,
                        RevisionStatus.PUBLISHED, json.readTree("{\"operationCode\":\"VOUCHER_ADD\"}"), checksum,
                        Map.of("u8Connection", "1")));
            }
        }

        void disable(String taskCode)
        {
            IntegrationTaskDefinition task = tasks.get(taskCode);
            tasks.put(taskCode, new IntegrationTaskDefinition(task.taskCode(), task.taskName(), task.taskType(), false,
                    task.activeRevisionId(), task.draftRevisionId(), task.configVersion()));
        }

        void archiveRevision(Long revisionId)
        {
            TaskRevision revision = revisions.get(revisionId);
            revisions.put(revisionId, new TaskRevision(revision.revisionId(), revision.taskCode(), revision.revisionNo(),
                    RevisionStatus.ARCHIVED, revision.config(), revision.checksum(), revision.dependencyRevisions()));
        }

        @Override
        public IntegrationTaskDefinition findTask(String taskCode)
        {
            return tasks.get(taskCode);
        }

        @Override
        public TaskRevision findRevision(Long revisionId)
        {
            return revisions.get(revisionId);
        }
    }
}
