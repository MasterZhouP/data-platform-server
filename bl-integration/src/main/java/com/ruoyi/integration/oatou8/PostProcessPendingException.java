package com.ruoyi.integration.oatou8;

/**
 * U8 已确认成功但必需结果尚不可得。
 * 此异常由运行器转换为 PARTIAL_SUCCESS，而非 FAILED，重试时只能从 lastCompletedStage 后继续查询。
 */
public class PostProcessPendingException extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final String lastCompletedStage;
    private final String resultOutputsJson;

    public PostProcessPendingException(String errorCode, String message, String lastCompletedStage, String resultOutputsJson)
    {
        super(message);
        this.errorCode = errorCode;
        this.lastCompletedStage = lastCompletedStage;
        this.resultOutputsJson = resultOutputsJson;
    }

    public String getErrorCode() { return errorCode; }
    public String getLastCompletedStage() { return lastCompletedStage; }
    public String getResultOutputsJson() { return resultOutputsJson; }
}
