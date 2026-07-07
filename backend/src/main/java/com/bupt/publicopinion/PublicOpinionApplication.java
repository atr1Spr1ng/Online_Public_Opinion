package com.bupt.publicopinion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.bupt.publicopinion.collection.mapper", "com.bupt.publicopinion.system.mapper", "com.bupt.publicopinion.content.mapper"})
public class PublicOpinionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublicOpinionApplication.class, args);
    }
}
