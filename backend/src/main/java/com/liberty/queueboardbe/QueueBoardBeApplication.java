package com.liberty.queueboardbe;

import com.liberty.queueboardbe.config.QueueBoardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(QueueBoardProperties.class)
public class QueueBoardBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueBoardBeApplication.class, args);
    }
}
