package com.storyforge.dao.jdbc;

import com.storyforge.dao.CharacterRepository;
import com.storyforge.db.Database;
import com.storyforge.model.Character;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Implémentation JDBC/MySQL de CharacterRepository.
 * Chaque méthode ouvre sa propre connexion via Database.getConnection() et
 * la referme avec try-with-resources (pas de pool de connexions, suffisant
 * pour ce projet).
 */
public class JdbcCharacterRepository implements CharacterRepository {

    @Override
    public List<Character> findByStoryId(long storyId) throws SQLException {
        // `role` est entre backticks car c'est un mot réservé depuis MySQL 8.0.17.
        String sql = "SELECT id, name, `role`, description FROM characters WHERE story_id = ?"; // la requête SQL à exécuter, avec un "?" qui sera remplacé par storyId
        List<Character> characters = new ArrayList<>(); // la liste qu'on va remplir avec les personnages trouvés
        // try-with-resources : la connexion et la requête se referment automatiquement, même en cas d'erreur
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, storyId); // on remplace le "?" de la requête par l'identifiant de l'histoire demandée
            try (ResultSet rs = statement.executeQuery()) { // on exécute la requête et on récupère le résultat ligne par ligne
                while (rs.next()) { // tant qu'il reste une ligne de résultat à lire
                    // on construit un nouvel objet Character à partir des colonnes de la ligne courante
                    Character character = new Character(rs.getString("name"), rs.getString("role"), rs.getString("description"));
                    character.setId(rs.getLong("id")); // on lui donne l'identifiant qu'il a en base
                    characters.add(character); // on ajoute ce personnage à la liste de résultat
                }
            }
        }
        return characters; // on renvoie la liste complète des personnages trouvés
    }

    @Override
    public void insert(Character character, long storyId) throws SQLException {
        String sql = "INSERT INTO characters (story_id, name, `role`, description) VALUES (?, ?, ?, ?)"; // requête qui crée une nouvelle ligne
        try (Connection connection = Database.getConnection();
             // RETURN_GENERATED_KEYS permet de récupérer l'id auto-incrémenté généré par MySQL.
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, storyId); // 1er "?" : l'identifiant de l'histoire à laquelle appartient ce personnage
            statement.setString(2, character.getName()); // 2e "?" : le nom du personnage
            statement.setString(3, character.getRole()); // 3e "?" : le rôle du personnage
            statement.setString(4, character.getDescription()); // 4e "?" : la description du personnage
            statement.executeUpdate(); // on exécute réellement l'insertion en base
            try (ResultSet keys = statement.getGeneratedKeys()) { // on récupère l'identifiant généré automatiquement par MySQL
                if (keys.next()) {
                    // Renseigne l'id côté objet Java pour que les futurs appels sachent qu'il est déjà persisté.
                    character.setId(keys.getLong(1)); // on stocke ce nouvel identifiant dans l'objet Java
                }
            }
        }
    }

    @Override
    public void update(Character character) throws SQLException {
        String sql = "UPDATE characters SET name = ?, `role` = ?, description = ? WHERE id = ?"; // requête qui modifie une ligne existante
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, character.getName()); // nouvelle valeur du nom
            statement.setString(2, character.getRole()); // nouvelle valeur du rôle
            statement.setString(3, character.getDescription()); // nouvelle valeur de la description
            statement.setLong(4, character.getId()); // identifiant du personnage à modifier
            statement.executeUpdate(); // on exécute réellement la mise à jour en base
        }
    }

    @Override
    public void delete(long id) throws SQLException {
        String sql = "DELETE FROM characters WHERE id = ?"; // requête qui supprime une ligne
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id); // identifiant du personnage à supprimer
            statement.executeUpdate(); // on exécute réellement la suppression en base
        }
    }
}
