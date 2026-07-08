package com.bupt.publicopinion.system.vo;

import com.bupt.publicopinion.system.entity.UserDomain;

public record DomainVO(Long id, String domainName) {
    public static DomainVO from(UserDomain ud) {
        return new DomainVO(ud.getId(), ud.getDomainName());
    }
}
