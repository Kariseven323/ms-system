package com.imooc.diners.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.io.Serializable;

@Getter
@Setter
public class OAuthDinerInfo implements Serializable {
    private String nickname;
    private String avatarUrl;
    private String accessToken;
    private String expireIn;
    private List<String> scopes;
    private String refreshToken;
}
