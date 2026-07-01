package com.storyforge.service;

import com.storyforge.dao.CharacterRepository;
import com.storyforge.dao.SceneRepository;
import com.storyforge.dao.StoryRepository;
import com.storyforge.model.Character;
import com.storyforge.model.Scene;
import com.storyforge.model.SceneStatus;
import com.storyforge.model.Story;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestre les règles métier du modèle avec la persistance.
 * Ne dépend que des interfaces de repository (DIP) : la couche UI n'a jamais
 * à connaître JDBC. C'est le seul point d'entrée utilisé par MainController :
 * chaque méthode applique d'abord la règle métier (via le modèle), puis
 * répercute le changement en base (via le repository correspondant).
 */
public class StoryService {

    private final StoryRepository storyRepository; // permet de sauvegarder/charger les histoires
    private final CharacterRepository characterRepository; // permet de sauvegarder/charger les personnages
    private final SceneRepository sceneRepository; // permet de sauvegarder/charger les scènes

    public StoryService(StoryRepository storyRepository, CharacterRepository characterRepository, SceneRepository sceneRepository) {
        this.storyRepository = storyRepository; // on mémorise le repository d'histoires reçu
        this.characterRepository = characterRepository; // on mémorise le repository de personnages reçu
        this.sceneRepository = sceneRepository; // on mémorise le repository de scènes reçu
    }

    /** Charge toutes les histoires (avec personnages et scènes) depuis la base. */
    public List<Story> loadStories() throws SQLException {
        return storyRepository.findAll(); // on délègue directement au repository, qui sait comment lire la base
    }

    /** Crée une nouvelle histoire en mémoire puis la persiste immédiatement. */
    public Story createStory(String title, String author, String summary) throws SQLException {
        Story story = new Story(title, author, summary); // crée l'histoire en mémoire (les vérifications se font dans le constructeur)
        storyRepository.insert(story); // enregistre cette nouvelle histoire en base de données
        return story; // renvoie l'histoire créée, désormais avec un identifiant
    }

    /** Modifie une histoire existante (les setters valident les champs) puis répercute en base. */
    public void updateStory(Story story, String title, String author, String summary) throws SQLException {
        story.setTitle(title); // applique le nouveau titre (vérifié par le setter)
        story.setAuthor(author); // applique le nouvel auteur (vérifié par le setter)
        story.setSummary(summary); // applique le nouveau résumé
        storyRepository.update(story); // répercute ces changements dans la base de données
    }

    /** Supprime une histoire ; rien à faire en base si elle n'a jamais été persistée (id null). */
    public void deleteStory(Story story) throws SQLException {
        if (story.getId() != null) {
            // on ne supprime en base que si l'histoire y a réellement été enregistrée un jour
            storyRepository.delete(story.getId());
        }
    }

    /** Crée un personnage, l'ajoute à l'histoire (vérifie l'unicité du nom) puis le persiste. */
    public Character addCharacter(Story story, String name, String role, String description) throws SQLException {
        Character character = new Character(name, role, description); // crée le personnage en mémoire (vérifications dans le constructeur)
        story.addCharacter(character); // l'ajoute à l'histoire (vérifie qu'il n'y a pas déjà un personnage du même nom)
        if (story.getId() != null) {
            // on n'enregistre en base que si l'histoire elle-même est déjà enregistrée
            characterRepository.insert(character, story.getId());
        }
        return character; // renvoie le personnage créé
    }

    /**
     * Modifie un personnage existant. Le renommage passe par
     * Story.renameCharacter pour revérifier l'unicité du nom, contrairement
     * au rôle et à la description qui n'ont pas cette contrainte.
     */
    public void updateCharacter(Story story, Character character, String name, String role, String description) throws SQLException {
        if (!character.getName().equalsIgnoreCase(name)) {
            // le nom a changé : on passe par renameCharacter pour revérifier qu'aucun autre personnage ne porte déjà ce nom
            story.renameCharacter(character, name);
        }
        character.setRole(role); // applique le nouveau rôle
        character.setDescription(description); // applique la nouvelle description
        if (character.getId() != null) {
            // on ne répercute en base que si ce personnage y est déjà enregistré
            characterRepository.update(character);
        }
    }

    /** Supprime un personnage de l'histoire et de la base (retiré aussi de ses scènes, voir Story.removeCharacter). */
    public void deleteCharacter(Story story, Character character) throws SQLException {
        if (character.getId() != null) {
            // on ne supprime en base que si ce personnage y a réellement été enregistré un jour
            characterRepository.delete(character.getId());
        }
        story.removeCharacter(character); // on le retire aussi de l'histoire en mémoire (et de toutes ses scènes)
    }

    /** Crée une scène et l'ajoute à la fin de l'histoire (elle reçoit la dernière position), puis la persiste. */
    public Scene addScene(Story story, String title, String location, String moment, String content, SceneStatus status) throws SQLException {
        Scene scene = new Scene(title, location, moment, content, status); // crée la scène en mémoire (vérifications dans le constructeur)
        story.addScene(scene); // l'ajoute à la fin de l'histoire (elle reçoit automatiquement la dernière position)
        if (story.getId() != null) {
            // on n'enregistre en base que si l'histoire elle-même est déjà enregistrée
            sceneRepository.insert(scene, story.getId());
        }
        return scene; // renvoie la scène créée
    }

    /** Modifie le contenu d'une scène existante (titre, lieu, moment, contenu, statut). Ne touche pas à sa position. */
    public void updateScene(Story story, Scene scene, String title, String location, String moment, String content, SceneStatus status) throws SQLException {
        scene.setTitle(title); // applique le nouveau titre (vérifié par le setter)
        scene.setLocation(location); // applique le nouveau lieu
        scene.setMoment(moment); // applique le nouveau moment
        scene.setContent(content); // applique le nouveau contenu (vérifié par le setter)
        scene.setStatus(status); // applique le nouveau statut (vérifié par le setter)
        if (scene.getId() != null) {
            // on ne répercute en base que si cette scène y est déjà enregistrée
            sceneRepository.update(scene);
        }
    }

    /**
     * Déplace une scène à un nouvel index dans l'histoire (glisser-déposer) :
     * Story renumérote toutes les scènes en mémoire, puis on répercute en
     * base les nouvelles positions de toutes les scènes déjà persistées (le
     * déplacement peut décaler la position de chacune d'entre elles).
     */
    public void moveScene(Story story, Scene scene, int newIndex) throws SQLException {
        story.moveScene(scene, newIndex); // déplace la scène en mémoire et renumérote toutes les scènes de l'histoire
        persistScenePositions(story); // répercute en base la nouvelle position de chaque scène
    }

    /** Supprime une scène de l'histoire et de la base, puis répercute le décalage des positions restantes. */
    public void deleteScene(Story story, Scene scene) throws SQLException {
        if (scene.getId() != null) {
            // on ne supprime en base que si cette scène y a réellement été enregistrée un jour
            sceneRepository.delete(scene.getId());
        }
        story.removeScene(scene); // on la retire aussi de l'histoire en mémoire (et on renumérote les scènes restantes)
        persistScenePositions(story); // répercute en base les nouvelles positions des scènes restantes
    }

    private void persistScenePositions(Story story) throws SQLException {
        for (Scene scene : story.getScenes()) {
            if (scene.getId() != null) {
                // on ne met à jour en base que les scènes qui y sont déjà enregistrées
                sceneRepository.update(scene);
            }
        }
    }

    /** Associe un personnage à une scène (Story vérifie qu'il appartient bien au casting de l'histoire). */
    public void addCharacterToScene(Story story, Scene scene, Character character) throws SQLException {
        story.addCharacterToScene(scene, character); // vérifie que le personnage fait partie du casting, puis l'ajoute à la scène en mémoire
        if (scene.getId() != null && character.getId() != null) {
            // on ne répercute en base que si la scène ET le personnage sont déjà tous les deux enregistrés
            sceneRepository.addCharacter(scene.getId(), character.getId());
        }
    }

    /** Retire l'association entre un personnage et une scène. */
    public void removeCharacterFromScene(Story story, Scene scene, Character character) throws SQLException {
        story.removeCharacterFromScene(scene, character); // retire le personnage de la scène en mémoire
        if (scene.getId() != null && character.getId() != null) {
            // on ne répercute en base que si la scène ET le personnage sont déjà tous les deux enregistrés
            sceneRepository.removeCharacter(scene.getId(), character.getId());
        }
    }

    /**
     * Prédicat de recherche/filtrage purement en mémoire (aucun accès base) :
     * vrai si la scène contient le mot-clé dans son titre et/ou son contenu
     * (insensible à la casse, ignoré si vide/null, conformément à l'énoncé),
     * a le statut demandé (ignoré si null), et contient le personnage demandé
     * (ignoré si null). Utilisé à la fois par searchScenes et par l'IHM pour
     * filtrer en direct la liste des scènes (FilteredList).
     */
    public boolean matchesSearch(Scene scene, String keyword, SceneStatus statusFilter, Character characterFilter) {
        // on prépare le mot-clé recherché : en minuscules et sans espace inutile, pour comparer sans tenir compte de la casse
        String needle = keyword == null ? "" : keyword.trim().toLowerCase();
        // vrai si aucun mot-clé n'est demandé, OU si le titre ou le contenu de la scène le contient
        boolean matchesKeyword = needle.isEmpty()
                || scene.getTitle().toLowerCase().contains(needle)
                || scene.getContent().toLowerCase().contains(needle);
        // vrai si aucun statut n'est demandé, OU si le statut de la scène correspond exactement
        boolean matchesStatus = statusFilter == null || scene.getStatus() == statusFilter;
        // vrai si aucun personnage n'est demandé, OU si ce personnage est bien présent dans la scène
        boolean matchesCharacter = characterFilter == null || scene.getPresentCharacters().contains(characterFilter);
        // la scène correspond seulement si les trois conditions sont vraies en même temps
        return matchesKeyword && matchesStatus && matchesCharacter;
    }

    /** Recherche/filtrage : renvoie les scènes de l'histoire qui correspondent aux critères (voir matchesSearch). */
    public List<Scene> searchScenes(Story story, String keyword, SceneStatus statusFilter, Character characterFilter) {
        List<Scene> results = new ArrayList<>(); // la liste qu'on va remplir avec les scènes qui correspondent
        for (Scene scene : story.getScenes()) { // on regarde chaque scène de l'histoire, une par une
            if (matchesSearch(scene, keyword, statusFilter, characterFilter)) {
                // si cette scène correspond aux critères demandés, on l'ajoute au résultat
                results.add(scene);
            }
        }
        return results; // on renvoie la liste des scènes qui correspondent
    }

    /** Statistique : nombre total de personnages de l'histoire. */
    public int countTotalCharacters(Story story) {
        return story.getCharacters().size(); // il suffit de compter les éléments de la liste des personnages
    }

    /** Statistique : nombre total de scènes de l'histoire. */
    public int countTotalScenes(Story story) {
        return story.getScenes().size(); // il suffit de compter les éléments de la liste des scènes
    }

    /** Statistique : nombre de scènes par statut (toujours les 4 statuts, même à 0). */
    public Map<SceneStatus, Long> countScenesByStatus(Story story) {
        Map<SceneStatus, Long> counts = new LinkedHashMap<>(); // dictionnaire statut -> nombre de scènes ayant ce statut
        for (SceneStatus status : SceneStatus.values()) {
            // on initialise chaque statut possible à 0, pour qu'il apparaisse même s'il n'y a aucune scène avec ce statut
            counts.put(status, 0L);
        }
        for (Scene scene : story.getScenes()) {
            // pour chaque scène, on augmente de 1 le compteur correspondant à son statut
            counts.merge(scene.getStatus(), 1L, Long::sum);
        }
        return counts; // on renvoie le dictionnaire complet des comptages par statut
    }

    /** Statistique : nombre de scènes dans lesquelles chaque personnage apparaît. */
    public Map<Character, Long> countSceneAppearancesByCharacter(Story story) {
        Map<Character, Long> counts = new LinkedHashMap<>(); // dictionnaire personnage -> nombre de scènes où il apparaît
        for (Character character : story.getCharacters()) {
            // on initialise chaque personnage à 0, pour qu'il apparaisse même s'il n'est dans aucune scène
            counts.put(character, 0L);
        }
        for (Scene scene : story.getScenes()) { // on regarde chaque scène, une par une
            for (Character character : scene.getPresentCharacters()) {
                // pour chaque personnage présent dans cette scène, on augmente son compteur de 1
                counts.merge(character, 1L, Long::sum);
            }
        }
        return counts; // on renvoie le dictionnaire complet des comptages par personnage
    }
}
