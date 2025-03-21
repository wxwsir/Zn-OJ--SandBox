package com.wxw.znojcodesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author by xxz
 * @Description
 * @date 2024/9/18
 * @throws
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CodeSandBoxResponse {
    /**
     * 输出列表
     */
    private List<String> outputList;
    /**
     * 编译响应信息
     */
    private String compileMessage;
    /**
     * 运行时错误信息
     */
    private String runtimeMessage;
    /**
     * 判题信息
     */
    private JudgeInfo judgeInfo;
    /**
     * 状态码(SPJ判题专用)
     */
    private String status;
}
