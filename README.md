# StoryForge

Application desktop JavaFX permettant de concevoir et organiser une histoire
de manière structurée : histoires, personnages, scènes, recherche/filtrage et
statistiques, avec persistance MySQL.

> **Schéma SQL** : [`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql), exécuté automatiquement au démarrage.

## Sommaire

- [Fonctionnalités](#fonctionnalités)
- [Architecture en couches](#architecture-en-couches)
- [Modèle métier et relations entre entités](#modèle-métier-et-relations-entre-entités)
- [Schéma de la base de données](#schéma-de-la-base-de-données)
- [Parcours d'une action utilisateur (exemple)](#parcours-dune-action-utilisateur-exemple)
- [Recherche, filtrage et statistiques](#recherche-filtrage-et-statistiques)
- [Organisation des fichiers](#organisation-des-fichiers)
- [Lancer le projet](#lancer-le-projet)
- [Lancer les tests](#lancer-les-tests)

## Fonctionnalités

L'application est organisée en quatre onglets.

### 1. Connexion MySQL

- Paramétrer l'hôte, le port, le nom de la base, l'utilisateur et le mot de
  passe MySQL.
- Se connecter : crée la base et ses tables si elles n'existent pas encore
  (`Database.ensureDatabaseAndSchema`), puis recharge les histoires.
- Démarrer le service Windows MySQL (`net start`) si le serveur n'est pas
  lancé.
- Tout supprimer : vide toutes les tables (avec confirmation, action
  irréversible).

### 2. Histoires et personnages

- Créer / modifier / supprimer une **histoire** (titre obligatoire, auteur
  obligatoire, résumé optionnel).
- Charger un jeu de données de démonstration en un clic (deux scénarios
  disponibles : « Légendes arthuriennes » et « Le Secret de la
  Bibliothèque », ce dernier étant le scénario imposé par l'énoncé du
  projet).
- Créer / modifier / supprimer un **personnage** au sein de l'histoire
  sélectionnée (nom obligatoire et unique dans l'histoire, rôle obligatoire,
  description optionnelle).

### 3. Scènes

- Créer / modifier / supprimer une **scène** (titre et contenu obligatoires,
  lieu et moment optionnels, statut obligatoire parmi _Brouillon_, _En
  cours_, _Prêt à publier_, _Publiée_).
- Réordonner les scènes par glisser-déposer ; la position de chaque scène
  est toujours recalculée automatiquement (1..N sans trou ni doublon).
- Cocher, dans une liste à sélection multiple, les personnages présents dans
  la scène (limités au casting de l'histoire).
- Rechercher/filtrer les scènes par mot-clé (titre et/ou contenu), par
  statut et/ou par personnage présent, directement sur la liste affichée ;
  bouton « Réinitialiser » pour tout effacer.

### 4. Statistiques

- Nombre total de personnages et de scènes de l'histoire sélectionnée.
- Nombre de scènes par statut.
- Nombre d'apparitions de chaque personnage dans les scènes.
- Recalculées automatiquement après chaque création/modification/suppression
  de personnage ou de scène.

## Architecture en couches

L'application suit une architecture en 4 couches, chacune ne connaissant que
la couche immédiatement inférieure (et seulement via des **interfaces** pour
la persistance, principe d'inversion de dépendances). Aucune couche ne
contient à la fois de l'UI, de la règle métier et du SQL.

```
┌──────────────────────────────────────────────────────────────────────┐
│  COUCHE PRÉSENTATION (JavaFX)                                        │
│                                                                        │
│   main-view.fxml  ──décrit l'écran──▶  MainController                │
│                                         (lit/écrit les champs,        │
│                                          appelle StoryService,        │
│                                          aucune règle métier, aucun   │
│                                          SQL)                         │
└───────────────────────────────┬────────────────────────────────────────┘
                                 │ appelle
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  COUCHE SERVICE (règles métier + orchestration)                      │
│                                                                        │
│   StoryService                                                       │
│     - applique d'abord la règle métier (via le modèle)               │
│     - répercute ensuite en base (via les repositories)               │
└───────────────────────────────┬────────────────────────────────────────┘
                                 │ dépend des INTERFACES, pas de JDBC
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  COUCHE PERSISTANCE (interfaces + implémentation JDBC)                │
│                                                                        │
│   StoryRepository    ◄────implémente──── JdbcStoryRepository         │
│   CharacterRepository◄────implémente──── JdbcCharacterRepository     │
│   SceneRepository    ◄────implémente──── JdbcSceneRepository         │
│                                         │                              │
│                                         ▼                              │
│                                   Database (connexions JDBC)          │
└───────────────────────────────┬────────────────────────────────────────┘
                                 │ SQL via le pilote MySQL
                                 ▼
┌──────────────────────────────────────────────────────────────────────┐
│  BASE DE DONNÉES MySQL  (tables story / characters / scenes /         │
│                           scene_characters)                           │
└──────────────────────────────────────────────────────────────────────┘

         ▲
         │  MainController et StoryService ne manipulent que des objets
         │  du MODÈLE (Story, Character, Scene, SceneStatus), qui
         │  contiennent eux-mêmes les règles de validation (titre non
         │  vide, unicité du nom, etc.) — voir section suivante.
```

Pourquoi cette séparation (principes SOLID appliqués) :

- **Single Responsibility** : chaque classe a un seul rôle (le contrôleur
  pilote l'IHM, le service orchestre, les repositories parlent SQL, le
  modèle valide ses propres règles).
- **Dependency Inversion** : `StoryService` dépend des interfaces
  `StoryRepository` / `CharacterRepository` / `SceneRepository`, jamais des
  classes `Jdbc*Repository` concrètes — on pourrait substituer une
  implémentation en mémoire sans toucher au service (voir
  `StoryServiceTest`, qui fait exactement ça pour les tests).

## Modèle métier et relations entre entités

```
                 1                        *
   ┌─────────┐ ───────possède────────▶ ┌─────────┐
   │  Story  │                         │Character│
   └─────────┘                         └─────────┘
        │ 1                                  ▲
        │                                    │
        │ possède                            │ présent dans
        │                                    │ (doit appartenir
        ▼ *                                  │  au casting de Story)
   ┌─────────┐  *                    *  ┌────┴────┐
   │  Scene  │ ◀──────────────────────▶ │Character│
   └─────────┘   présente / présent     └─────────┘
        │
        │ possède (obligatoire)
        ▼
   ┌────────────┐
   │ SceneStatus│  (BROUILLON | EN_COURS | PRET_A_PUBLIER | PUBLIEE)
   └────────────┘
```

Détail des relations :

- **Story 1 → \* Character** : une histoire possède plusieurs personnages ;
  un personnage appartient à une seule histoire. Le nom d'un personnage doit
  être unique au sein de son histoire (`Story.addCharacter` / `Story.renameCharacter`).
- **Story 1 → \* Scene** : une histoire possède plusieurs scènes ; une scène
  appartient à une seule histoire. La position d'une scène est toujours son
  rang dans la liste (`Story.renumberScenes`), jamais un champ libre — donc
  jamais de trou ni de doublon possible.
- **Scene \* ↔ \* Character** : une scène peut contenir plusieurs
  personnages, un personnage peut apparaître dans plusieurs scènes. Règle
  métier clé : un personnage ne peut être ajouté à une scène que s'il
  appartient déjà au casting de l'histoire (`Story.addCharacterToScene`).

`Story` est l'**agrégat racine** du modèle : c'est elle qui fait respecter
les règles qui dépendent de plusieurs personnages/scènes à la fois (unicité
des noms, contiguïté des positions, appartenance au casting). `Character` et
`Scene` ne valident que leurs propres champs (titre non vide, etc.), car ils
ne connaissent pas les autres éléments de la collection.

## Schéma de la base de données

```
┌────────────────────┐
│ story              │
├────────────────────┤
│ id            PK   │
│ title               │
│ author              │
│ summary             │
└─────────┬──────────┘
          │ 1
          │ story_id (FK, ON DELETE CASCADE)
     ┌────┴─────────────────┐              ┌────┴─────────────────┐
     │ *                    │              │ *                    │
┌────▼───────────────┐ ┌────▼───────────────┐
│ characters         │ │ scenes              │
├────────────────────┤ ├─────────────────────┤
│ id            PK   │ │ id            PK    │
│ story_id      FK   │ │ story_id      FK    │
│ name                │ │ title                │
│ role                │ │ location             │
│ description         │ │ moment               │
│ UNIQUE(story_id,    │ │ content              │
│        name)        │ │ position             │
└────────┬───────────┘ │ status               │
         │ *           └──────────┬───────────┘
         │                        │ *
         │      scene_characters  │
         │   (table de jonction)  │
         └──────────┬─────────────┘
                     ▼
        ┌─────────────────────────┐
        │ scene_characters        │
        ├─────────────────────────┤
        │ scene_id      PK, FK    │
        │ character_id  PK, FK    │
        │ (clé composite)         │
        └─────────────────────────┘
```

Toutes les clés étrangères sont `ON DELETE CASCADE` : supprimer une histoire
supprime automatiquement ses personnages, ses scènes et les associations
scène/personnage (côté MySQL). Voir [`schema.sql`](src/main/resources/db/schema.sql).

## Parcours d'une action utilisateur (exemple)

Exemple : l'utilisateur clique sur **« Enregistrer le personnage »** pour
créer un nouveau personnage.

```
 Utilisateur
     │  clique "Enregistrer le personnage"
     ▼
 MainController.onSaveCharacter()
     │  lit characterNameField / characterRoleField / characterDescriptionField
     ▼
 StoryService.addCharacter(story, name, role, description)
     │
     ├─▶ new Character(name, role, description)
     │       │  Character.setName() vérifie : nom non vide ? sinon
     │       │  IllegalArgumentException, remontée jusqu'au contrôleur qui
     │       │  l'affiche dans characterErrorLabel (rouge)
     │
     ├─▶ story.addCharacter(character)
     │       │  Story vérifie : un autre personnage de l'histoire porte-t-il
     │       │  déjà ce nom (insensible à la casse) ? sinon
     │       │  IllegalArgumentException
     │       ▼  si OK : ajouté à story.getCharacters() (ObservableList)
     │
     └─▶ characterRepository.insert(character, story.getId())
             │  JdbcCharacterRepository ouvre une connexion JDBC,
             │  exécute INSERT INTO characters (...) VALUES (...),
             │  récupère l'id auto-incrémenté généré par MySQL
             ▼  character.setId(idGénéré)
     ◀── Character créé, avec son id, renvoyé au contrôleur

 MainController : sélectionne le nouveau personnage dans characterListView
                   (qui se redessine seule, car liée à l'ObservableList de
                   l'histoire), puis refreshStatistics() met à jour l'onglet
                   Statistiques (le nombre total de personnages a changé).
```

Ce même schéma (modèle valide → service orchestre → repository persiste) se
répète pour toutes les actions de création/modification/suppression
d'histoire, de personnage et de scène.

## Recherche, filtrage et statistiques

Contrairement à la persistance, la recherche/filtrage et les statistiques ne
font **aucun accès base** : elles travaillent uniquement sur les collections
déjà chargées en mémoire (`story.getScenes()` / `story.getCharacters()`),
dans `StoryService`, jamais dans le contrôleur (l'énoncé impose que cette
logique reste dans la couche métier).

```
 Champs de recherche (mot-clé, statut, personnage)
        │
        ▼
 MainController.applySceneFilter()
        │  construit un prédicat : scene -> storyService.matchesSearch(...)
        ▼
 FilteredList<Scene> filteredScenes
   (enveloppe story.getScenes() ; sceneListView affiche cette vue filtrée,
    jamais une copie — toute scène ajoutée/supprimée s'y reflète aussi)
        │
        ▼
 sceneListView (UI) ne montre que les scènes qui correspondent
```

`StoryService.matchesSearch(scene, keyword, statusFilter, characterFilter)`
combine en ET logique : mot-clé dans le titre et/ou le contenu (ignoré si
vide), statut exact (ignoré si null), personnage présent (ignoré si null).

Les statistiques (`countTotalCharacters`, `countTotalScenes`,
`countScenesByStatus`, `countSceneAppearancesByCharacter`) sont recalculées
par `MainController.refreshStatistics()` après chaque action qui modifie un
personnage ou une scène (pas besoin de bouton « Actualiser »).

## Organisation des fichiers

```
src/main/java/com/storyforge/storyforge/
├── Launcher.java              point d'entrée (main), lance StoryForgeApp
├── StoryForgeApp.java         construit la fenêtre JavaFX à partir du FXML
├── MainController.java        contrôleur unique de main-view.fxml (UI ↔ service)
├── model/
│   ├── Story.java             agrégat racine : histoire + règles inter-objets
│   ├── Character.java         personnage (validation de ses propres champs)
│   ├── Scene.java             scène (validation de ses propres champs)
│   └── SceneStatus.java       enum des 4 statuts possibles
├── service/
│   └── StoryService.java      règles métier + orchestration persistance
├── dao/
│   ├── StoryRepository.java       interface (DIP)
│   ├── CharacterRepository.java   interface (DIP)
│   ├── SceneRepository.java       interface (DIP)
│   └── jdbc/
│       ├── JdbcStoryRepository.java       implémentation MySQL
│       ├── JdbcCharacterRepository.java   implémentation MySQL
│       └── JdbcSceneRepository.java       implémentation MySQL
└── db/
    └── Database.java          connexions JDBC, création base/tables, reset

src/main/resources/
├── com/storyforge/storyforge/
│   ├── main-view.fxml          description de l'interface (4 onglets)
│   └── styles.css              thème visuel (neumorphisme)
└── db/schema.sql                script SQL de création des tables

src/test/java/com/storyforge/storyforge/
├── model/StoryTest.java        tests unitaires des règles métier de Story
├── model/SceneTest.java        tests unitaires des règles métier de Scene
└── service/StoryServiceTest.java  tests d'intégration (service + faux
                                    repositories en mémoire, sans MySQL)
```

## Lancer le projet

Prérequis : JDK 17+, un serveur MySQL accessible (paramétrable dans l'onglet
« Connexion MySQL », valeurs par défaut : `localhost:3306`, base
`storyforge`, utilisateur `root`).

Le schéma SQL (création des tables) est dans
[`src/main/resources/db/schema.sql`](src/main/resources/db/schema.sql) —
il est exécuté automatiquement au démarrage via `Database.ensureDatabaseAndSchema()`.

```
mvnw clean javafx:run
```

## Lancer les tests

```
mvnw test
```

Les tests ne nécessitent aucune base MySQL : `StoryServiceTest` utilise des
implémentations en mémoire des interfaces de repository (voir
`InMemoryStoryRepository` etc.), rendues possibles par l'inversion de
dépendances (DIP) entre `StoryService` et les interfaces `*Repository`.
