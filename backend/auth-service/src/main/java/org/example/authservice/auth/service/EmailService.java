package org.example.authservice.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Random;


// 실제 이메일을 보내는 역할의 클래스이다!!
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailService {

    // JavaMailSender 객체를 자바가 자동으로 등록
    private final JavaMailSender javaMailSender;


    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendVerificationCode(String toEmail, String code){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("FevalGo <" + fromEmail + ">");  //이메일 발송주체를 이메일->FevalGo로 덮어쓰기?
        message.setTo(toEmail);
        message.setSubject("[FevalGo] 이메일 인증코드입니다.");

        message.setText("FevalGo 회원가입 및 비밀번호 재설정을 위한 이메일 인증코드입니다.\n" +
                "인증 화면에 아래의 코드를 입력해 주시기 바랍니다.\n" +
                "인증코드: " + code + "\n\n" +
                "-인증코드 유효기간 안내\n" +
                "● 인증 코드는 1회만 유효합니다.\n" +
                "● 인증 코드는 메일이 발송된 후 5분이 지나면 만료됩니다.\n" +
                "● 인증 코드를 재전송하시는 경우, 이전 인증 코드를 사용하실 수 없습니다.\n" +
                "" +
                "-------------------------------------------------------\n " +
                "▽FevalGo 홈페이지\n https://www.fevalgo.duckdns.org");

        send(message, toEmail);
    }

    private void send(SimpleMailMessage message, String toEmail) {
        try {
            javaMailSender.send(message);
        } catch (Exception e) {
            // 비동기라 호출자에게 예외가 전달되지 않는다. 추적 수단은 이 로그뿐이다.
            log.error("메일 발송 실패. to={}, subject={}", toEmail, message.getSubject(), e);
        }
    }

    // 6자리 숫자 코드(우리가 회원가입때 입력해야할 인증코드) 생성-> 보안 강도 높임 secureRandom사용
    private static final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        int number = secureRandom.nextInt(1000000);
        return String.format("%06d", number);
    }

}
