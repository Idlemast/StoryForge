package com.storyforge.model;

/**
 * Représente un personnage de l'histoire.
 * Un personnage n'existe pas seul : il appartient toujours à un Story
 * (c'est Story qui garantit l'unicité de son nom au sein de l'histoire, voir
 * Story.addCharacter / Story.renameCharacter).
 */
public class Character {

    // Null tant que le personnage n'a pas encore été sauvegardé en base
    // (permet aux DAO de savoir s'il faut faire un INSERT ou un UPDATE).
    private Long id; // l'identifiant en base de données ; vaut null si le personnage n'a jamais été enregistré
    private String name; // le nom du personnage, par exemple "Arthur"
    private String role; // le rôle du personnage dans l'histoire, par exemple "Roi"
    private String description; // un texte libre qui décrit le personnage (peut être vide)

    public Character(String name, String role, String description) {
        // Les setters appliquent déjà la validation, on les réutilise ici
        // pour ne pas dupliquer les règles entre constructeur et mutateurs.
        setName(name); // vérifie et enregistre le nom
        setRole(role); // vérifie et enregistre le rôle
        setDescription(description); // enregistre la description (pas de vérification stricte)
    }

    public Long getId() {
        return id; // renvoie simplement l'identifiant stocké
    }

    public void setId(Long id) {
        this.id = id; // remplace l'identifiant stocké par la nouvelle valeur reçue
    }

    public String getName() {
        return name; // renvoie simplement le nom stocké
    }

    /** Le nom est obligatoire (règle métier de l'énoncé). */
    public void setName(String name) {
        if (name == null || name.isBlank()) {
            // si le nom est absent ou ne contient que des espaces, on refuse et on explique pourquoi
            throw new IllegalArgumentException("Le nom du personnage ne peut pas être vide.");
        }
        this.name = name.trim(); // on enlève les espaces inutiles au début/à la fin avant de stocker
    }

    public String getRole() {
        return role; // renvoie simplement le rôle stocké
    }

    /** Le rôle est obligatoire (règle métier de l'énoncé). */
    public void setRole(String role) {
        if (role == null || role.isBlank()) {
            // même règle que pour le nom : un rôle vide n'est pas autorisé
            throw new IllegalArgumentException("Le rôle du personnage ne peut pas être vide.");
        }
        this.role = role.trim(); // on enlève les espaces inutiles avant de stocker
    }

    public String getDescription() {
        return description; // renvoie simplement la description stockée
    }

    /** La description est optionnelle, contrairement au nom et au rôle. */
    public void setDescription(String description) {
        // si aucune description n'est donnée (null), on stocke une chaîne vide plutôt que null,
        // pour ne jamais avoir à vérifier "est-ce que c'est null ?" ailleurs dans le code
        this.description = description == null ? "" : description.trim();
    }

    /** Utilisé par les ListView JavaFX pour l'affichage textuel par défaut. */
    @Override
    public String toString() {
        // ce texte est ce que l'utilisateur voit dans les listes de l'interface, par exemple "Arthur (Roi)"
        return name + " (" + role + ")";
    }
}
