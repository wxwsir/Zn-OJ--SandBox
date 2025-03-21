package com.wxw.znojcodesandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;

import java.util.Arrays;

/**
 * @description: Docker容器管理器
 * @author: wxw
 * @date: 2025/3/21
 */
public class DockerContainerManager {
    /**
     * Docker客户端
     */
    private DockerClient dockerClient;
    /**
     * 容器ID
     */
    private String containerId;


    public DockerContainerManager() {
        this.dockerClient = DockerClientBuilder.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://192.168.100.1:2375")
                .build()).build();
    }

    /**
     * 创建容器
     * @return 容器ID
     */
    public String createContainer(String uuid) {
        // 创建容器逻辑，设置容器ID
        // 这里需要根据实际情况来设置容器配置，例如挂载卷、网络等
        String image = "openjdk:8-alpine";
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
        System.out.println("镜像下载完成");
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(100 * 1000 * 1000L);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);
        // 设置容器的安全选项
        String profileConfig = ResourceUtil.readUtf8Str("profile.json");
        hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profileConfig));
        // 挂载用户代码文件到容器内(/data目录)
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

        return containerId;
    }

    /**
     * 删除容器
     */
    public void removeContainer() {
        if (containerId != null) {
            dockerClient.stopContainerCmd(containerId).exec();
            dockerClient.removeContainerCmd(containerId).exec();
            System.out.println("容器删除成功");
        } else{
            throw new RuntimeException("容器ID为空，无法删除容器");
        }
    }
}
