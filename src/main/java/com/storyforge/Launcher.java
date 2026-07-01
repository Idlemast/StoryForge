package com.storyforge;

import javafx.application.Application;

/**
 * Point d'entrée du programme (méthode main).
 * Sert d'intermédiaire avant de lancer l'application JavaFX : certains
 * environnements (notamment l'exécution depuis un .jar) refusent de lancer
 * directement une classe qui hérite de Application, d'où cette classe
 * séparée qui se contente d'appeler Application.launch(...).
 */
public class Launcher {
    public static void main(String[] args) {
        // Délègue tout le cycle de vie JavaFX (init, start, stop) à StoryForgeApp.
        Application.launch(StoryForgeApp.class, args); // démarre l'application JavaFX définie dans StoryForgeApp
    }
}
