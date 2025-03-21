package com.wxw.znojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.wxw.znojcodesandbox.model.CodeSandBoxRequest;
import com.wxw.znojcodesandbox.model.CodeSandBoxResponse;
import com.wxw.znojcodesandbox.model.ExecuteMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author by xxz
 * @Description  java-docker代码沙箱
 * @date 2024/9/22
 * @throws
 */
@Component
public class JavaDockerCodeSandBox extends JavaCodeSandBoxTemplate {
    /**
     * 超时时间
     */
    private static final Long TIME_OUT = 5000L;
    /**
     * 是否初次拉取镜像
     */
    private static final Boolean FIRST_INIT = true;
    /**
     * Docker容器管理器
     */
    private static DockerContainerManager containerManager;

    private JavaDockerCodeSandBox() {
        containerManager = new DockerContainerManager();
    }

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile, List<String> inputList, String uuid) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 4.创建容器，把文件复制到docker容器内
        // 4.1 获取默认的 Docker Client
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://192.168.100.1:2375")
                .build();// 指定Docker守护进程的地址
        DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
        // 4.2 拉取镜像image(使用轻量级的java环境镜像)
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");

        // 4.3 创建容器Container
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        // 设置容器的安全选项
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileConfig));
        // 挂载用户代码文件到容器内(/data目录)
        // userCodeParentPath.replace("\\","/").replace("E:", "/e")
        // "/home/wxwlinux/znoj-code-sandbox/tempCode/"+uuid
        hostConfig.setBinds(new Bind("/home/wxwlinux/znoj-code-sandbox/tempCode/"+uuid, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();

        // 4.4 启动容器
        dockerClient.startContainerCmd(containerId).exec();

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
                            System.out.println("输出错误结果：" + errorMessage[0]);
                        } else {
                            message[0] = new String(frame.getPayload());
                            System.out.println("输出结果：" + message[0]);
                        }
                        super.onNext(frame);
                    }
                };

                final long[] maxMemory = {0L};

                // 获取占用的内存
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {

                    @Override
                    public void onNext(Statistics statistics) {
                        System.out.println("内存占用: " + statistics.getMemoryStats().getUsage()/1024 + " KB");
                        maxMemory[0] = Math.max(statistics.getMemoryStats().getUsage()/1024, maxMemory[0]);
                    }

                    @Override
                    public void close() throws IOException {

                    }

                    @Override
                    public void onStart(Closeable closeable) {

                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
                //这里获取状态是异步的，必须在执行前去统计状态
                statsCmd.exec(statisticsResultCallback);
                // statsCmd.close();

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
                executeMessage.setMemory(maxMemory[0]);
                executeMessageList.add(executeMessage);
        }
        // 6. 停止并删除容器
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
        System.out.println("容器删除成功");
        System.out.println("第一个用例运行结果: " + executeMessageList.get(0));
        return executeMessageList;
    }

    @Override
    public ExecuteMessage compileCode(File userCodeFile) {
        // TODO
    }
}
