# Étape 1 — Première version JavaFX : histoire et personnages

## Objectif
Application JavaFX en mémoire permettant de créer une histoire et de gérer son
casting de personnages. Aucune persistance à ce stade.

## Modèle
- `model/Story.java` : titre, auteur, résumé, liste observable de `Character`.
  - Validation dans les setters (`IllegalArgumentException` si titre/auteur vides).
  - `addCharacter` / `renameCharacter` refusent les doublons de nom (insensible à la casse).
- `model/Character.java` : nom, rôle, description. Validation nom/rôle non vides.

## Interface
- `main-view.fxml` : un `SplitPane` vertical.
  - Partie haute : liste des histoires + formulaire (titre, auteur, résumé).
  - Partie basse : liste des personnages de l'histoire sélectionnée + formulaire (nom, rôle, description).
- `MainController.java` : gère la sélection courante (`currentStory`, `currentCharacter`),
  les boutons Nouveau/Enregistrer/Supprimer, et affiche les erreurs de validation
  dans un `Label` rouge sous chaque formulaire (pas de popup, erreurs visibles inline).

## Choix techniques
- `ObservableList<Character>` directement dans `Story` pour que la `ListView` se
  mette à jour automatiquement sans code de synchronisation supplémentaire.
- Pas de DAO/service à ce stade : le contrôleur manipule directement le modèle
  (sera scindé à l'étape 3 — couches).

## Contraintes métier respectées
- Titre/auteur d'histoire non vides, résumé optionnel.
- Nom/rôle de personnage non vides, description optionnelle.
- Unicité du nom de personnage dans une même histoire.
- Suppression d'une histoire ⇒ ses personnages disparaissent avec elle (ils sont
  contenus dans la liste de l'objet `Story`, donc supprimés de fait).
- Toute erreur de validation est affichée à l'écran (label rouge), l'objet n'est
  pas créé/modifié si l'exception est levée.

## Build/run
- Correction nécessaire du `pom.xml` existant : `module-info.java` exige
  `--release 9+`, alors que le projet était configuré en `source/target 8`.
  Remplacé par `<release>17</release>` (cohérent avec JavaFX 21).
- Vérifié avec `mvnw.cmd compile` puis `mvnw.cmd javafx:run` (JAVA_HOME pointé
  vers le JDK 25 installé localement) : compilation et lancement OK.

## Limites connues (volontaires à ce stade)
- Pas de persistance (données perdues à la fermeture) → étape 2.
- Contrôleur et modèle dans le même module sans séparation en couches → étape 3.
