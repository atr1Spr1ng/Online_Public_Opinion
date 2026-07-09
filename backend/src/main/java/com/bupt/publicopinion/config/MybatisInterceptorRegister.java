package com.bupt.publicopinion.config;

import jakarta.annotation.PostConstruct;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class MybatisInterceptorRegister {

    private final SqlSessionFactory sqlSessionFactory;
    private final EsSyncInterceptor esSyncInterceptor;

    public MybatisInterceptorRegister(@Lazy SqlSessionFactory sqlSessionFactory, EsSyncInterceptor esSyncInterceptor) {
        this.sqlSessionFactory = sqlSessionFactory;
        this.esSyncInterceptor = esSyncInterceptor;
    }

    @PostConstruct
    public void register() {
        sqlSessionFactory.getConfiguration().addInterceptor(esSyncInterceptor);
    }
}
