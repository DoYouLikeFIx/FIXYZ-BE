package com.fix.channel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisIndexedHttpSession(
    redisNamespace = "fixyz:channel:session",
    maxInactiveIntervalInSeconds = 1800
)
public class ChannelSessionConfig {

  @Bean
  public CookieSerializer cookieSerializer(
      @Value("${server.servlet.session.cookie.name:SESSION}") String cookieName,
      @Value("${server.servlet.session.cookie.http-only:true}") boolean httpOnly,
      @Value("${server.servlet.session.cookie.same-site:strict}") String sameSite,
      @Value("${server.servlet.session.cookie.secure:false}") boolean secure
  ) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(cookieName);
    serializer.setUseHttpOnlyCookie(httpOnly);
    serializer.setUseSecureCookie(secure);
    serializer.setSameSite(sameSite);
    serializer.setUseBase64Encoding(false);
    serializer.setCookiePath("/");
    return serializer;
  }
}
