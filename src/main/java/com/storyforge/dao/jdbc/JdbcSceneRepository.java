package com.storyforge.dao.jdbc;

import com.storyforge.dao.SceneRepository;
import com.storyforge.db.Database;
import com.storyforge.model.Character;
import com.storyforge.model.Scene;
import com.storyforge.model.SceneStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implémentation JDBC/MySQL de SceneRepository.
 * La relation Many-To-Many avec Character est gérée via la table de jonction
 * scene_characters (clé composite scene_id + character_id, voir schema.sql).
 */
public class JdbcSceneRepository implements SceneRepository {

    @Override
    public List<Scene> findByStoryId(long storyId, Map<Long, Character> charactersById) throws SQLException {
        // ORDER BY position : l'ordre de la liste retournée doit correspondre à
        // l'ordre des positions, puisque Story s'appuie ensuite sur l'ordre de
        // la liste (et non plus sur une relecture du champ position) pour tout
        // réordonnancement (voir Story.renumberScenes).
        String sql = "SELECT id, title, location, moment, content, position, status FROM scenes WHERE story_id = ? ORDER BY position"; // requête qui charge les scènes triées par position
        List<Scene> scenes = new ArrayList<>(); // la liste qu'on va remplir avec les scènes trouvées
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, storyId); // on remplace le "?" par l'identifiant de l'histoire demandée
            try (ResultSet rs = statement.executeQuery()) { // on exécute la requête et on parcourt le résultat
                while (rs.next()) { // tant qu'il reste une ligne de résultat à lire
                    // Le statut est stocké en base comme le nom de l'enum (ex: "BROUILLON").
                    Scene scene = new Scene(
                            rs.getString("title"),
                            rs.getString("location"),
                            rs.getString("moment"),
                            rs.getString("content"),
                            SceneStatus.valueOf(rs.getString("status"))); // on reconstruit le statut à partir du texte stocké en base
                    scene.setId(rs.getLong("id")); // on lui donne l'identifiant qu'elle a en base
                    scene.setPositionInternal(rs.getInt("position")); // on restaure sa position telle que lue en base
                    loadCharacters(connection, scene, charactersById); // on va chercher les personnages présents dans cette scène
                    scenes.add(scene); // on ajoute cette scène à la liste de résultat
                }
            }
        }
        return scenes; // on renvoie la liste complète des scènes trouvées, déjà triées
    }

    /** Charge les personnages présents dans une scène via la table de jonction. */
    private void loadCharacters(Connection connection, Scene scene, Map<Long, Character> charactersById) throws SQLException {
        String sql = "SELECT character_id FROM scene_characters WHERE scene_id = ?"; // requête qui liste les liens scène-personnage
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, scene.getId()); // on remplace le "?" par l'identifiant de la scène
            try (ResultSet rs = statement.executeQuery()) { // on exécute la requête et on parcourt le résultat
                while (rs.next()) { // tant qu'il reste un lien à lire
                    // Réutilise l'instance Character déjà chargée pour l'histoire (cf. JdbcStoryRepository).
                    Character character = charactersById.get(rs.getLong("character_id")); // on retrouve l'objet Character correspondant à cet identifiant
                    if (character != null) {
                        // on ajoute ce personnage à la liste des présents de la scène, seulement s'il a bien été trouvé
                        scene.getPresentCharacters().add(character);
                    }
                }
            }
        }
    }

    @Override
    public void insert(Scene scene, long storyId) throws SQLException {
        String sql = "INSERT INTO scenes (story_id, title, location, moment, content, position, status) VALUES (?, ?, ?, ?, ?, ?, ?)"; // requête qui crée une nouvelle ligne
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, storyId); // identifiant de l'histoire à laquelle appartient cette scène
            statement.setString(2, scene.getTitle()); // titre de la scène
            statement.setString(3, scene.getLocation()); // lieu de la scène
            statement.setString(4, scene.getMoment()); // moment de la scène
            statement.setString(5, scene.getContent()); // contenu de la scène
            statement.setInt(6, scene.getPosition()); // position de la scène dans l'histoire
            statement.setString(7, scene.getStatus().name()); // statut de la scène, converti en texte
            statement.executeUpdate(); // on exécute réellement l'insertion en base
            try (ResultSet keys = statement.getGeneratedKeys()) { // on récupère l'identifiant généré automatiquement par MySQL
                if (keys.next()) {
                    scene.setId(keys.getLong(1)); // on stocke ce nouvel identifiant dans l'objet Java
                }
            }
        }
    }

    @Override
    public void update(Scene scene) throws SQLException {
        String sql = "UPDATE scenes SET title = ?, location = ?, moment = ?, content = ?, position = ?, status = ? WHERE id = ?"; // requête qui modifie une ligne existante
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, scene.getTitle()); // nouvelle valeur du titre
            statement.setString(2, scene.getLocation()); // nouvelle valeur du lieu
            statement.setString(3, scene.getMoment()); // nouvelle valeur du moment
            statement.setString(4, scene.getContent()); // nouvelle valeur du contenu
            statement.setInt(5, scene.getPosition()); // nouvelle valeur de la position
            statement.setString(6, scene.getStatus().name()); // nouvelle valeur du statut
            statement.setLong(7, scene.getId()); // identifiant de la scène à modifier
            statement.executeUpdate(); // on exécute réellement la mise à jour en base
        }
    }

    @Override
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM scenes WHERE id = ?"; // requête qui supprime une ligne
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id); // identifiant de la scène à supprimer
            statement.executeUpdate(); // on exécute réellement la suppression en base
        }
    }

    @Override
    public void addCharacter(long sceneId, long characterId) throws SQLException {
        // INSERT IGNORE : idempotent, n'échoue pas si l'association existe déjà
        // (clé primaire composite scene_id + character_id en doublon).
        String sql = "INSERT IGNORE INTO scene_characters (scene_id, character_id) VALUES (?, ?)"; // requête qui crée le lien scène-personnage
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sceneId); // identifiant de la scène
            statement.setLong(2, characterId); // identifiant du personnage
            statement.executeUpdate(); // on exécute réellement l'insertion du lien en base
        }
    }

    @Override
    public void removeCharacter(long sceneId, long characterId) throws SQLException {
        String sql = "DELETE FROM scene_characters WHERE scene_id = ? AND character_id = ?"; // requête qui supprime le lien scène-personnage
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sceneId); // identifiant de la scène
            statement.setLong(2, characterId); // identifiant du personnage
            statement.executeUpdate(); // on exécute réellement la suppression du lien en base
        }
    }
}
