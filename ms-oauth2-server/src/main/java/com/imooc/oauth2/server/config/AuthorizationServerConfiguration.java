package com.imooc.oauth2.server.config;

import com.imooc.commons.model.domain.SignInIdentity;
import com.imooc.oauth2.server.service.UserService;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;

import javax.annotation.Resource;
import java.util.LinkedHashMap;

/**
 * 授权服务器配置类
 **/
@Configuration
@EnableAuthorizationServer
public class AuthorizationServerConfiguration extends AuthorizationServerConfigurerAdapter {

    // RedisTokenSore
    @Resource
    private RedisTokenStore redisTokenStore;

    // 认证管理对象
    @Resource
    private AuthenticationManager authenticationManager;

    // 密码编码器
    @Resource
    private PasswordEncoder passwordEncoder;

    // 客户端配置类
    @Resource
    private ClientOAuth2DataConfiguration clientOAuth2DataConfiguration;

    // 登录校验
    @Resource
    private UserService userService;

    /**
     * 客户端配置 - 授权模型
     */
    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        clients.inMemory()
                // 客户端标识 ID
                .withClient(clientOAuth2DataConfiguration.getClientId())
                // 客户端安全码
                .secret(passwordEncoder.encode(clientOAuth2DataConfiguration.getSecret()))
                // 授权类型
                .authorizedGrantTypes(clientOAuth2DataConfiguration.getGrantTypes())
                // token 有效期
                .accessTokenValiditySeconds(clientOAuth2DataConfiguration.getTokenValidityTime())
                // 刷新 token 的有效期
                .refreshTokenValiditySeconds(clientOAuth2DataConfiguration.getRefreshTokenValidityTime())
                // 客户端访问范围
                .scopes(clientOAuth2DataConfiguration.getScopes());
    }

    /**
     * 配置授权以及令牌的访问端点和令牌服务
     */
//    @Override
//    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
//        // 认证器
//        endpoints.authenticationManager(authenticationManager)
//                // 刷新令牌必须配置userDetailsService，用来刷新令牌时的认证
//                .userDetailsService(userService)
//                // token 存储的方式：Redis
//                .tokenStore(redisTokenStore);
//    }

    /**
     * 配置令牌端点安全约束
     */
    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        // 允许访问 token 的公钥，默认 /oauth/token_key 是受保护的
        security.tokenKeyAccess("permitAll()")
                // 允许检查 token 的状态，默认 /oauth/check_token 是受保护的
                .checkTokenAccess("permitAll()");
    }

    /**
     * 配置授权以及令牌的访问端点和令牌服务
     */
    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        // 认证器
        endpoints.authenticationManager(authenticationManager)
                // 刷新令牌必须配置userDetailsService，用来刷新令牌时的认证
                .userDetailsService(userService)
                // token 存储的方式：Redis
                .tokenStore(redisTokenStore)
                // 令牌增强对象，增强返回的结果
                .tokenEnhancer((accessToken, authentication) -> {
                    // 获取登录用户的信息，然后设置
                    SignInIdentity signInIdentity = (SignInIdentity) authentication.getPrincipal();
                    LinkedHashMap<String, Object> map = new LinkedHashMap<>();
                    map.put("nickname", signInIdentity.getNickname());
                    map.put("avatarUrl", signInIdentity.getAvatarUrl());
                    DefaultOAuth2AccessToken token = (DefaultOAuth2AccessToken) accessToken;
                    token.setAdditionalInformation(map);
                    return token;
                });
    }

}


