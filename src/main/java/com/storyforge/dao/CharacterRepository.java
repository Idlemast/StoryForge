package com.storyforge.dao;

import com.storyforge.model.Character;

import java.sql.SQLException;
import java.util.List;

/**
 * Abstraction de la persistance des personnages (voir StoryRepository pour
 * la justification du Dependency Inversion Principle).
 */
public interface CharacterRepository {
    /** Charge tous les personnages d'une histoire donnée. */
    List<Character> findByStoryId(long storyId) throws SQLException; // donne tous les personnages d'une histoire précise
    /** Insère un nouveau personnage rattaché à l'histoire storyId. */
    void insert(Character character, long storyId) throws SQLException; // enregistre un nouveau personnage en base
    /** Met à jour un personnage déjà persisté. */
    void update(Character character) throws SQLException; // enregistre les modifications d'un personnage existant
    /** Supprime un personnage. */
    void delete(long id) throws SQLException; // supprime un personnage de la base à partir de son identifiant
}
