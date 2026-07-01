# Étape 4 — Ajout des scènes

## Objectif
Faire évoluer le modèle métier sans casser l'architecture en couches mise en
place à l'étape 3 : ajout d'une nouvelle entité `Scene`, d'une relation
One-To-Many (`Story 1 - * Scene`) et d'une relation Many-To-Many
(`Scene * - * Character`).

## Modèle
- `model/SceneStatus.java` : enum `BROUILLON`, `EN_COURS`, `PRET_A_PUBLIER`,
  `PUBLIEE`.
- `model/Scene.java` : titre, lieu, moment, contenu, position, statut, liste
  observable des personnages présents. Validation : titre/contenu non vides,
  statut non nul.
- `model/Story.java` (étendu) :
  - `scenes` (`ObservableList<Scene>`).
  - `addCharacterToScene` / `removeCharacterFromScene` : **règle métier
    clé de cette étape** — un personnage ne peut être associé à une scène que
    s'il appartient déjà au casting de l'histoire (`characters.contains(...)`).
  - `removeCharacter` retire aussi le personnage de toutes les scènes où il
    apparaissait, pour éviter une référence flottante côté objets Java (le
    schéma SQL fait de même via `ON DELETE CASCADE`).

### Révision : positions contiguës + glisser-déposer (ajout postérieur)
La première version validait l'unicité de la position et refusait toute
position déjà prise (erreur affichée à l'utilisateur). Sur demande, ce
comportement a été remplacé par une renumérotation automatique :
- La position d'une scène est désormais simplement son **rang dans la liste
  `scenes`** de l'histoire (1..N, jamais de trou ni de doublon possible par
  construction).
- `Story.addScene` (ajoute en fin), `Story.removeScene` (retire) et la
  nouvelle `Story.moveScene(scene, newIndex)` (déplace vers un nouvel index)
  appellent tous `renumberScenes()` ensuite, qui réécrit `position = index + 1`
  pour chaque scène. Plus aucune `IllegalArgumentException` possible sur la
  position.
- `Scene` n'a donc plus de paramètre `position` dans son constructeur ; sa
  position est affectée directement via `setPositionInternal` (publique,
  sans validation), aussi bien par `Story.renumberScenes` (renumérotation
  après ajout/suppression/déplacement) que par `JdbcSceneRepository` au
  chargement (restauration de la position lue en base).
- Côté UI, le glisser-déposer dans `sceneListView` (voir
  `MainController.setUpSceneDragAndDrop`) transporte l'index source dans le
  `Dragboard` puis appelle `StoryService.moveScene` au dépôt — aucune saisie
  manuelle de position n'est plus nécessaire (le `Spinner` a été retiré du
  formulaire).
- Côté base, la contrainte `UNIQUE(story_id, position)` a été retirée du
  schéma : un réordonnancement persiste les nouvelles positions scène par
  scène (`StoryService.persistScenePositions`), et une contrainte stricte
  aurait pu être violée transitoirement entre deux `UPDATE` (ex: échanger les
  positions 1 et 2 fait temporairement exister deux fois la position 2).

#### Migration automatique des bases existantes
`CREATE TABLE IF NOT EXISTS` ne modifie pas une table déjà existante : les
bases créées avant ce changement gardent l'ancienne contrainte. Deux essais
en conditions réelles ont révélé deux problèmes successifs, maintenant gérés
automatiquement par `Database.ensureDatabaseAndSchema()` (appelée à chaque
clic sur "Se connecter") :
1. `ALTER TABLE scenes DROP INDEX uq_scene_story_position` échouait avec
   l'erreur MySQL 1553 (*"needed in a foreign key constraint"*) : MySQL
   utilisait cet index composite comme support de la FK `fk_scene_story`
   (son premier champ, `story_id`, en a besoin). Il faut d'abord créer un
   index simple sur `story_id` (`CREATE INDEX idx_scenes_story_id`) pour que
   MySQL ait un autre support disponible, *avant* de pouvoir supprimer
   l'index composite.
2. Les deux opérations sont chacune entourées d'un `try/catch` qui ignore
   l'erreur si l'index cible existe déjà / n'existe plus : la migration est
   idempotente, sans condition explicite à vérifier au préalable.

#### Bug de rafraîchissement de l'affichage (corrigé)
Après un déplacement (glisser-déposer) ou une suppression de scène, les
numéros affichés dans `sceneListView` (ex: "3. Titre") ne se mettaient pas à
jour pour les scènes dont seule la position avait changé sans que leur
position *dans la liste JavaFX* ne bouge. En cause : `ListView` ne redessine
une cellule que lorsque l'`ObservableList` envoie un événement de
changement structurel (ajout/suppression/déplacement d'élément) — pas
lorsqu'un champ interne d'un objet déjà présent (ici `Scene.position`, muté
par `Story.renumberScenes`) change silencieusement. Corrigé en appelant
explicitement `sceneListView.refresh()` après `moveScene` (dans le handler
`onDragDropped`) et après `onDeleteScene`, les deux seuls cas où la position
d'**autres** scènes que celle manipulée peut changer.

## Persistance
- Nouvelles tables `scenes` et `scene_characters` (table de jonction pour le
  Many-To-Many, clé composite `(scene_id, character_id)`, deux FK en cascade).
  Voir la révision « positions contiguës » ci-dessus pour la contrainte de
  position, retirée après la première version.
- `dao/SceneRepository` (interface) + `dao/jdbc/JdbcSceneRepository` :
  CRUD sur `scenes`, plus `addCharacter`/`removeCharacter` qui
  insèrent/suppriment une ligne dans `scene_characters`
  (`INSERT IGNORE` pour rester idempotent).
- `JdbcStoryRepository.findAll()` charge maintenant, pour chaque histoire :
  personnages → map `id → Character`, puis scènes (en résolvant les
  personnages présents à partir de cette map, pour réutiliser les mêmes
  instances Java que celles de `story.getCharacters()`).

## Service
- `StoryService` gagne `addScene`, `updateScene`, `deleteScene`, `moveScene`,
  `addCharacterToScene`, `removeCharacterFromScene` : même pattern que pour
  les personnages (appel au modèle pour la règle métier, puis au repository
  pour la persistance). `deleteScene` et `moveScene` rappellent en plus
  `persistScenePositions` pour répercuter en base le décalage des positions
  des autres scènes de l'histoire.

## Interface
- Le `SplitPane` unique de l'étape 1 est passé dans un `TabPane` : onglet
  « Histoires et personnages » (inchangé) + nouvel onglet « Scènes ».
- Onglet Scènes : liste des scènes de l'histoire sélectionnée (glisser-déposer
  pour réordonner), formulaire (titre, lieu, moment, statut via `ComboBox`,
  contenu — plus de saisie manuelle de position), et une `ListView` à
  sélection multiple listant les personnages de l'histoire — la sélection
  représente les personnages présents dans la scène. À l'enregistrement, on
  calcule la différence entre la sélection actuelle et
  `scene.getPresentCharacters()` pour appeler
  `addCharacterToScene`/`removeCharacterFromScene` uniquement sur ce qui a
  changé.

## Vérification de l'architecture en couches (étape 3)
Ajouter cette fonctionnalité n'a nécessité aucune modification de
`JdbcCharacterRepository`, `JdbcStoryRepository` (hors injection du nouveau
repository) ni des règles déjà en place sur `Character`/`Story` : la
séparation modèle / repository / service / contrôleur a permis d'ajouter
`Scene` comme un bloc supplémentaire plutôt que de retoucher l'existant.

## Limites connues
- Un déplacement (`moveScene`) réécrit en base la position de **toutes** les
  scènes de l'histoire (une requête `UPDATE` par scène), pas seulement celles
  effectivement décalées : suffisant pour le volume de scènes attendu dans ce
  projet, à optimiser (calcul de la plage réellement affectée) si les
  histoires devenaient très longues.
