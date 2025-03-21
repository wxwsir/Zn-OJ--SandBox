package com.wxw.znojcodesandbox;


import cn.hutool.core.io.resource.ResourceUtil;
import com.wxw.znojcodesandbox.model.CodeSandBoxRequest;
import com.wxw.znojcodesandbox.model.CodeSandBoxResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author by xxz
 * @Description  Java原生代码沙箱
 * @date 2024/9/18
 * @throws
 */
@Component
public class JavaNativeCodeSandBox extends JavaCodeSandBoxTemplate {

    @Override
    public CodeSandBoxResponse run(CodeSandBoxRequest codeSandBoxRequest) {
        // 调用父类的方法执行代码
        return super.run(codeSandBoxRequest);
    }
}
