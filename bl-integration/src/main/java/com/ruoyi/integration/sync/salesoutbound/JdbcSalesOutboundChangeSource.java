package com.ruoyi.integration.sync.salesoutbound;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import com.ruoyi.integration.sync.schedule.SyncCandidate;
import com.ruoyi.integration.task.TaskAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "integration.datasource.u8", name = "enabled", havingValue = "true")
public class JdbcSalesOutboundChangeSource implements SalesOutboundChangeSource
{
    private static final String CREATE_SQL = """
            SELECT ccode AS document_no, dnmaketime AS changed_at, NULL AS version_token
            FROM rdrecord32
            WHERE dnmaketime > :fromTime AND dnmaketime <= :toTime
              AND dVeriDate IS NULL AND ddate >= :minimumDate
            ORDER BY dnmaketime, ccode
            """;
    private static final String UPDATE_SQL = """
            SELECT ccode AS document_no, dnmodifytime AS changed_at, ufts AS version_token
            FROM rdrecord32
            WHERE dnmodifytime > :fromTime AND dnmodifytime <= :toTime
              AND dVeriDate IS NULL AND ddate >= :minimumDate
            ORDER BY dnmodifytime, ccode
            """;
    private static final String DELETE_SQL = """
            SELECT djbh AS document_no, sj AS changed_at, NULL AS version_token
            FROM rdrecord32_delete_log
            WHERE sj > :fromTime AND sj <= :toTime
            ORDER BY sj, djbh
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final LocalDate minimumDate;

    @Autowired
    public JdbcSalesOutboundChangeSource(@Qualifier("u8JdbcTemplate") NamedParameterJdbcTemplate jdbc,
            @Value("${integration.sync.sales-outbound.minimum-document-date:2023-09-21}") String minimumDate)
    {
        this(jdbc, LocalDate.parse(minimumDate));
    }

    public JdbcSalesOutboundChangeSource(NamedParameterJdbcTemplate jdbc, LocalDate minimumDate)
    {
        this.jdbc = jdbc;
        this.minimumDate = minimumDate;
    }

    @Override
    public LocalDateTime captureUpperBound()
    {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP AS db_time", Map.of(),
                (rs, rowNum) -> rs.getTimestamp("db_time").toLocalDateTime());
    }

    @Override
    public List<SyncCandidate> findCandidates(LocalDateTime fromExclusive, LocalDateTime toInclusive)
    {
        Map<String, Object> params = Map.of(
                "fromTime", Timestamp.valueOf(fromExclusive),
                "toTime", Timestamp.valueOf(toInclusive),
                "minimumDate", java.sql.Date.valueOf(minimumDate));
        List<SyncCandidate> result = new ArrayList<>();
        result.addAll(jdbc.query(CREATE_SQL, params,
                (rs, rowNum) -> candidate(rs, TaskAction.CREATE)));
        result.addAll(jdbc.query(UPDATE_SQL, params,
                (rs, rowNum) -> candidate(rs, TaskAction.CANCEL_RECREATE)));
        result.addAll(jdbc.query(DELETE_SQL, params,
                (rs, rowNum) -> candidate(rs, TaskAction.DELETE)));
        return result;
    }

    private SyncCandidate candidate(ResultSet rs, TaskAction action) throws SQLException
    {
        byte[] rowVersion = rs.getBytes("version_token");
        if (action == TaskAction.CANCEL_RECREATE && (rowVersion == null || rowVersion.length == 0))
        {
            throw new SQLException("U8销售出库单缺少ufts行版本");
        }
        return new SyncCandidate(rs.getString("document_no"), action,
                rs.getTimestamp("changed_at").toLocalDateTime(),
                rowVersion == null ? null : HexFormat.of().formatHex(rowVersion));
    }
}
