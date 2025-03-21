package com.wxw.znojcodesandbox.model;

import lombok.Data;

/**
 * @author by xxz
 * @Description
 * @date 2024/9/19
 * @throws
 */
@Data
public class ExecuteMessage {
    /**
     * 错误码
     */
    private Integer exitValue;
    /**
     * 输出结果
     */
    private String message;
    /**
     * 错误信息
     */
    private String errorMessage;
    /**
     * 执行时间
     */
    private Long time;
    /**
     * 执行内存
     */
    private Long memory;
}
