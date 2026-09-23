package com.netbanking.notification.service;
import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.context.annotation.Profile; import org.springframework.stereotype.Service;
@Service @Profile("local") public class DevelopmentSmsService implements SmsService { private static final Logger log=LoggerFactory.getLogger(DevelopmentSmsService.class); public void send(String recipient,String message){log.info("Development SMS queued for {}",recipient);} }
