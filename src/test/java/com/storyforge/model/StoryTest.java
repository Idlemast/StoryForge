package com.storyforge.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryTest {

    @Test
    void rejectsBlankTitleOrAuthor() {
        // on vérifie qu'une histoire sans titre lève bien une erreur
        assertThrows(IllegalArgumentException.class, () -> new Story("", "Auteur", ""));
        // on vérifie aussi qu'une histoire avec un auteur composé uniquement d'espaces lève une erreur
        assertThrows(IllegalArgumentException.class, () -> new Story("Titre", " ", ""));
    }

    @Test
    void rejectsDuplicateCharacterNameIgnoringCase() {
        Story story = new Story("Titre", "Auteur", ""); // on crée une histoire de test
        story.addCharacter(new Character("Arthur", "Roi", "")); // on y ajoute un premier personnage nommé "Arthur"
        // on vérifie qu'ajouter un second personnage avec le même nom (même en minuscules) est refusé
        assertThrows(IllegalArgumentException.class, () -> story.addCharacter(new Character("arthur", "Chevalier", "")));
    }

    @Test
    void removingCharacterRemovesItFromItsScenesToo() {
        Story story = new Story("Titre", "Auteur", ""); // on crée une histoire de test
        Character arthur = new Character("Arthur", "Roi", ""); // on crée un personnage
        story.addCharacter(arthur); // on l'ajoute à l'histoire
        Scene scene = new Scene("Scène", "Lieu", "Moment", "Contenu", SceneStatus.BROUILLON); // on crée une scène
        story.addScene(scene); // on l'ajoute à l'histoire
        story.addCharacterToScene(scene, arthur); // on place le personnage dans cette scène

        story.removeCharacter(arthur); // on supprime le personnage de l'histoire

        // on vérifie que le personnage a bien disparu aussi de la liste des présents de la scène
        assertTrue(scene.getPresentCharacters().isEmpty());
    }

    @Test
    void onlyCastMembersCanBeAddedToAScene() {
        Story story = new Story("Titre", "Auteur", ""); // on crée une histoire de test
        Character outsider = new Character("Inconnu", "Figurant", ""); // un personnage qui n'appartient PAS à cette histoire
        Scene scene = new Scene("Scène", "Lieu", "Moment", "Contenu", SceneStatus.BROUILLON); // on crée une scène
        story.addScene(scene); // on l'ajoute à l'histoire

        // on vérifie qu'on ne peut pas mettre dans la scène un personnage qui n'appartient pas au casting de l'histoire
        assertThrows(IllegalArgumentException.class, () -> story.addCharacterToScene(scene, outsider));
    }

    @Test
    void scenePositionsStayContiguousAfterRemovalAndMove() {
        Story story = new Story("Titre", "Auteur", ""); // on crée une histoire de test
        Scene s1 = new Scene("S1", "", "", "c", SceneStatus.BROUILLON); // trois scènes de test
        Scene s2 = new Scene("S2", "", "", "c", SceneStatus.BROUILLON);
        Scene s3 = new Scene("S3", "", "", "c", SceneStatus.BROUILLON);
        story.addScene(s1); // ajoutées dans l'ordre : s1 (position 1), s2 (position 2), s3 (position 3)
        story.addScene(s2);
        story.addScene(s3);

        story.removeScene(s2); // on retire la scène du milieu
        // on vérifie que les scènes restantes ont bien été renumérotées sans trou : s1=1, s3=2
        assertEquals(1, s1.getPosition());
        assertEquals(2, s3.getPosition());

        story.moveScene(s3, 0); // on déplace s3 en première position
        // on vérifie que les positions ont bien été recalculées : s3=1, s1=2
        assertEquals(1, s3.getPosition());
        assertEquals(2, s1.getPosition());
    }
}
