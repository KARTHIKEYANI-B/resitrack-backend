package com.resitrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.cdimascio.dotenv.Dotenv;


@SpringBootApplication
@EnableScheduling
public class ResiTrackApplication {
    public static void main(String[] args) {
   
            SpringApplication.run(ResiTrackApplication.class, args);
    }
}
