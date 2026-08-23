package com.ssa.plugin.pushWay.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.ssa.core.utils.ApiResult;
import com.ssa.plugin.annotation.SsaApiClass;
import com.ssa.plugin.annotation.SsaApiMethod;
import com.ssa.plugin.pushWay.config.MailConfig;
import jakarta.annotation.Resource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * SMTP（Simple Mail Transfer Protocol）主要用于发送邮件，
 * POP3（Post Office Protocol 3）则用于接收邮件
 */
@SsaApiClass("mailSender")
@Service
@Slf4j
public class EmailSender {
    @Resource
    private MailConfig mailConfig;
    @SsaApiMethod
    public Object send(String email, String safeCode, int expireTime) {
        Properties properties = new Properties();
        properties.put("mail.smtp.auth", mailConfig.getSmtpAuth());
        properties.put("mail.smtp.starttls.enable", mailConfig.getSmtpStarttlsEnable());
        // 使用 163 邮箱的 SMTP 服务器地址
        properties.put("mail.smtp.host", mailConfig.getSmtpHost());
        // 端口一般 25 不加密，TLS加密 587 SSl加密 465
        // SSL 加密需要增加 properties.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        properties.put("mail.smtp.port", mailConfig.getSmtpPort());
        // 创建会话对象
        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(mailConfig.getSmtpUsername(), mailConfig.getSmtpPassword());
            }
        });
        // 邮件主题
        String subject = "SSA验证码";
        // 邮件内容
        String htmlContent = "<p>您的验证码为：<b>" + safeCode + "</b>，<b>" + expireTime + "</b> 分钟内有效。请妥善保管。</p>";
        try {
            // 创建邮件消息对象
            Message message = new MimeMessage(session);
            // 设置发件人
            message.setFrom(new InternetAddress(mailConfig.getSmtpUsername()));
            // 设置收件人
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
            // 设置邮件主题
            message.setSubject(subject);
            // 设置邮件内容为 HTML 格式
            message.setContent(htmlContent, "text/html; charset=utf-8");
            // 发送邮件
            Transport.send(message);
            log.info("邮件发送成功！");
            return ApiResult.success(null);
        } catch (MessagingException e) {
            log.error("邮件发送失败: {}", e.getMessage());
            return ApiResult.failed(e.getMessage());
        }
    }
}
