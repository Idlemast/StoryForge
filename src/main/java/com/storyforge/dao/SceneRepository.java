package com.storyforge.dao;

import com.storyforge.model.Character;
import com.storyforge.model.Scene;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Abstraction de la persistance des scènes, y compris la relation
 * Many-To-Many avec les personnages (table de jonction scene_characters
 * côté implémentation JDBC).
 */
public interface SceneRepository {
    /**
     * Charge toutes les scènes d'une histoire. charactersById permet de
     * résoudre les personnages présents en réutilisant les instances déjà
     * chargées pour cette histoire (plutôt que de recréer des doublons).
     */
    List<Scene> findByStoryId(long storyId, Map<Long, Character> charactersById) throws SQLException; // donne toutes les scènes d'une histoire précise
    /** Insère une nouvelle scène rattachée à l'histoire storyId. */
    void insert(Scene scene, long storyId) throws SQLException; // enregistre une nouvelle scène en base
    /** Met à jour une scène déjà persistée. */
    void update(Scene scene) throws SQLException; // enregistre les modifications d'une scène existante
    /** Supprime une scène (entraîne la suppression de ses associations en cascade). */
    void delete(long id) throws SQLException; // supprime une scène de la base à partir de son identifiant
    /** Associe un personnage à une scène (ligne dans la table de jonction). */
    void addCharacter(long sceneId, long characterId) throws SQLException; // enregistre qu'un personnage apparaît dans une scène
    /** Retire l'association entre un personnage et une scène. */
    void removeCharacter(long sceneId, long characterId) throws SQLException; // supprime le lien entre un personnage et une scène
}
