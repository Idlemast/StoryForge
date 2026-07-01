package com.storyforge.model;

/**
 * Statut d'avancement d'une scène, tel que défini dans l'énoncé du projet.
 * Le label associé à chaque valeur est celui affiché dans l'interface
 * (ComboBox), via toString().
 */
public enum SceneStatus {
    // chaque ligne ci-dessous crée une des 4 valeurs possibles du statut,
    // avec entre parenthèses le texte à afficher à l'utilisateur pour cette valeur
    BROUILLON("Brouillon"),
    EN_COURS("En cours"),
    PRET_A_PUBLIER("Prêt à publier"),
    PUBLIEE("Publiée");

    private final String label; // le texte à afficher à l'écran pour cette valeur

    SceneStatus(String label) {
        this.label = label; // on mémorise le texte reçu pour pouvoir le réutiliser dans toString()
    }

    /** Affiché tel quel dans les ComboBox/ListView JavaFX. */
    @Override
    public String toString() {
        return label; // renvoie le texte lisible plutôt que le nom technique (ex: "Brouillon" plutôt que "BROUILLON")
    }
}
