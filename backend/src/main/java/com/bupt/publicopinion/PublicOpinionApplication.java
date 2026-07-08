package com.bupt.publicopinion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.bupt.publicopinion.collection.mapper", "com.bupt.publicopinion.system.mapper", "com.bupt.publicopinion.content.mapper", "com.bupt.publicopinion.analysis.mapper", "com.bupt.publicopinion.event.mapper", "com.bupt.publicopinion.report.mapper", "com.bupt.publicopinion.fake.mapper", "com.bupt.publicopinion.propagation.mapper"})
public class PublicOpinionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PublicOpinionApplication.class, args);
    }
}
