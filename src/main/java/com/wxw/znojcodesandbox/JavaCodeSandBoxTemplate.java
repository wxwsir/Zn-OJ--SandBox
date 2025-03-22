package com.wxw.znojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.wxw.znojcodesandbox.model.CodeSandBoxRequest;
import com.wxw.znojcodesandbox.model.CodeSandBoxResponse;
import com.wxw.znojcodesandbox.model.ExecuteMessage;
import com.wxw.znojcodesandbox.model.JudgeInfo;
import com.wxw.znojcodesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author by xxz
 * @Description  代码沙箱模板类
 * @date 2024/9/22
 * @throws
 */
@Slf4j
public abstract class JavaCodeSandBoxTemplate implements CodeSandBox{
    /**
     * 全局代码目录名称
     */
    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
    /**
     * 用户代码类名
     */
    private static final String USER_CODE_CLASS_NAME = "Main.java";
    /**
     * 安全管理器路径
     */
    private static final String SECURITY_MANAGER_PATH = "E:\\Znoj\\znoj-code-sandbox\\src\\main\\resources\\security";
    /**
     * 安全管理器类名
     */
    private static final String SECURITY_MANAGER_CLASS_NAME = "MySecurityManager";
    /**
     * 执行超时时间(非题目限制，防止无限期执行): 10s
     */
    private static final Long RUN_TIME_OUT = 10000L;
    /**
     * 编译超时时间(非题目限制，防止无限期执行): 10s
     */
    private static final Long COMPILE_TIME_OUT = 10000L;
    /**
     * 编译内存限制(非题目限制): 64MB
     */
    private static final Long COMPILE_MEMORY_OUT = 64 * 1024L * 1024L;
    /**
     * 代码文件唯一标识
     */
    private static String uuid;
    /**
     * 特殊判题代码文件名
     */
    private static String specialCodeName = "FloatAnswerChecker.java";

    private static String specialCodePath = "FloatCode";
    /**
     * 代码沙箱执行流程
     * @param codeSandBoxRequest
     * @return
     */
    @Override
    public CodeSandBoxResponse run(CodeSandBoxRequest codeSandBoxRequest) {
        // 1. 获取代码沙箱请求信息
        String code = codeSandBoxRequest.getCode();
        List<String> inputList = codeSandBoxRequest.getInputList();
        Integer isSpecial = codeSandBoxRequest.getIs_special();

        // 2. 将代码写入到文件中
        File userCodeFile = saveCodeToFile(code);
        CodeSandBoxResponse codeSandBoxResponse;
        // 3.编译代码,若编译错误直接返回错误信息,不用再执行代码
        ExecuteMessage compileFileExecuteMessage = compileCode(userCodeFile, uuid);
        if (compileFileExecuteMessage.getErrorMessage() != null){
            codeSandBoxResponse = new CodeSandBoxResponse();
            codeSandBoxResponse.setCompileMessage("compileError: " + compileFileExecuteMessage.getErrorMessage());
            System.out.println("编译结果：" + compileFileExecuteMessage.getErrorMessage());
            return codeSandBoxResponse;
        }
        // 编译超时
        if (compileFileExecuteMessage.getTime() > COMPILE_TIME_OUT){
            codeSandBoxResponse = new CodeSandBoxResponse();
            codeSandBoxResponse.setCompileMessage("编译超时");
            return codeSandBoxResponse;
        }
        if (compileFileExecuteMessage.getMemory() > COMPILE_MEMORY_OUT){
            codeSandBoxResponse = new CodeSandBoxResponse();
            codeSandBoxResponse.setCompileMessage("编译内存超限");
            return codeSandBoxResponse;
        }
        // 4. 执行代码
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);
        // 5.整理返回结果
        codeSandBoxResponse = getResponse(executeMessageList);
        // 获取沙箱输出
        List<String> outputList = codeSandBoxResponse.getOutputList();
        // 特殊判题
        if(isSpecial == 1){
            JavaDockerSpecialCodeSandBox javaDockerSpecialCodeSandBox = new JavaDockerSpecialCodeSandBox();
            // 编译特殊判题代码
            javaDockerSpecialCodeSandBox.compileSpecialCode(specialCodeName,specialCodePath);
            // 编译错误直接返回错误信息
            if (compileFileExecuteMessage.getErrorMessage() != null){
                codeSandBoxResponse = new CodeSandBoxResponse();
                codeSandBoxResponse.setCompileMessage("compileError: " + compileFileExecuteMessage.getErrorMessage());
                codeSandBoxResponse.setSpecialJudgeMessage("SPJ_CE");
                System.out.println("编译结果：" + compileFileExecuteMessage.getErrorMessage());
                return codeSandBoxResponse;
            }
            // 执行特殊判题代码
            List<ExecuteMessage> executeSpecialMessageList = javaDockerSpecialCodeSandBox.runSpecialFile(inputList, outputList);
            codeSandBoxResponse = getSpecialResponse(executeSpecialMessageList);
        }

        // 6.文件清理
        boolean delFile = delFile(userCodeFile);
        if (!delFile) {
            log.error("deleteFile error, userCodeFilePath = {}", userCodeFile.getAbsolutePath());
        }

        return codeSandBoxResponse;
    }

    /**
     * 获取特殊判题结果
     * @param executeSpecialMessageList
     * @return
     */
    private CodeSandBoxResponse getResponse(List<ExecuteMessage> executeSpecialMessageList) {
        // 5.整理返回结果
        CodeSandBoxResponse codeSandBoxResponse = new CodeSandBoxResponse();
        List<String> outputList = new ArrayList<>();
        long max_time = 0L;
        long memory = 0L;
        for (ExecuteMessage executeMessage : executeSpecialMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            // 运行时有错误就退出
            if (StrUtil.isNotBlank(errorMessage)) {
                codeSandBoxResponse.setRuntimeMessage(errorMessage);
                break;
            }
            outputList.add(executeMessage.getMessage());
            long time = executeMessage.getTime();
            // 取单个用例最大执行时间
            max_time = Math.max(max_time, time);
            // 取单个用例最大内存
            memory = Math.max(memory, executeMessage.getMemory());
        }
        codeSandBoxResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(max_time);
        judgeInfo.setMemory(memory);
        codeSandBoxResponse.setJudgeInfo(judgeInfo);
        System.out.println("返回结果: " + codeSandBoxResponse);
        return codeSandBoxResponse;
    }

    /**
     * 1. 代码写入至文件
     * @param code
     * @return
     */
    public File saveCodeToFile(String code){
        // 2. 将代码写入到文件中
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        UUID uuidPath = UUID.randomUUID();
        uuid = uuidPath.toString();
        String userCodeParentPath = globalCodePathName + File.separator + uuidPath;
        String userCodePath = userCodeParentPath + File.separator + USER_CODE_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2. 编译代码
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileCode(File userCodeFile, String uuid){
        // 3.编译代码
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process process = Runtime.getRuntime().exec(compileCmd);
            // 获取编译进程的信息
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "编译");
            return executeMessage;
        } catch (IOException e) {
           throw new RuntimeException(e);
        }
    }


    /**
     * 3. 执行代码
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList){
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 4.执行代码(多个输入用例多次执行)
        List<ExecuteMessage> executeMessageList = new ArrayList<>(); // 存储每次执行的信息
        for (String input : inputList) {
            // 执行命令,限制Java堆内存大小为256MB,设置编码为UTF-8,指定Java安全管理器
//            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s Main %s", userCodeParentPath, SECURITY_MANAGER_PATH, SECURITY_MANAGER_CLASS_NAME, input);
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, input);
            try {
                Process process = Runtime.getRuntime().exec(runCmd);
                // 超时处理
                new Thread(()->{
                    try {
                        Thread.sleep(RUN_TIME_OUT);
                        System.out.println("超时,强制结束");
                        process.destroy();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
                // 获取执行进程的信息
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(process, "执行");
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("执行代码错误", e);
            }
        }
        return executeMessageList;
    }

    /**
     * 4. 整理特殊代码执行的返回结果
     * @param executeMessageList
     * @return
     */
    public CodeSandBoxResponse getSpecialResponse(List<ExecuteMessage> executeMessageList){
        // 5.整理返回结果
        CodeSandBoxResponse codeSandBoxResponse = new CodeSandBoxResponse();
        List<String> outputList = new ArrayList<>();
        long max_time = 0L;
        long memory = 0L;
        String specialMessage = "SPJ_AC";
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            // 运行时有错误就退出
            if (StrUtil.isNotBlank(errorMessage)) {
                codeSandBoxResponse.setRuntimeMessage(errorMessage);
                codeSandBoxResponse.setSpecialJudgeMessage("SPJ_RE");
                break;
            }
            String message = executeMessage.getMessage();
            if (message.startsWith("WA")){
                specialMessage = "SPJ_WA";
                break;
            }
            outputList.add(executeMessage.getMessage());
            long time = executeMessage.getTime();
            // 取单个用例最大执行时间
            max_time = Math.max(max_time, time);
            // 取单个用例最大内存
            memory = Math.max(memory, executeMessage.getMemory());
        }
        codeSandBoxResponse.setSpecialJudgeMessage(specialMessage);
        codeSandBoxResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(max_time);
        judgeInfo.setMemory(memory);
        codeSandBoxResponse.setJudgeInfo(judgeInfo);
        System.out.println("返回结果: " + codeSandBoxResponse);
        return codeSandBoxResponse;
    }

    /**
     * 5. 删除临时文件
     * @param userCodeFile
     * @return
     */
    public boolean delFile(File userCodeFile){
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

}
