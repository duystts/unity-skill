package com.unityskill.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@unityskill.app}")
    private String fromAddress;

    @Value("${app.mail.from-name:unity_skill}")
    private String fromName;

    /**
     * Send workspace invitation email asynchronously.
     * Failure is logged but does NOT propagate — invitation is already saved.
     */
    @Async
    public void sendInvitationEmail(
            String toEmail,
            String workspaceName,
            String inviteUrl,
            Instant expiresAt
    ) {
        try {
            String expiry = DateTimeFormatter
                    .ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("UTC"))
                    .format(expiresAt);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(String.format("%s <%s>", fromName, fromAddress));
            helper.setTo(toEmail);
            helper.setSubject(String.format("You've been invited to %s on unity_skill", workspaceName));
            helper.setText(buildHtml(workspaceName, inviteUrl, expiry), true);

            mailSender.send(message);
            log.info("Invitation email sent to {}", toEmail);

        } catch (MessagingException e) {
            log.error("Failed to send invitation email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildHtml(String workspaceName, String inviteUrl, String expiry) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>Workspace Invitation</title>
            </head>
            <body style="margin:0;padding:0;background:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:40px 16px;">
                <tr><td align="center">
                  <table width="520" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.08);">

                    <!-- Header -->
                    <tr>
                      <td style="background:#4f46e5;padding:28px 40px;">
                        <p style="margin:0;font-size:20px;font-weight:700;color:#ffffff;letter-spacing:-0.5px;">
                          unity_skill
                        </p>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:36px 40px 28px;">
                        <h1 style="margin:0 0 8px;font-size:22px;font-weight:700;color:#0f172a;">
                          You're invited!
                        </h1>
                        <p style="margin:0 0 24px;font-size:15px;color:#475569;line-height:1.6;">
                          You've been invited to join the workspace
                          <strong style="color:#0f172a;">%s</strong>
                          on unity_skill.
                        </p>

                        <!-- CTA Button -->
                        <table cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                          <tr>
                            <td style="background:#4f46e5;border-radius:10px;">
                              <a href="%s"
                                 style="display:inline-block;padding:13px 28px;font-size:15px;font-weight:600;
                                        color:#ffffff;text-decoration:none;letter-spacing:-0.2px;">
                                Accept invitation →
                              </a>
                            </td>
                          </tr>
                        </table>

                        <!-- Fallback link -->
                        <p style="margin:0 0 8px;font-size:13px;color:#94a3b8;">
                          Or copy this link into your browser:
                        </p>
                        <p style="margin:0 0 24px;font-size:12px;color:#6366f1;word-break:break-all;">
                          <a href="%s" style="color:#6366f1;">%s</a>
                        </p>

                        <!-- Expiry notice -->
                        <table cellpadding="0" cellspacing="0" width="100%%">
                          <tr>
                            <td style="background:#fef9c3;border:1px solid #fde047;border-radius:8px;padding:12px 16px;">
                              <p style="margin:0;font-size:13px;color:#854d0e;">
                                ⏳ This invitation expires on <strong>%s</strong>.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="padding:16px 40px 28px;border-top:1px solid #f1f5f9;">
                        <p style="margin:0;font-size:12px;color:#94a3b8;line-height:1.6;">
                          If you didn't expect this invitation, you can safely ignore this email.<br/>
                          This message was sent by unity_skill.
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(workspaceName, inviteUrl, inviteUrl, inviteUrl, expiry);
    }
}
