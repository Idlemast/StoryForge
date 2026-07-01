package com.storyforge.dao.jdbc;

import com.storyforge.dao.CharacterRepository;
import com.storyforge.dao.SceneRepository;
import com.storyforge.dao.StoryRepository;
import com.storyforge.db.Database;
import com.storyforge.model.Character;
import com.storyforge.model.Scene;
import com.storyforge.model.Story;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implémentation JDBC/MySQL de StoryRepository.
 * Dépend des deux autres repositories (personnages, scènes) pour reconstituer
 * une histoire complète dans findAll() : c'est elle qui orchestre l'ordre de
 * chargement (histoire -> personnages -> scènes, car les scènes référencent
 * des personnages déjà chargés).
 */
public class JdbcStoryRepository implements StoryRepository {

    private final CharacterRepository characterRepository; // sert à charger les personnages de chaque histoire
    private final SceneRepository sceneRepository; // sert à charger les scènes de chaque histoire

    public JdbcStoryRepository(CharacterRepository characterRepository, SceneRepository sceneRepository) {
        this.characterRepository = characterRepository; // on mémorise le repository de personnages reçu
        this.sceneRepository = sceneRepository; // on mémorise le repository de scènes reçu
    }

    @Override
    public List<Story> findAll() throws SQLException {
        String sql = "SELECT id, title, author, summary FROM story"; // requête qui charge toutes les histoires
        List<Story> stories = new ArrayList<>(); // la liste qu'on va remplir avec les histoires trouvées
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) { // on exécute la requête et on parcourt le résultat
            while (rs.next()) { // tant qu'il reste une histoire à lire
                Story story = new Story(rs.getString("title"), rs.getString("author"), rs.getString("summary")); // on reconstruit l'histoire à partir des colonnes
                story.setId(rs.getLong("id")); // on lui donne l'identifiant qu'elle a en base
                // Map id -> Character : permet à sceneRepository.findByStoryId de réutiliser
                // les mêmes instances Java plutôt que de recréer des personnages dupliqués
                // quand il résout les personnages présents dans chaque scène.
                Map<Long, Character> charactersById = new HashMap<>(); // dictionnaire identifiant -> personnage, pour retrouver rapidement un personnage par son id
                for (Character character : characterRepository.findByStoryId(story.getId())) {
                    story.getCharacters().add(character); // on ajoute ce personnage à l'histoire
                    charactersById.put(character.getId(), character); // et on le mémorise dans le dictionnaire pour plus tard
                }
                for (Scene scene : sceneRepository.findByStoryId(story.getId(), charactersById)) {
                    story.getScenes().add(scene); // on ajoute chaque scène trouvée à l'histoire
                }
                stories.add(story); // on ajoute cette histoire complète (avec ses personnages et scènes) à la liste de résultat
            }
        }
        return stories; // on renvoie la liste complète des histoires trouvées
    }

    @Override
    public void insert(Story story) throws SQLException {
        String sql = "INSERT INTO story (title, author, summary) VALUES (?, ?, ?)"; // requête qui crée une nouvelle ligne
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, story.getTitle()); // titre de l'histoire
            statement.setString(2, story.getAuthor()); // auteur de l'histoire
            statement.setString(3, story.getSummary()); // résumé de l'histoire
            statement.executeUpdate(); // on exécute réellement l'insertion en base
            try (ResultSet keys = statement.getGeneratedKeys()) { // on récupère l'identifiant généré automatiquement par MySQL
                if (keys.next()) {
                    story.setId(keys.getLong(1)); // on stocke ce nouvel identifiant dans l'objet Java
                }
            }
        }
    }

    @Override
    public void update(Story story) throws SQLException {
        String sql = "UPDATE story SET title = ?, author = ?, summary = ? WHERE id = ?"; // requête qui modifie une ligne existante
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, story.getTitle()); // nouvelle valeur du titre
            statement.setString(2, story.getAuthor()); // nouvelle valeur de l'auteur
            statement.setString(3, story.getSummary()); // nouvelle valeur du résumé
            statement.setLong(4, story.getId()); // identifiant de l'histoire à modifier
            statement.executeUpdate(); // on exécute réellement la mise à jour en base
        }
    }

    @Override
    public void delete(long id) throws SQLException {
        // ON DELETE CASCADE côté schéma : les personnages et scènes associés sont supprimés par MySQL.
        String sql = "DELETE FROM story WHERE id = ?"; // requête qui supprime une ligne
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id); // identifiant de l'histoire à supprimer
            statement.executeUpdate(); // on exécute réellement la suppression en base (entraîne la suppression en cascade)
        }
    }
}
