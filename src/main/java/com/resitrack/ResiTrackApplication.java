package com.resitrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.github.cdimascio.dotenv.Dotenv;


@SpringBootApplication
@EnableScheduling
public class ResiTrackApplication {
    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure()
                .directory("src/main/resources")
                .ignoreIfMissing()
                .load();
        dotenv.entries().forEach(entry ->
                System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(ResiTrackApplication.class, args);
    }
}
