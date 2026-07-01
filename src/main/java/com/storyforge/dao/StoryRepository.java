package com.storyforge.dao;

import com.storyforge.model.Story;

import java.sql.SQLException;
import java.util.List;

/**
 * Abstraction de la persistance des histoires (Dependency Inversion
 * Principle, étape 3) : StoryService dépend de cette interface, pas d'une
 * implémentation JDBC concrète. Permet par exemple de substituer une
 * implémentation en mémoire pour des tests, sans toucher au service.
 */
public interface StoryRepository {
    /** Charge toutes les histoires (avec leurs personnages et scènes). */
    List<Story> findAll() throws SQLException; // donne la liste de toutes les histoires sauvegardées
    /** Insère une nouvelle histoire et renseigne son id généré. */
    void insert(Story story) throws SQLException; // enregistre une nouvelle histoire en base
    /** Met à jour une histoire déjà persistée. */
    void update(Story story) throws SQLException; // enregistre les modifications d'une histoire existante
    /** Supprime une histoire (et ses personnages/scènes, via ON DELETE CASCADE en base). */
    void delete(long id) throws SQLException; // supprime une histoire de la base à partir de son identifiant
}
