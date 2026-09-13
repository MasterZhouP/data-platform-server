package com.ruoyi.integration.sync.salesoutbound;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundDocument;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundHeader;
import com.ruoyi.integration.sync.salesoutbound.model.SalesOutboundLine;

class SalesOutboundValidatorTest
{
    private final SalesOutboundValidator validator = new SalesOutboundValidator();

    @Test
    void acceptsUnverifiedDocumentWithConsistentDetails()
    {
        assertDoesNotThrow(() -> validator.validate(document(false, "10001", List.of(line("10001")))));
    }

    @Test
    void rejectsAuditedEmptyOrInconsistentDocumentsBeforeCallingOa()
    {
        assertThrows(SourceValidationException.class,
                () -> validator.validate(document(true, "10001", List.of(line("10001")))));
        assertThrows(SourceValidationException.class,
                () -> validator.validate(document(false, "10001", List.of())));
        assertThrows(SourceValidationException.class,
                () -> validator.validate(document(false, "10001", List.of(line("other")))));
        assertThrows(SourceValidationException.class,
                () -> validator.validate(document(false, null, List.of(line("10001")))));
        SalesOutboundDocument missingManager = new SalesOutboundDocument(
                new SalesOutboundHeader("CK-001", "张三", LocalDate.now(), "WH-01", "成品仓",
                        "C-01", "客户甲", "D-01", "销售部", null, "10001", "SO-9", null, false),
                List.of(line("10001")));
        assertThrows(SourceValidationException.class, () -> validator.validate(missingManager));
    }

    private SalesOutboundDocument document(boolean verified, String id, List<SalesOutboundLine> lines)
    {
        return new SalesOutboundDocument(new SalesOutboundHeader("CK-001", "张三", LocalDate.now(), "WH-01",
                "成品仓", "C-01", "客户甲", "D-01", "销售部", null, id, "SO-9",
                "900000000000001", verified), lines);
    }

    private SalesOutboundLine line(String parentId)
    {
        return new SalesOutboundLine("INV-1", "商品A", "S", BigDecimal.ONE, "20001", parentId,
                "箱", "件", BigDecimal.ONE, null, null, null);
    }
}
