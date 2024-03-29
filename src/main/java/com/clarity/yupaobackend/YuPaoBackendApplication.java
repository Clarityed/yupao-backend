package com.clarity.yupaobackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.clarity.yupaobackend.mapper")
@EnableScheduling
public class YuPaoBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuPaoBackendApplication.class, args);
    }

}
