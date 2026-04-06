package com.alertsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    
    @Autowired(required = false)
    private JavaMailSender mailSender;
    
    @Async
    public void sendEmail(String to, String subject, String body) {
        try {
            if (mailSender == null) {
                logger.warn("Mail sender not configured, logging instead: To: {}, Subject: {}", to, subject);
                return;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            message.setFrom("noreply@alertsystem.com");
            
            mailSender.send(message);
            logger.info("Email sent to {}", to);
            
        } catch (Exception e) {
            logger.error("Error sending email", e);
        }
    }
}