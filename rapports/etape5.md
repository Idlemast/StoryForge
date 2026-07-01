# Étape 5 — Recherche, filtrage et statistiques

## Objectif
Exploiter le modèle objet en mémoire (`Story` -> `Scene`/`Character`) sans
toucher à la persistance : recherche de scènes par mot-clé et/ou statut et/ou
personnage, directement dans la zone de gestion des scènes, plus des
statistiques sur l'histoire sélectionnée, mises à jour automatiquement.

## Choix d'architecture
Aucune nouvelle requête SQL : ces opérations portent sur les collections déjà
chargées en mémoire (`story.getScenes()` / `story.getCharacters()`), donc
elles vivent dans `StoryService` comme de simples méthodes de requête
(aucune `SQLException`, contrairement aux méthodes de persistance) plutôt que
dans un repository ou le modèle — conformément à l'énoncé ("les traitements
liés aux statistiques, à la recherche et au filtrage doivent être réalisés
dans la logique métier... et non directement dans l'interface graphique").

## Service
- `matchesSearch(scene, keyword, statusFilter, characterFilter)` : prédicat
  réutilisé à la fois par `searchScenes` et par le filtrage en direct de
  l'IHM (`FilteredList`). Le mot-clé ne porte que sur le titre et/ou le
  contenu de la scène (conformément à l'énoncé, pas sur le lieu), insensible
  à la casse, ignoré si vide/null. `statusFilter` et `characterFilter` sont
  ignorés si null. Les trois critères se combinent (ET logique).
- `searchScenes(story, keyword, statusFilter, characterFilter)` : renvoie la
  liste des scènes correspondantes (utilisé par les tests).
- `countTotalCharacters(story)` / `countTotalScenes(story)` : totaux exigés
  par l'énoncé ("le nombre total de personnages", "le nombre total de
  scènes").
- `countScenesByStatus(story)` : nombre de scènes par `SceneStatus`, les
  quatre statuts apparaissent toujours dans le résultat (même à 0).
- `countSceneAppearancesByCharacter(story)` : nombre de scènes dans
  lesquelles chaque personnage du casting apparaît (fréquence d'apparition,
  mentionnée dans les fonctionnalités supplémentaires de l'énoncé).

## Interface
La recherche/filtrage est intégrée à l'onglet « Scènes » lui-même (et non
dans un onglet séparé), conformément à l'énoncé : « la zone de gestion des
scènes de l'interface devra permettre la consultation de la liste des
scènes, le filtrage par statut, le filtrage par personnage, la recherche par
mot-clé, la réinitialisation des filtres ».
- Un champ mot-clé, un `ComboBox` de statut et un `ComboBox` de personnage
  (chacun avec une entrée vide = « tous »), surmontant `sceneListView` ;
  bouton « Rechercher » qui applique le filtre, bouton « Réinitialiser » qui
  vide les champs et le réaffiche tout.
- `sceneListView` est alimentée par un `FilteredList<Scene>` qui enveloppe
  `story.getScenes()` (recréé à chaque sélection d'histoire, car un
  `FilteredList` ne peut pas changer de liste source) ; son prédicat est
  recalculé par `applySceneFilter()` à partir des champs de recherche.
  Le glisser-déposer (étape 4) reste correct même filtré : l'index de
  cellule cible n'est plus utilisé directement, on retrouve la position
  réelle de la scène déposée via `Story.getScenes().indexOf(...)`.
- Un onglet « Statistiques » affiche les totaux, la répartition par statut et
  les apparitions par personnage dans une zone de texte non éditable,
  recalculée automatiquement (`refreshStatistics()`) après chaque opération
  qui modifie les personnages ou les scènes (ajout/modification/suppression
  d'un personnage ou d'une scène, changement d'histoire sélectionnée) —
  conformément à l'énoncé ("les statistiques doivent être mises à jour
  lorsque les données de l'histoire sont modifiées"), sans bouton manuel.

## Tests
Couvert par `StoryServiceTest` (étape 6) : filtrage par mot-clé (titre et
contenu, pas le lieu), par statut, par personnage, par combinaisons, et les
quatre méthodes de comptage (totaux, par statut, par personnage).
