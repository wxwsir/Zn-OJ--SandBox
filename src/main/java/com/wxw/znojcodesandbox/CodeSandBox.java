package com.wxw.znojcodesandbox;


import com.wxw.znojcodesandbox.model.CodeSandBoxRequest;
import com.wxw.znojcodesandbox.model.CodeSandBoxResponse;

/**
 * @author by xxz
 * @Description  代码沙箱接口
 * @date 2024/9/18
 * @throws
 */

public interface CodeSandBox {


    CodeSandBoxResponse run(CodeSandBoxRequest request);

}
