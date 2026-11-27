package com.blogsphere.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    public boolean isConfigured() {
        return mailSender != null
            && fromEmail != null && !fromEmail.isBlank()
            && smtpPassword != null && !smtpPassword.isBlank();
    }

    public boolean sendPasswordResetEmail(String toEmail, String verificationCode) {
        if (!isConfigured()) {
            logger.error("Password recovery email is not configured; set MAIL_USERNAME and MAIL_PASSWORD.");
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("BlogSphere - Password Reset Verification Code");

            String htmlContent = "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "  <style>"
                + "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; margin: 0; padding: 20px; color: #1e293b; }"
                + "    .card { max-width: 500px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }"
                + "    .header { background: #2563eb; padding: 24px; text-align: center; color: white; }"
                + "    .header h1 { margin: 0; font-size: 24px; font-weight: 700; }"
                + "    .body { padding: 30px 24px; }"
                + "    .code-box { background: #eff6ff; border: 2px dashed #3b82f6; border-radius: 8px; padding: 18px; text-align: center; margin: 24px 0; }"
                + "    .code { font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #1d4ed8; font-family: monospace; }"
                + "    .footer { padding: 18px 24px; background: #f1f5f9; text-align: center; font-size: 12px; color: #64748b; }"
                + "  </style>"
                + "</head>"
                + "<body>"
                + "  <div class=\"card\">"
                + "    <div class=\"header\">"
                + "      <h1>BlogSphere</h1>"
                + "    </div>"
                + "    <div class=\"body\">"
                + "      <h2 style=\"margin-top:0; color:#0f172a; font-size:18px;\">Password Reset Request</h2>"
                + "      <p style=\"font-size:14px; line-height:1.6; color:#475569;\">"
                + "        We received a request to reset your password for your BlogSphere account. Use the 6-digit verification code below to reset your password:"
                + "      </p>"
                + "      <div class=\"code-box\">"
                + "        <div class=\"code\">" + verificationCode + "</div>"
                + "      </div>"
                + "      <p style=\"font-size:13px; line-height:1.5; color:#64748b;\">"
                + "        This verification code is valid for <strong>5 minutes</strong>. If you did not make this request, you can safely ignore this email."
                + "      </p>"
                + "    </div>"
                + "    <div class=\"footer\">"
                + "      &copy; 2026 BlogSphere Platform. All rights reserved."
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";

            helper.setText(htmlContent, true);
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            logger.error("Failed to dispatch password recovery email ({})", e.getClass().getSimpleName());
            return false;
        }
    }
}
