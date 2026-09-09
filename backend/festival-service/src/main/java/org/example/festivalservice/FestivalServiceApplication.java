package org.example.festivalservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FestivalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FestivalServiceApplication.class, args);
    }

}
