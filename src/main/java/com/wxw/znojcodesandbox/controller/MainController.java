package com.wxw.znojcodesandbox.controller;

import com.wxw.znojcodesandbox.JavaCodeSandBoxTemplate;
import com.wxw.znojcodesandbox.JavaDockerCodeSandBox;
import com.wxw.znojcodesandbox.JavaNativeCodeSandBox;
import com.wxw.znojcodesandbox.model.CodeSandBoxRequest;
import com.wxw.znojcodesandbox.model.CodeSandBoxResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author wxw
 * @date 2024/09/24
 * @description 代码沙箱接口
 */

@RestController("/")
public class MainController {

    /**
     * 鉴权请求头
     */
    private static final String AUTH_REQUEST_HEADER = "auth";
    /**
     * 鉴权密钥
     */
    private static final String AUTH_REQUEST_SECRET = "ce656850400574e9f9cffb285ee8abc0";

    @Resource
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    @Resource
    private JavaDockerCodeSandBox javaDockerCodeSandBox;

    @PostMapping("/executeCode")
    public CodeSandBoxResponse executeCode(@RequestBody CodeSandBoxRequest codeSandBoxRequest, HttpServletRequest httpServletRequest,
                                           HttpServletResponse httpServletResponse) {
        String header = httpServletRequest.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(header)) {
            httpServletResponse.setStatus(403);
            return null;
        }
        if(codeSandBoxRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaDockerCodeSandBox.run(codeSandBoxRequest);
    }
}
