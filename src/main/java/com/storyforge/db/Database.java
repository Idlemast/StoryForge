package com.storyforge.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Point d'accès unique aux connexions JDBC vers MySQL.
 * Les paramètres de connexion sont configurables à chaud (écran "Connexion
 * MySQL" de l'interface) plutôt que codés en dur, via configure().
 */
public final class Database {

    // ponytail: config en mémoire (pas de fichier) ; modifiable via l'écran
    // de connexion de l'appli. Valeurs par défaut pour une base locale.
    private static String host = "localhost"; // l'adresse du serveur MySQL
    private static int port = 3306; // le port du serveur MySQL
    private static String database = "storyforge"; // le nom de la base de données à utiliser
    private static String user = "root"; // le nom d'utilisateur MySQL
    private static String password = ""; // le mot de passe MySQL

    // Classe utilitaire : pas d'instanciation.
    private Database() {
    }

    /** Met à jour les paramètres de connexion utilisés par getConnection(). */
    public static void configure(String host, int port, String database, String user, String password) {
        // on remplace simplement chaque paramètre mémorisé par la nouvelle valeur reçue
        Database.host = host;
        Database.port = port;
        Database.database = database;
        Database.user = user;
        Database.password = password;
    }

    /** Ouvre une nouvelle connexion JDBC vers la base configurée. À fermer par l'appelant (try-with-resources). */
    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database; // on construit l'adresse complète de la base à partir des paramètres
        return DriverManager.getConnection(url, user, password); // on demande au pilote JDBC d'ouvrir une connexion vers cette adresse
    }

    /**
     * Crée la base configurée si elle n'existe pas encore, puis (re)joue le
     * schéma (CREATE TABLE IF NOT EXISTS) lu depuis db/schema.sql.
     * Appelée par l'écran de connexion avant le premier chargement des histoires.
     */
    public static void ensureDatabaseAndSchema() throws SQLException {
        // Étape 1 : se connecter au serveur sans préciser de base (elle peut ne pas exister encore).
        String serverUrl = "jdbc:mysql://" + host + ":" + port + "/"; // adresse du serveur seul, sans nom de base à la fin
        try (Connection connection = DriverManager.getConnection(serverUrl, user, password);
             Statement statement = connection.createStatement()) {
            // on crée la base seulement si elle n'existe pas déjà (IF NOT EXISTS évite une erreur sinon)
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database + "` CHARACTER SET utf8mb4");
        }
        // Étape 2 : se reconnecter à la base désormais existante et y créer les tables manquantes.
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : readTableStatements()) { // on relit chaque instruction de création de table du fichier schema.sql
                if (!sql.isBlank()) {
                    statement.executeUpdate(sql); // on exécute cette instruction (elle ne fait rien si la table existe déjà)
                }
            }
            // Migration : les bases créées avant l'introduction du glisser-déposer
            // ont une contrainte UNIQUE(story_id, position) sur scenes, retirée
            // depuis car elle empêche de persister un réordonnancement (deux
            // scènes peuvent se partager une position le temps des UPDATE
            // intermédiaires). CREATE TABLE IF NOT EXISTS ne la retire pas sur
            // une table déjà existante, donc on la supprime explicitement ici.
            //
            // MySQL s'appuie sur cet index composite pour satisfaire la FK
            // fk_scene_story (son premier champ, story_id, en a besoin) : il
            // refuse de le supprimer (erreur 1553) tant qu'aucun autre index
            // ne couvre story_id. On crée donc d'abord un index simple sur
            // story_id, qui devient le nouveau support de la FK, avant de
            // pouvoir retirer l'index composite.
            try {
                // on essaie de créer un index simple sur story_id
                statement.executeUpdate("CREATE INDEX idx_scenes_story_id ON scenes(story_id)");
            } catch (SQLException ignoredIfIndexAlreadyPresent) {
                // L'index existe déjà : rien à faire.
            }
            try {
                // on essaie de supprimer l'ancien index composite devenu inutile
                statement.executeUpdate("ALTER TABLE scenes DROP INDEX uq_scene_story_position");
            } catch (SQLException ignoredIfIndexAlreadyAbsent) {
                // L'index n'existe pas (base déjà à jour, ou nouvellement créée) : rien à faire.
            }
        }
    }

    /**
     * Vide toutes les tables de l'application (histoires, personnages,
     * scènes, associations scène/personnage), sans supprimer les tables
     * elles-mêmes. Ordre enfant -> parent pour respecter les contraintes de
     * clé étrangère sans avoir à les désactiver.
     */
    public static void deleteAllData() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            // on vide les tables dans cet ordre précis : d'abord les tables "enfant" qui dépendent
            // des autres, puis les tables "parent", pour ne jamais casser une contrainte de clé étrangère
            statement.executeUpdate("DELETE FROM scene_characters"); // d'abord les liens scène-personnage
            statement.executeUpdate("DELETE FROM scenes"); // puis les scènes
            statement.executeUpdate("DELETE FROM characters"); // puis les personnages
            statement.executeUpdate("DELETE FROM story"); // enfin les histoires elles-mêmes
        }
    }

    /**
     * Lit db/schema.sql depuis les ressources embarquées et retourne les
     * instructions CREATE TABLE (les lignes CREATE DATABASE / USE sont
     * filtrées car gérées séparément par ensureDatabaseAndSchema, avec le nom
     * de base réellement configuré plutôt que celui codé dans le fichier).
     */
    private static String[] readTableStatements() throws SQLException {
        // on ouvre le fichier schema.sql qui est embarqué dans l'application
        try (InputStream in = Database.class.getResourceAsStream("/db/schema.sql")) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8); // on lit tout le contenu du fichier en texte
            return content.lines()
                    .filter(line -> !line.trim().toUpperCase().startsWith("CREATE DATABASE")) // on enlève les lignes qui créent une base (gérée à part)
                    .filter(line -> !line.trim().toUpperCase().startsWith("USE ")) // on enlève les lignes "USE" (gérées à part aussi)
                    .reduce("", (a, b) -> a + "\n" + b) // on recolle toutes les lignes restantes en un seul gros texte
                    .split(";"); // puis on découpe ce texte en plusieurs instructions, séparées par des points-virgules
        } catch (IOException e) {
            // si la lecture du fichier échoue, on transforme l'erreur en SQLException pour rester cohérent avec le reste de la classe
            throw new SQLException("Impossible de lire le schéma SQL embarqué.", e);
        }
    }
}
