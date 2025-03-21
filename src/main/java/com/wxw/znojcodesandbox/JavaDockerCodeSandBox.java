package com.wxw.znojcodesandbox;

import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.wxw.znojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author by xxz
 * @Description  Docker代码沙箱-普通判题
 * @date 2024/9/22
 * @throws
 */
@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {
    /**
     * Docker容器管理器
     */
    private static final DockerContainerManager containerManager = new DockerContainerManager();
    /**
     * 容器ID
     */
    private static String containerId;

    @Override
    public ExecuteMessage compileCode(File userCodeFile, String uuid) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        System.out.println("111" + containerManager);
        // 启动容器
        containerId = containerManager.createContainer(uuid);
        DockerClient dockerClient = DockerClientManager.getDockerClient();

        // 执行编译命令并获取结果(docker命令)
        StopWatch stopWatch = new StopWatch();
        String compileFileName = "/app/" + userCodeFile.getName();
        String[] cmdArray = ArrayUtil.append(new String[]{"javac", "-encoding", "utf-8", compileFileName});
        ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                .withCmd(cmdArray)
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .exec();
        System.out.println("创建编译命令：" + execCreateCmdResponse);

        ExecuteMessage executeMessage = new ExecuteMessage();
        final String[] message = {null};
        final String[] errorMessage = {null};
        long time = 0L;
        // 判断是否超时
        final boolean[] timeout = {true};
        // 添加编译完成标志
        final boolean[] compileFinished = {false};

        String execId = execCreateCmdResponse.getId();
        ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
            @Override
            public void onComplete() {
                // 如果执行完成，则表示没超时
                timeout[0] = false;
                super.onComplete();
            }

            @Override
            public void onNext(Frame frame) {
                StreamType streamType = frame.getStreamType();
                if (StreamType.STDERR.equals(streamType)) {
                    errorMessage[0] = new String(frame.getPayload());
                    System.out.println("Compile Error: " + errorMessage[0]);
                    // 编译出错时也标记完成
                    compileFinished[0] = true;
                } else {
                    message[0] = new String(frame.getPayload());
                    System.out.println("Compile Message: " + message[0]);
                }
                super.onNext(frame);
            }

            @Override
            public void onError(Throwable throwable) {
                // 发生错误时标记完成
                compileFinished[0] = true;
                super.onError(throwable);
            }

        };

        final long[] memory = {0L};

        // 获取占用的内存
        StatsCmd statsCmd = dockerClient.statsCmd(containerId);
        ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
            @Override
            public void onNext(Statistics statistics) {
                // 检查编译是否已完成
                if (compileFinished[0]) {
                    try {
                        // 编译完成后关闭统计
                        statsCmd.close();
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                    return;
                }
                System.out.println("编译内存占用: " + statistics.getMemoryStats().getUsage()/1024 + " KB");
                memory[0] = statistics.getMemoryStats().getUsage()/1024;
            }

            @Override
            public void close() throws IOException {
                statsCmd.close();
            }

            @Override
            public void onStart(Closeable closeable) {}

            @Override
            public void onError(Throwable throwable) {
                statsCmd.close();
            }

            @Override
            public void onComplete() {
                statsCmd.close();
            }
        });
        //这里获取状态是异步的，必须在执行前去统计状态
        statsCmd.exec(statisticsResultCallback);

        try {
            stopWatch.start();
            // 执行命令
            dockerClient.execStartCmd(execId)
                    .exec(execStartResultCallback)
                    // 控制程序是否超时  TIME_OUT, TimeUnit.MICROSECONDS
                    .awaitCompletion();
            stopWatch.stop();
            time = stopWatch.getLastTaskTimeMillis();
            // 关闭统计命令
            statsCmd.close();
        } catch (InterruptedException e) {
            System.out.println("编译异常");
            throw new RuntimeException(e);
        }finally {
            // 确保在命令执行完毕后关闭统计回调
            statsCmd.close();
        }
        executeMessage.setMessage(message[0]);
        executeMessage.setErrorMessage(errorMessage[0]);
        executeMessage.setTime(time);
        executeMessage.setMemory(memory[0]);
        System.out.println("Compile Message: " + executeMessage.getMessage());
        System.out.println("Compile Error Message: " + executeMessage.getErrorMessage());
        System.out.println("Compile Time: " + executeMessage.getTime() + " ms");
        System.out.println("Compile Memory: " + executeMessage.getMemory() + " KB");
        if (compileFinished[0]){
            // 6. 停止并删除容器
            containerManager.removeContainer(containerId);
            System.out.println("编译失败,容器删除");
        }
        return executeMessage;
    }

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList) {

        // 获取Docker客户端
        DockerClient dockerClient = DockerClientManager.getDockerClient();
        // 5. 执行命令并获取结果(docker命令)
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
                StopWatch stopWatch = new StopWatch();
                String[] inputArgsArray = inputArgs.split(" ");
                String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(cmdArray)
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();
                System.out.println("创建执行命令：" + execCreateCmdResponse);

                ExecuteMessage executeMessage = new ExecuteMessage();
                final String[] message = {null};
                final String[] errorMessage = {null};
                long time = 0L;
                // 判断是否超时
                final boolean[] timeout = {true};
                String execId = execCreateCmdResponse.getId();
                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                    @Override
                    public void onComplete() {
                        // 如果执行完成，则表示没超时
                        timeout[0] = false;
                        super.onComplete();
                    }

                    @Override
                    public void onNext(Frame frame) {
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorMessage[0] = new String(frame.getPayload());
                            System.out.println("RunTime Error Message: " + errorMessage[0]);
                        } else {
                            message[0] = new String(frame.getPayload());
                            System.out.println("Run Message: " + message[0]);
                        }
                        super.onNext(frame);
                    }
                };

                final long[] memory = {0L};

                // 获取占用的内存
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                    @Override
                    public void onNext(Statistics statistics) {
                        System.out.println("执行内存占用: " + statistics.getMemoryStats().getUsage()/1024 + " KB");
                        memory[0] = statistics.getMemoryStats().getUsage()/1024;
                    }

                    @Override
                    public void close() throws IOException {}

                    @Override
                    public void onStart(Closeable closeable) {}

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onComplete() {}
                });
                //这里获取状态是异步的，必须在执行前去统计状态
                statsCmd.exec(statisticsResultCallback);

                try {
                    stopWatch.start();
                    // 执行命令
                    dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            // 控制程序是否超时  TIME_OUT, TimeUnit.MICROSECONDS
                            .awaitCompletion();
                    stopWatch.stop();
                    time = stopWatch.getLastTaskTimeMillis();
                    // 关闭统计命令
                    statsCmd.close();
                } catch (InterruptedException e) {
                    System.out.println("程序执行异常");
                    throw new RuntimeException(e);
                }
                executeMessage.setMessage(message[0]);
                executeMessage.setErrorMessage(errorMessage[0]);
                executeMessage.setTime(time);
                executeMessage.setMemory(memory[0]);
                executeMessageList.add(executeMessage);
        }
        // 6. 停止并删除容器
        containerManager.removeContainer(containerId);
        System.out.println("第一个用例运行结果: " + executeMessageList.get(0));
        return executeMessageList;
    }
}
