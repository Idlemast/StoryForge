package com.storyforge.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SceneTest {

    @Test
    void rejectsBlankTitleOrContent() {
        // on vérifie qu'une scène sans titre lève bien une erreur
        assertThrows(IllegalArgumentException.class,
                () -> new Scene("", "Lieu", "Moment", "Contenu", SceneStatus.BROUILLON));
        // on vérifie aussi qu'une scène avec un contenu composé uniquement d'espaces lève une erreur
        assertThrows(IllegalArgumentException.class,
                () -> new Scene("Titre", "Lieu", "Moment", " ", SceneStatus.BROUILLON));
    }

    @Test
    void rejectsNullStatus() {
        // on vérifie qu'une scène sans statut (null) lève bien une erreur
        assertThrows(IllegalArgumentException.class,
                () -> new Scene("Titre", "Lieu", "Moment", "Contenu", null));
    }
}
