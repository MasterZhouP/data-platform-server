package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.ruoyi.integration.task.TaskAction;

class JdbcSalesOutboundChangeSourceTest
{
    @Test
    void readsCreateUpdateAndDeleteEventsInsideTheCapturedWindow()
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:changes;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", "");
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(dataSource);
        jdbc.getJdbcTemplate().execute("CREATE TABLE rdrecord32 (ccode VARCHAR(100), ddate DATE, dVeriDate TIMESTAMP, dnmaketime TIMESTAMP, dnmodifytime TIMESTAMP, ufts VARBINARY(8))");
        jdbc.getJdbcTemplate().execute("CREATE TABLE rdrecord32_delete_log (djbh VARCHAR(100), sj TIMESTAMP)");
        jdbc.update("INSERT INTO rdrecord32 VALUES ('NEW-1','2026-09-01',NULL,'2026-09-11 10:00:00',NULL,X'0000000000000001')", Map.of());
        jdbc.update("INSERT INTO rdrecord32 VALUES ('UPD-1','2026-09-01',NULL,'2026-08-01 10:00:00','2026-09-11 11:00:00',X'0000000000000002')", Map.of());
        jdbc.update("INSERT INTO rdrecord32_delete_log VALUES ('DEL-1','2026-09-11 12:00:00')", Map.of());
        JdbcSalesOutboundChangeSource source = new JdbcSalesOutboundChangeSource(jdbc, LocalDate.of(2023, 9, 21));

        var changes = source.findCandidates(LocalDateTime.of(2026, 9, 10, 0, 0),
                LocalDateTime.of(2026, 9, 12, 0, 0));

        assertEquals(3, changes.size());
        assertEquals(TaskAction.CREATE, changes.get(0).action());
        assertEquals(TaskAction.CANCEL_RECREATE, changes.get(1).action());
        assertEquals(TaskAction.DELETE, changes.get(2).action());
        assertEquals(null, changes.get(0).versionToken());
        assertEquals("0000000000000002", changes.get(1).versionToken());
    }
}
