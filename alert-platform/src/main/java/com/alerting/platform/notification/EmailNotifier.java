package com.alerting.platform.notification;

import com.alerting.platform.alerts.model.Alert;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotifier {

    private final JavaMailSender mailSender;

    @Value("${alerting.notifications.email.enabled}")
    private boolean enabled;

    @Value("${alerting.notifications.email.from}")
    private String fromAddress;

    @Value("${alerting.notifications.email.recipients}")
    private String recipients;

    public boolean send(Alert alert) {
        if (!enabled) {
            log.debug("Email notifications disabled");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(recipients.split(","));
            helper.setSubject(buildSubject(alert));
            helper.setText(buildHtmlBody(alert), true);

            mailSender.send(message);
            log.info("Email notification sent for alert {}", alert.getId());
            return true;

        } catch (MessagingException e) {
            log.error("Failed to send email notification: {}", e.getMessage());
            return false;
        }
    }

    private String buildSubject(Alert alert) {
        return String.format("[%s] %s - %s", 
            alert.getSeverity(), 
            alert.getAppId(), 
            alert.getRuleName());
    }

    private String buildHtmlBody(Alert alert) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; }
                    .alert-box { 
                        border: 2px solid %s; 
                        border-radius: 8px; 
                        padding: 20px; 
                        margin: 20px;
                    }
                    .header { 
                        font-size: 24px; 
                        font-weight: bold; 
                        color: %s; 
                    }
                    .metric { 
                        background: #f5f5f5; 
                        padding: 10px; 
                        margin: 10px 0; 
                        border-radius: 4px;
                    }
                    .label { font-weight: bold; color: #666; }
                    .value { font-size: 18px; }
                </style>
            </head>
            <body>
                <div class="alert-box">
                    <div class="header">🚨 %s Alert: %s</div>
                    <hr/>
                    
                    <div class="metric">
                        <span class="label">Application:</span>
                        <span class="value">%s</span>
                    </div>
                    
                    <div class="metric">
                        <span class="label">Feature:</span>
                        <span class="value">%s</span>
                    </div>
                    
                    <div class="metric">
                        <span class="label">Metric Value:</span>
                        <span class="value">%.2f</span>
                    </div>
                    
                    <div class="metric">
                        <span class="label">Threshold:</span>
                        <span class="value">%.2f</span>
                    </div>
                    
                    <div class="metric">
                        <span class="label">Sample Size:</span>
                        <span class="value">%d events</span>
                    </div>
                    
                    <hr/>
                    <p><strong>Message:</strong></p>
                    <pre>%s</pre>
                    
                    <hr/>
                    <small>Triggered at: %s</small>
                </div>
            </body>
            </html>
            """,
            getSeverityColor(alert.getSeverity()),
            getSeverityColor(alert.getSeverity()),
            alert.getSeverity(),
            alert.getRuleName(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getMetricValue(),
            alert.getThreshold(),
            alert.getSampleSize(),
            alert.getMessage(),
            alert.getTriggeredAt()
        );
    }

    private String getSeverityColor(com.alerting.platform.rules.model.AlertRule.Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#FF0000";
            case HIGH -> "#FF6600";
            case MEDIUM -> "#FFCC00";
            case LOW -> "#00CC00";
        };
    }
}

