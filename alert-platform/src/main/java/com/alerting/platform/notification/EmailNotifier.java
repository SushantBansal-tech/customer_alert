package com.alerting.platform.notification;

import com.alerting.platform.alerts.model.Alert;
import com.alerting.platform.rules.model.AlertRule.Severity;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotifier {

    private final JavaMailSender mailSender;

    @Value("${alerting.notifications.email.enabled:true}")
    private boolean enabled;

    @Value("${alerting.notifications.email.from:alerts@company.com}")
    private String fromAddress;

    @Value("${alerting.notifications.email.recipients:}")
    private String defaultRecipients;

    // ========== Main Send Method ==========

    public boolean send(Alert alert) {
        if (!enabled || defaultRecipients.isBlank()) {
            log.debug("Email notifications disabled or no recipients configured");
            return false;
        }

        return sendToRecipients(
            defaultRecipients.split(","),
            buildSubject(alert),
            buildHtmlBody(alert)
        );
    }

    // ========== Send to Specific Recipients ==========

    @Async("notificationExecutor")
    public boolean sendToRecipients(String[] recipients, String subject, String body) {
        if (!enabled || recipients == null || recipients.length == 0) {
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(body, true);

            mailSender.send(message);
            log.info("Email sent to {} recipients: {}", recipients.length, subject);
            return true;

        } catch (MessagingException e) {
            log.error("Failed to send email: {}", e.getMessage());
            return false;
        }
    }

    // ========== Escalation Email ==========

    public void sendEscalationEmail(String email, Alert alert, int level) {
        String subject = String.format("[ESCALATION L%d][%s] %s - %s",
            level,
            alert.getSeverity(),
            alert.getAppId(),
            alert.getRuleName()
        );

        String body = buildEscalationHtmlBody(alert, level);
        sendToRecipients(new String[]{email}, subject, body);
    }

    // ========== HTML Body Builders ==========

    private String buildSubject(Alert alert) {
        return String.format("[%s] %s - %s",
            alert.getSeverity(),
            alert.getAppId(),
            alert.getRuleName()
        );
    }

    private String buildHtmlBody(Alert alert) {
        String severityColor = getSeverityColor(alert.getSeverity());
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .alert-header { 
                        background: %s; 
                        color: white; 
                        padding: 20px; 
                        border-radius: 8px 8px 0 0;
                        text-align: center;
                    }
                    .alert-body { 
                        border: 1px solid #ddd; 
                        border-top: none;
                        border-radius: 0 0 8px 8px;
                        padding: 20px;
                    }
                    .metric-row { 
                        display: flex; 
                        justify-content: space-between;
                        padding: 10px 0;
                        border-bottom: 1px solid #eee;
                    }
                    .metric-label { font-weight: bold; color: #666; }
                    .metric-value { font-size: 16px; }
                    .message-box {
                        background: #f5f5f5;
                        padding: 15px;
                        border-radius: 4px;
                        margin: 15px 0;
                        white-space: pre-wrap;
                    }
                    .footer { 
                        text-align: center; 
                        color: #888; 
                        font-size: 12px;
                        margin-top: 20px;
                    }
                    .cta-button {
                        display: inline-block;
                        background: %s;
                        color: white;
                        padding: 12px 24px;
                        text-decoration: none;
                        border-radius: 4px;
                        margin-top: 15px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="alert-header">
                        <h1>🚨 %s Alert</h1>
                        <h2>%s</h2>
                    </div>
                    
                    <div class="alert-body">
                        <div class="metric-row">
                            <span class="metric-label">Application</span>
                            <span class="metric-value">%s</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Feature</span>
                            <span class="metric-value">%s</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Alert Type</span>
                            <span class="metric-value">%s</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Metric Value</span>
                            <span class="metric-value">%.2f</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Threshold</span>
                            <span class="metric-value">%.2f</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Sample Size</span>
                            <span class="metric-value">%d events</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Status</span>
                            <span class="metric-value">%s</span>
                        </div>
                        
                        <div class="metric-row">
                            <span class="metric-label">Assigned To</span>
                            <span class="metric-value">%s</span>
                        </div>
                        
                        <h3>Message</h3>
                        <div class="message-box">%s</div>
                        
                        <div style="text-align: center;">
                            <a href="#" class="cta-button">View Alert Details</a>
                        </div>
                    </div>
                    
                    <div class="footer">
                        <p>Alert ID: %d | Triggered: %s</p>
                        <p>Customer Experience Alerting Platform</p>
                    </div>
                </div>
            </body>
            </html>
            """,
            severityColor,
            severityColor,
            alert.getSeverity(),
            alert.getRuleName(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getAlertType(),
            alert.getMetricValue(),
            alert.getThreshold(),
            alert.getSampleSize(),
            alert.getStatus(),
            alert.getAssignee() != null ? alert.getAssignee().getName() : "Unassigned",
            alert.getMessage(),
            alert.getId(),
            alert.getTriggeredAt()
        );
    }

    private String buildEscalationHtmlBody(Alert alert, int level) {
        String severityColor = getSeverityColor(alert.getSeverity());
        String escalationColor = level >= 2 ? "#FF0000" : "#FF6600";
        
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .escalation-banner {
                        background: %s;
                        color: white;
                        padding: 15px;
                        text-align: center;
                        font-size: 18px;
                        font-weight: bold;
                        border-radius: 8px;
                        margin-bottom: 20px;
                    }
                    .alert-header { 
                        background: %s; 
                        color: white; 
                        padding: 20px; 
                        border-radius: 8px 8px 0 0;
                    }
                    .alert-body { 
                        border: 1px solid #ddd; 
                        border-top: none;
                        border-radius: 0 0 8px 8px;
                        padding: 20px;
                    }
                    .metric-row { 
                        padding: 8px 0;
                        border-bottom: 1px solid #eee;
                    }
                    .urgent-message {
                        background: #FFF3CD;
                        border: 1px solid #FFE69C;
                        padding: 15px;
                        border-radius: 4px;
                        margin: 15px 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="escalation-banner">
                        ⚠️ ESCALATION LEVEL %d ⚠️
                    </div>
                    
                    <div class="urgent-message">
                        <strong>This alert has not been acknowledged and requires immediate attention.</strong>
                    </div>
                    
                    <div class="alert-header">
                        <h2>%s - %s</h2>
                    </div>
                    
                    <div class="alert-body">
                        <div class="metric-row">
                            <strong>Application:</strong> %s
                        </div>
                        <div class="metric-row">
                            <strong>Feature:</strong> %s
                        </div>
                        <div class="metric-row">
                            <strong>Severity:</strong> %s
                        </div>
                        <div class="metric-row">
                            <strong>Triggered:</strong> %s
                        </div>
                        <div class="metric-row">
                            <strong>Assigned Team:</strong> %s
                        </div>
                        <div class="metric-row">
                            <strong>Assigned To:</strong> %s
                        </div>
                        <div class="metric-row">
                            <strong>Current Status:</strong> %s
                        </div>
                        
                        <h3>Details</h3>
                        <pre style="background: #f5f5f5; padding: 10px; border-radius: 4px;">%s</pre>
                    </div>
                </div>
            </body>
            </html>
            """,
            escalationColor,
            severityColor,
            level,
            alert.getSeverity(),
            alert.getRuleName(),
            alert.getAppId(),
            alert.getFeature(),
            alert.getSeverity(),
            alert.getTriggeredAt(),
            alert.getAssignedTeam() != null ? alert.getAssignedTeam().getName() : "Unassigned",
            alert.getAssignee() != null ? alert.getAssignee().getName() : "Unassigned",
            alert.getStatus(),
            alert.getMessage()
        );
    }

    private String getSeverityColor(Severity severity) {
        return switch (severity) {
            case CRITICAL -> "#DC3545";
            case HIGH -> "#FD7E14";
            case MEDIUM -> "#FFC107";
            case LOW -> "#28A745";
        };
    }
}