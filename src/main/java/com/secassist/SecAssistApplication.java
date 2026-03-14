package com.secassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.secassist.config.BugFlagsProperties;

/**
 * SecAssist – Security Incident Triage Chatbot.
 *
 * <p>Workshop-Anwendung für eine Red-Team-vs-Blue-Team-Übung zum Thema
 * CyberSecurity im AI-Umfeld. Enthält bewusst eingebaute, per Feature-Flag
 * steuerbare Schwachstellen.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties(BugFlagsProperties.class)
public class SecAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecAssistApplication.class, args);
    }
}
