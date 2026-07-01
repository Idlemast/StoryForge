# Étape 6 — Tests unitaires et tests d'intégration

## Objectif
Vérifier les règles métier et le câblage service/persistance par des tests
automatisés JUnit 5 (déjà présent en dépendance dans le `pom.xml` depuis
l'étape 1, jamais exploité jusqu'ici).

## Mise en place
Le `maven-surefire-plugin` n'était pas déclaré explicitement : la version
par défaut de Maven ne sait pas exécuter des tests JUnit 5 (Jupiter), elle
les ignore silencieusement (`Tests run: 0`). Ajout du plugin en version
3.2.5 dans le `pom.xml` pour que `mvn test` détecte et lance réellement les
classes de test.

## Tests unitaires (`src/test/.../model`)
Portent sur le modèle seul, sans aucune dépendance externe :
- `SceneTest` : rejet d'un titre/contenu vide, rejet d'un statut nul.
- `StoryTest` : rejet d'un titre/auteur vide, unicité du nom d'un personnage
  (insensible à la casse), suppression d'un personnage qui le retire aussi de
  ses scènes, refus d'ajouter à une scène un personnage hors casting,
  contiguïté des positions de scène après suppression et déplacement.

Ces règles sont exactement celles documentées dans les rapports des étapes
2 à 4 ; les tests servent de garde-fou si le modèle est retouché plus tard.

## Test d'intégration (`src/test/.../service/StoryServiceTest`)
Fait collaborer `StoryService` avec de vrais objets du modèle et trois
repositories en mémoire (`InMemoryStoryRepository`, etc.), implémentations
minimales des interfaces `dao.*Repository` qui se contentent d'attribuer un
id auto-incrémenté à l'insertion (pas de connexion MySQL). Ce test couvre
donc tout le câblage modèle -> service -> repository (interface, DIP) sans
dépendre d'une base réelle :
- `fullWorkflowPersistsAndLinksCorrectly` : création d'une histoire, d'un
  personnage et d'une scène, association des deux, vérification que le
  repository de scènes reçoit bien l'appel `addCharacter`/`removeCharacter`.
- `searchScenesFiltersByKeywordAndStatus`, `countScenesByStatusCounts...`,
  `countSceneAppearancesByCharacter...` : couvrent les méthodes de requête
  ajoutées à l'étape 5.

## Limites connues
Aucun test ne couvre les implémentations JDBC elles-mêmes
(`JdbcStoryRepository` etc.) : cela demanderait soit une vraie base MySQL de
test, soit une base embarquée compatible (H2), non mise en place ici par
souci de simplicité — le test d'intégration se limite à la frontière
service/repository via les interfaces.

## Exécution
```
mvnw test
```
Résultat : 11 tests, 0 échec.
