# Étape 2 — Ajout de la persistance

## Objectif
Sauvegarder/recharger histoires et personnages dans MySQL, sans changer le
comportement métier de l'étape 1.

## Schéma de base
`src/main/resources/db/schema.sql` :
- `story(id, title, author, summary)`
- `characters(id, story_id, name, role, description)` avec FK `story_id → story.id
  ON DELETE CASCADE` (la suppression en cascade des personnages est déléguée à
  MySQL plutôt que codée en Java) et contrainte unique `(story_id, name)` qui
  reflète en base la règle métier d'unicité déjà vérifiée côté Java.
- Table renommée `characters` (et colonne `` `role` `` entre backticks) car
  `character`/`role` sont des mots réservés selon la version de MySQL.

À exécuter manuellement : `mysql -u root -p < src/main/resources/db/schema.sql`.

## Code
- `db/Database.java` : `DriverManager.getConnection` avec URL/identifiants
  configurables (voir « Écran de connexion » ci-dessous).
- `dao/StoryDao.java`, `dao/CharacterDao.java` : CRUD JDBC minimal
  (`PreparedStatement`, try-with-resources, `RETURN_GENERATED_KEYS` pour
  récupérer l'id après `INSERT`). `StoryDao.findAll()` recharge aussi les
  personnages de chaque histoire via `CharacterDao.findByStoryId`.
- `model/Story` et `model/Character` ont désormais un champ `id` (null tant que
  l'objet n'est pas persisté) pour savoir si un DAO doit faire un `INSERT` ou
  un `UPDATE`.
- `MainController` appelle les DAO au lieu de ne manipuler que des listes en
  mémoire ; au démarrage il charge `storyDao.findAll()`. Si la base n'est pas
  disponible, l'erreur est affichée et l'application continue en mémoire
  seule (pas de crash au lancement).

## Choix techniques
- Suppression de `module-info.java` : le projet utilisait JPMS sans en avoir
  besoin, et ça aurait compliqué l'ajout du driver MySQL (modules
  automatiques). Classpath classique = moins de friction pour la suite du
  projet (étapes 3 et 4 ajoutent d'autres dépendances).
- `pom.xml` : ajout de `mysql-connector-j`, correction de
  `maven-compiler-plugin` (`source/target 8` → `release 17`, requis pour
  compiler avec JavaFX 21 et le JDK 17+ installé).

## Écran de connexion (ajout postérieur à l'étape 2)
Plutôt que des identifiants en dur, un onglet « Connexion MySQL » a été ajouté
à l'interface :
- Champs hôte / port / nom de base / utilisateur / mot de passe →
  `Database.configure(...)` met à jour les champs statiques utilisés par
  `getConnection()`.
- Bouton « Se connecter » → appelle `Database.ensureDatabaseAndSchema()` qui :
  1. se connecte au serveur (sans base précise) et exécute
     `CREATE DATABASE IF NOT EXISTS` avec le nom saisi ;
  2. se reconnecte à cette base et rejoue les `CREATE TABLE IF NOT EXISTS` lus
     directement depuis `db/schema.sql` (le fichier reste la source unique de
     vérité du schéma, pas de duplication du DDL en Java) ;
  3. recharge les histoires (`storyService.loadStories()`).
- Bouton « Démarrer le service MySQL » → exécute `net start MySQL80` dans un
  `cmd` élevé via `Start-Process -Verb RunAs` (déclenche l'invite UAC
  Windows si l'appli n'est pas déjà lancée en administrateur). La sortie de
  `net` est redirigée dans un fichier temporaire puis décodée en **CP850**
  (codepage OEM française utilisée par les outils console Windows, même
  avec `chcp 65001`) pour éviter les accents mal affichés.

## Limites connues
- Pas encore de séparation DAO/service/contrôleur stricte au moment de
  l'étape 2 (le contrôleur appelait directement les DAO) → traité à l'étape 3.
- Pas de pool de connexions (une connexion par opération) : suffisant pour ce
  projet, à revoir si l'appli devait gérer une charge réelle.
- Le nom du service Windows (`MySQL80`) est en dur dans le code : à adapter si
  l'installation locale utilise un autre nom de service.
