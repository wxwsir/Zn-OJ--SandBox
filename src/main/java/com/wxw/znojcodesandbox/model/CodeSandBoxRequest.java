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
public class CodeSandBoxRequest {

    private String code;

    private String language;

    private List<String> inputList;
}
