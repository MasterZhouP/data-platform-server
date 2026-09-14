SELECT *
FROM (
    SELECT
        ch.iinvweight jz,
        ch.cComUnitCode zdwbm,
        ch.cSTComUnitCode fdwbm,
        ch.cInvCode,
        cInvName,
        cInvStd,
        zdw.cComUnitName zdw,
        fdw.cComUnitName fdw,
        CAST (
            (
                CASE
                    WHEN igrouptype = 2 AND ISNULL(xcl.iNUM, 0) <> 0
                    THEN ABS(ISNULL(xcl.iQuantity, 0)) / ABS(ISNULL(xcl.iNUM, 1))
                    ELSE ISNULL(ComputationUnit2.iChangRate, 0)
                END
            ) AS DECIMAL(20, 6)
        ) hsl,
        cInvDefine2 zldj,
        ch.iMassDate,
        chdl.cInvCName chdl,
        ch.cAddress cd,
        cInvDefine4 mrscbm
    FROM Inventory ch
    LEFT JOIN ComputationUnit zdw ON ch.cComUnitCode = zdw.cComunitCode
    LEFT JOIN ComputationUnit fdw ON ch.cSTComUnitCode = fdw.cComunitCode
    LEFT JOIN ComputationUnit ComputationUnit2 ON ch.cSAComUnitCode = ComputationUnit2.cComunitCode
    LEFT JOIN InventoryClass chdl ON chdl.cInvCCode = LEFT(ch.cInvCCode, 2)
    LEFT JOIN CurrentStock xcl ON ch.cinvcode = xcl.cinvcode
    GROUP BY
        ch.iinvweight,
        ch.cComUnitCode,
        ch.cSTComUnitCode,
        ch.cInvCode,
        cInvName,
        cInvStd,
        zdw.cComUnitName,
        fdw.cComUnitName,
        cInvDefine4,
        CAST (
            (
                CASE
                    WHEN igrouptype = 2 AND ISNULL(xcl.iNUM, 0) <> 0
                    THEN ABS(ISNULL(xcl.iQuantity, 0)) / ABS(ISNULL(xcl.iNUM, 1))
                    ELSE ISNULL(ComputationUnit2.iChangRate, 0)
                END
            ) AS DECIMAL(20, 6)
        ),
        cInvDefine2,
        ch.iMassDate,
        chdl.cInvCName,
        ch.cAddress,
        dEDate
    HAVING dEDate IS NULL
) a
