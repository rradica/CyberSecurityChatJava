package com.secassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SecAssist – Security Incident Triage Chatbot.
 *
 * <p>Workshop-Anwendung fuer eine Red-Team-vs-Blue-Team-Uebung zum Thema
 * CyberSecurity im AI-Umfeld. Enthaelt bewusst eingebaute Schwachstellen
 * mit entsprechenden FIX-Kommentaren an jeder Codestelle.</p>
 */
@SpringBootApplication
public class SecAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecAssistApplication.class, args);
    }
}
