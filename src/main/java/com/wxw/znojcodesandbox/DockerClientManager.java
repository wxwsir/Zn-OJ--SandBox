package com.wxw.znojcodesandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;

/**
 * @description:
 * @author: wxw
 * @date: 2025/3/21
 */
public class DockerClientManager {
    private static volatile DockerClient dockerClient = null;

    private DockerClientManager() {

    }

    public static DockerClient getDockerClient() {
        if (dockerClient == null) {
            synchronized (DockerClient.class){
                if (dockerClient == null) {
                    dockerClient = DockerClientBuilder.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                            .withDockerHost("tcp://192.168.100.1:2375")
                            .build()).build();
                }
            }
        }
        return dockerClient;
    }
}
