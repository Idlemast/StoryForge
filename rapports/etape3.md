# Étape 3 — Restructuration de l'application

## Objectif
Améliorer l'architecture sans changer les fonctionnalités. Aucune nouvelle
règle métier introduite.

## Architecture en couches
```
MainController (UI)
      ↓
StoryService (orchestration métier + persistance)
      ↓ (interfaces)
StoryRepository / CharacterRepository
      ↓ (implémentations)
JdbcStoryRepository / JdbcCharacterRepository  →  Database (JDBC)
```
- `model/` : `Story`, `Character` — règles de validation et invariants (titre
  non vide, unicité des noms, etc.), indépendantes de toute persistance ou UI.
- `dao/` : interfaces `StoryRepository`/`CharacterRepository`.
- `dao/jdbc/` : implémentations JDBC de ces interfaces (anciennement
  `StoryDao`/`CharacterDao` de l'étape 2, renommées et déplacées).
- `service/StoryService.java` (nouveau) : seul point d'entrée utilisé par le
  contrôleur. Combine appel aux méthodes du modèle (validation) et appel au
  repository (persistance), pour que le contrôleur n'ait plus à faire les deux.
- `MainController` : ne connaît plus que `StoryService` ; plus aucune
  référence à JDBC ou aux DAO concrets.

## Principes SOLID appliqués

**1. Single Responsibility Principle**
Chaque classe a une seule raison de changer :
- `Story`/`Character` changent si une règle métier change.
- `JdbcStoryRepository`/`JdbcCharacterRepository` changent si le SQL ou le
  moteur de BD change.
- `StoryService` change si l'enchaînement des opérations métier change.
- `MainController` change si l'UI change.
Avant cette étape, `MainController` mélangeait UI, règles métier et appels
SQL directs (via les DAO) : une seule classe avait trois raisons de changer.

**2. Dependency Inversion Principle**
`StoryService` dépend des interfaces `StoryRepository`/`CharacterRepository`,
pas des classes JDBC concrètes. Le contrôleur instancie les implémentations
JDBC puis les injecte dans `StoryService` via son constructeur. Conséquence
concrète : on peut remplacer `JdbcStoryRepository` par une implémentation en
mémoire (utile pour des tests unitaires de `StoryService` sans base de
données réelle) sans toucher au service ni au contrôleur.

## Changements transverses (sans impact fonctionnel)
- Suppression de `module-info.java` (fait dès l'étape 2 pour permettre le
  driver MySQL) : le projet n'est plus modulaire (JPMS), ce qui simplifie
  aussi cette restructuration en couches (pas de directives `exports`/`opens`
  à maintenir par package).

## Limites connues
- `StoryService` reste assez générique (CRUD), il n'y a pas encore de
  fonctionnalité métier propre aux scènes → étape 4.
