package com.bupt.publicopinion.common.aop;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.exception.AccessDeniedException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AdminAspect {

    @Before("@annotation(com.bupt.publicopinion.common.annotation.RequireAdmin)")
    public void checkAdmin() {
        UserContext.UserContextInfo ctx = UserContext.get();
        if (ctx == null || !"ADMIN".equals(ctx.role())) {
            throw new AccessDeniedException("无权访问");
        }
    }
}
