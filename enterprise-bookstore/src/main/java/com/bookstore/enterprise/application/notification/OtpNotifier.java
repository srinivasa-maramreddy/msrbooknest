package com.bookstore.enterprise.application.notification;

import com.bookstore.enterprise.domain.model.VerificationChannel;

public interface OtpNotifier {

    void sendOtp(VerificationChannel channel, String destination, String otpCode);
}
