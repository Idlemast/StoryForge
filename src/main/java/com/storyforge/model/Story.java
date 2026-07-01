package com.storyforge.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Représente une histoire : son entête (titre, auteur, résumé) ainsi que les
 * deux collections qu'elle possède en propre, ses personnages et ses scènes.
 *
 * Story est l'agrégat racine du modèle : c'est elle qui fait respecter les
 * invariants qui dépendent de plusieurs personnages/scènes à la fois
 * (unicité du nom d'un personnage, position contiguë des scènes,
 * appartenance d'un personnage présent dans une scène au casting de
 * l'histoire). Ces règles sont volontairement ici plutôt que dans Character
 * ou Scene, qui ne connaissent pas les autres éléments de la collection.
 *
 * Les positions des scènes sont toujours 1..N sans trou : la position d'une
 * scène est simplement son rang dans la liste `scenes`. Réordonner (drag and
 * drop, suppression) ne fait donc jamais échouer de validation : on déplace
 * l'élément dans la liste puis on renumérote tout le monde.
 */
public class Story {

    // Null tant que l'histoire n'a pas encore été sauvegardée en base.
    private Long id; // identifiant en base de données ; null si jamais enregistrée
    private String title; // le titre de l'histoire
    private String author; // l'auteur de l'histoire
    private String summary; // le résumé de l'histoire (peut être vide)
    private final ObservableList<Character> characters = FXCollections.observableArrayList(); // la liste des personnages de cette histoire
    private final ObservableList<Scene> scenes = FXCollections.observableArrayList(); // la liste des scènes de cette histoire, dans l'ordre

    public Story(String title, String author, String summary) {
        setTitle(title); // vérifie et enregistre le titre
        setAuthor(author); // vérifie et enregistre l'auteur
        setSummary(summary); // enregistre le résumé (pas de vérification stricte)
    }

    public Long getId() {
        return id; // renvoie simplement l'identifiant stocké
    }

    public void setId(Long id) {
        this.id = id; // remplace l'identifiant stocké par la nouvelle valeur reçue
    }

    public String getTitle() {
        return title; // renvoie simplement le titre stocké
    }

    /** Le titre est obligatoire (règle métier de l'énoncé). */
    public void setTitle(String title) {
        if (title == null || title.isBlank()) {
            // si le titre est absent ou ne contient que des espaces, on refuse
            throw new IllegalArgumentException("Le titre de l'histoire ne peut pas être vide.");
        }
        this.title = title.trim(); // on enlève les espaces inutiles avant de stocker
    }

    public String getAuthor() {
        return author; // renvoie simplement l'auteur stocké
    }

    /** L'auteur est obligatoire (règle métier de l'énoncé). */
    public void setAuthor(String author) {
        if (author == null || author.isBlank()) {
            // si l'auteur est absent ou ne contient que des espaces, on refuse
            throw new IllegalArgumentException("L'auteur de l'histoire ne peut pas être vide.");
        }
        this.author = author.trim(); // on enlève les espaces inutiles avant de stocker
    }

    public String getSummary() {
        return summary; // renvoie simplement le résumé stocké
    }

    /** Le résumé est optionnel, contrairement au titre et à l'auteur. */
    public void setSummary(String summary) {
        // si aucun résumé n'est donné (null), on stocke une chaîne vide plutôt que null
        this.summary = summary == null ? "" : summary.trim();
    }

    public ObservableList<Character> getCharacters() {
        return characters; // renvoie la liste des personnages de l'histoire
    }

    /**
     * Ajoute un personnage à l'histoire, en refusant les doublons de nom
     * (insensible à la casse) entre personnages d'une même histoire.
     */
    public void addCharacter(Character character) {
        // on regarde si un personnage existe déjà avec exactement le même nom
        // (en ignorant majuscules/minuscules) parmi ceux déjà présents
        boolean duplicate = characters.stream()
                .anyMatch(c -> c.getName().equalsIgnoreCase(character.getName()));
        if (duplicate) {
            // si oui, on refuse l'ajout et on explique pourquoi
            throw new IllegalArgumentException("Un personnage nommé \"" + character.getName() + "\" existe déjà dans cette histoire.");
        }
        characters.add(character); // sinon, on ajoute le nouveau personnage à la liste
    }

    /**
     * Retire un personnage de l'histoire. On le retire aussi de toutes les
     * scènes où il apparaissait, pour ne pas laisser de référence flottante
     * côté objets Java (le schéma SQL fait la même chose via ON DELETE CASCADE).
     */
    public void removeCharacter(Character character) {
        for (Scene scene : scenes) {
            // pour chaque scène de l'histoire, on retire ce personnage de la liste des présents
            // (s'il n'y était pas, remove() ne fait rien, donc pas de vérification nécessaire avant)
            scene.getPresentCharacters().remove(character);
        }
        characters.remove(character); // enfin, on retire le personnage de la liste principale de l'histoire
    }

    /**
     * Vérifie qu'un nouveau nom (lors d'une modification) n'entre pas en conflit avec un autre personnage.
     */
    public void renameCharacter(Character character, String newName) {
        // on cherche un AUTRE personnage (différent de celui qu'on renomme) qui porterait déjà ce nouveau nom
        boolean duplicate = characters.stream()
                .filter(c -> c != character)
                .anyMatch(c -> c.getName().equalsIgnoreCase(newName));
        if (duplicate) {
            // si un tel personnage existe, on refuse le renommage
            throw new IllegalArgumentException("Un personnage nommé \"" + newName + "\" existe déjà dans cette histoire.");
        }
        character.setName(newName); // sinon, on applique le nouveau nom
    }

    public ObservableList<Scene> getScenes() {
        return scenes; // renvoie la liste des scènes de l'histoire, dans l'ordre
    }

    /** Ajoute une scène à la fin de l'histoire, puis renumérote (elle obtient la dernière position). */
    public void addScene(Scene scene) {
        scenes.add(scene); // on ajoute la scène à la fin de la liste
        renumberScenes(); // puis on recalcule la position de toutes les scènes (1, 2, 3...)
    }

    /** Retire une scène et comble le trou laissé dans la numérotation. */
    public void removeScene(Scene scene) {
        scenes.remove(scene); // on retire la scène de la liste
        renumberScenes(); // puis on recalcule la position de toutes les scènes restantes, sans trou
    }

    /**
     * Déplace une scène vers un nouvel index dans l'ordre de l'histoire
     * (ex: glisser-déposer dans la liste), puis renumérote toutes les scènes
     * en conséquence. newIndex est un index de liste (0-based), pas une
     * position (1-based).
     */
    public void moveScene(Scene scene, int newIndex) {
        // on s'assure que l'index demandé reste dans les limites de la liste (jamais négatif, jamais trop grand)
        int clampedIndex = Math.max(0, Math.min(newIndex, scenes.size() - 1));
        scenes.remove(scene); // on retire d'abord la scène de sa position actuelle
        scenes.add(clampedIndex, scene); // puis on la réinsère à la nouvelle position souhaitée
        renumberScenes(); // enfin, on recalcule la position de toutes les scènes en fonction du nouvel ordre
    }

    /** Réaligne les positions (1..N) sur l'ordre actuel de la liste, sans trou ni doublon possible. */
    private void renumberScenes() {
        for (int i = 0; i < scenes.size(); i++) {
            // la position de chaque scène devient simplement son rang dans la liste (en commençant à 1, pas à 0)
            scenes.get(i).setPositionInternal(i + 1);
        }
    }

    /**
     * Associe un personnage à une scène : le personnage doit obligatoirement
     * faire partie du casting de l'histoire.
     */
    public void addCharacterToScene(Scene scene, Character character) {
        if (!characters.contains(character)) {
            // un personnage qui n'appartient pas à l'histoire ne peut pas être ajouté à une de ses scènes
            throw new IllegalArgumentException("\"" + character.getName() + "\" ne fait pas partie des personnages de cette histoire.");
        }
        if (!scene.getPresentCharacters().contains(character)) {
            // on n'ajoute le personnage que s'il n'est pas déjà présent dans la scène (pour éviter les doublons)
            scene.getPresentCharacters().add(character);
        }
    }

    public void removeCharacterFromScene(Scene scene, Character character) {
        // on retire simplement ce personnage de la liste des présents de cette scène
        scene.getPresentCharacters().remove(character);
    }

    /** Utilisé par les ListView JavaFX pour l'affichage textuel par défaut. */
    @Override
    public String toString() {
        return title; // ce texte est ce que l'utilisateur voit dans la liste des histoires
    }
}
