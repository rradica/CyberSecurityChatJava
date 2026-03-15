package com.secassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecAssist – Security Incident Triage Chatbot.
 *
 * <p>Workshop-Anwendung für eine Red-Team-vs-Blue-Team-Übung zum Thema
 * CyberSecurity im AI-Umfeld. Enthält bewusst eingebaute Schwachstellen
 * mit entsprechenden FIX-Kommentaren an jeder Codestelle.</p>
 */
@SpringBootApplication
public class SecAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecAssistApplication.class, args);
    }
}
