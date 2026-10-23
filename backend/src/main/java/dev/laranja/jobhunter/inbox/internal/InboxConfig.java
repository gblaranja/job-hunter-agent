package dev.laranja.jobhunter.inbox.internal;

import dev.laranja.jobhunter.inbox.GmailProperties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GmailProperties.class)
class InboxConfig {

}
