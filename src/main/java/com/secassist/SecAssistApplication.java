package com.secassist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt der SecAssist-Anwendung.
 *
 * <p>Die Klasse startet die Spring-Boot-Anwendung und verbindet damit alle
 * zentralen Bausteine des Workshops: Web-Endpunkte, Policy-Pruefung,
 * Retrieval-Logik, LLM-Anbindung und simulierte Workflow-Aktionen. Sie selbst
 * enthaelt bewusst keine Fachlogik, sondern markiert nur den technischen
 * Startpunkt des Systems.</p>
 *
 * <p>SecAssist ist als kleine, gut lesbare Workshop-Anwendung aufgebaut. Auch
 * die absichtlich eingebauten Schwachstellen liegen daher nicht in dieser
 * Bootstrap-Klasse, sondern in den fachlichen Komponenten, in denen
 * Teilnehmerinnen und Teilnehmer die Sicherheitslogik nachvollziehen und
 * gezielt verbessern koennen.</p>
 */
@SpringBootApplication
public class SecAssistApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecAssistApplication.class, args);
    }
}
