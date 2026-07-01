package com.storyforge.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Représente une scène d'une histoire : un événement narratif situé à un
 * endroit et un moment donnés, avec un statut d'avancement et la liste des
 * personnages présents.
 *
 * Une scène appartient toujours à une Story (relation 1-*) ; c'est Story qui
 * gère sa position (toujours égale à son rang dans la liste des scènes de
 * l'histoire, voir Story.renumberScenes) et garantit que les personnages
 * présents font bien partie du casting de l'histoire (voir
 * Story.addScene / Story.addCharacterToScene). Scene ne valide donc que ses
 * propres champs, pas ces invariants inter-objets.
 */
public class Scene {

    // Null tant que la scène n'a pas encore été sauvegardée en base.
    private Long id; // identifiant en base de données ; null si jamais enregistrée
    private String title; // le titre de la scène, par exemple "La découverte du livre"
    private String location; // le lieu où se déroule la scène (peut être vide)
    private String moment; // le moment où se déroule la scène, par exemple "le matin" (peut être vide)
    private String content; // le texte qui raconte la scène
    private int position; // le rang de la scène dans l'histoire (1, 2, 3...)
    private SceneStatus status; // l'avancement de la scène (brouillon, en cours, etc.)
    // Relation Many-To-Many avec Character : un personnage peut apparaître
    // dans plusieurs scènes, une scène peut contenir plusieurs personnages.
    private final ObservableList<Character> presentCharacters = FXCollections.observableArrayList(); // la liste des personnages présents dans cette scène

    public Scene(String title, String location, String moment, String content, SceneStatus status) {
        setTitle(title); // vérifie et enregistre le titre
        setLocation(location); // enregistre le lieu (pas de vérification stricte)
        setMoment(moment); // enregistre le moment (pas de vérification stricte)
        setContent(content); // vérifie et enregistre le contenu
        // La position n'a pas de paramètre dans le constructeur ni de setter
        // public : elle est entièrement dérivée du rang de la scène dans la
        // liste de l'histoire (voir Story.renumberScenes), qui l'écrasera
        // dès l'ajout via Story.addScene.
        setStatus(status); // vérifie et enregistre le statut
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
            throw new IllegalArgumentException("Le titre de la scène ne peut pas être vide.");
        }
        this.title = title.trim(); // on enlève les espaces inutiles avant de stocker
    }

    public String getLocation() {
        return location; // renvoie simplement le lieu stocké
    }

    /** Le lieu est optionnel. */
    public void setLocation(String location) {
        // si aucun lieu n'est donné (null), on stocke une chaîne vide plutôt que null
        this.location = location == null ? "" : location.trim();
    }

    public String getMoment() {
        return moment; // renvoie simplement le moment stocké
    }

    /** Le moment (ex: "le matin", "trois jours plus tard") est optionnel. */
    public void setMoment(String moment) {
        // si aucun moment n'est donné (null), on stocke une chaîne vide plutôt que null
        this.moment = moment == null ? "" : moment.trim();
    }

    public String getContent() {
        return content; // renvoie simplement le contenu stocké
    }

    /** Le contenu (le texte de la scène) est obligatoire (règle métier de l'énoncé). */
    public void setContent(String content) {
        if (content == null || content.isBlank()) {
            // si le contenu est absent ou ne contient que des espaces, on refuse
            throw new IllegalArgumentException("Le contenu de la scène ne peut pas être vide.");
        }
        this.content = content.trim(); // on enlève les espaces inutiles avant de stocker
    }

    public int getPosition() {
        return position; // renvoie simplement la position stockée
    }

    /**
     * Affecte directement la position, sans validation : utilisée par
     * Story.renumberScenes (après ajout/suppression/déplacement) et par
     * JdbcSceneRepository.findByStoryId (restauration depuis la base). Ne
     * doit jamais être appelée ailleurs : la position n'est pas un champ
     * librement modifiable, c'est toujours le rang de la scène dans
     * l'histoire (voir Story.renumberScenes).
     */
    public void setPositionInternal(int position) {
        this.position = position; // remplace la position stockée par la nouvelle valeur reçue
    }

    public SceneStatus getStatus() {
        return status; // renvoie simplement le statut stocké
    }

    /** Le statut est obligatoire (Brouillon / En cours / Prêt à publier / Publiée). */
    public void setStatus(SceneStatus status) {
        if (status == null) {
            // une scène doit toujours avoir un statut, on refuse donc null
            throw new IllegalArgumentException("La scène doit posséder un statut.");
        }
        this.status = status; // enregistre le nouveau statut
    }

    public ObservableList<Character> getPresentCharacters() {
        return presentCharacters; // renvoie la liste des personnages présents dans cette scène
    }

    /** Utilisé par les ListView JavaFX pour l'affichage textuel par défaut. */
    @Override
    public String toString() {
        // ce texte est ce que l'utilisateur voit dans la liste des scènes, par exemple "1. La découverte du livre"
        return position + ". " + title;
    }
}
