package com.goodthingjar.notification.infrastructure;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailGateway {
  private final JavaMailSender sender;

  public SmtpMailGateway(JavaMailSender sender) {
    this.sender = sender;
  }

  public void sendVerification(String recipient, String token) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(recipient);
    message.setSubject("Verify your Good Thing Jar account");
    message.setText("Use this verification token: " + token);
    sender.send(message);
  }

  public void sendInvitation(String recipient, String payload) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(recipient);
    message.setSubject("Your Good Thing Jar invitation");
    message.setText(payload);
    sender.send(message);
  }
}
