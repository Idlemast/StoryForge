package com.storyforge;

import com.storyforge.dao.jdbc.JdbcCharacterRepository;
import com.storyforge.dao.jdbc.JdbcSceneRepository;
import com.storyforge.dao.jdbc.JdbcStoryRepository;
import com.storyforge.db.Database;
import com.storyforge.model.Character;
import com.storyforge.model.Scene;
import com.storyforge.model.SceneStatus;
import com.storyforge.model.Story;
import com.storyforge.service.StoryService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Contrôleur unique de main-view.fxml (les trois onglets : Connexion MySQL,
 * Histoires et personnages, Scènes). Ne contient aucune logique métier ni
 * SQL : il se limite à lire/écrire les champs de l'UI et à appeler
 * StoryService, qui se charge de la validation et de la persistance.
 *
 * Trois champs "courant" (currentStory/currentCharacter/currentScene)
 * suivent la sélection active dans les ListView : null signifie "formulaire
 * vide, prêt pour une création" ; non-null signifie "modification de
 * l'élément sélectionné".
 */
public class MainController implements Initializable {

    // --- Onglet "Connexion MySQL" ---
    @FXML private TextField dbHostField; // champ de saisie : adresse du serveur MySQL
    @FXML private TextField dbPortField; // champ de saisie : port du serveur MySQL
    @FXML private TextField dbNameField; // champ de saisie : nom de la base de données
    @FXML private TextField dbUserField; // champ de saisie : nom d'utilisateur MySQL
    @FXML private PasswordField dbPasswordField; // champ de saisie : mot de passe MySQL (masqué)
    @FXML private TextField dbServiceNameField; // champ de saisie : nom du service Windows MySQL
    @FXML private Label dbStatusLabel; // texte qui affiche le résultat de la dernière action (succès ou erreur)

    // --- Onglet "Histoires et personnages" : partie Histoires ---
    @FXML private ListView<Story> storyListView; // liste affichant toutes les histoires
    @FXML private TextField storyTitleField; // champ de saisie : titre de l'histoire
    @FXML private TextField storyAuthorField; // champ de saisie : auteur de l'histoire
    @FXML private TextArea storySummaryField; // champ de saisie : résumé de l'histoire
    @FXML private Label storyErrorLabel; // texte qui affiche une erreur liée à l'histoire

    // --- Onglet "Histoires et personnages" : partie Personnages ---
    @FXML private ListView<Character> characterListView; // liste affichant les personnages de l'histoire sélectionnée
    @FXML private TextField characterNameField; // champ de saisie : nom du personnage
    @FXML private TextField characterRoleField; // champ de saisie : rôle du personnage
    @FXML private TextArea characterDescriptionField; // champ de saisie : description du personnage
    @FXML private Label characterErrorLabel; // texte qui affiche une erreur liée au personnage

    // --- Onglet "Scènes" ---
    @FXML private TextField searchKeywordField; // champ de saisie : mot-clé recherché dans les scènes
    @FXML private ComboBox<SceneStatus> searchStatusComboBox; // liste déroulante : statut sur lequel filtrer
    @FXML private ComboBox<Character> searchCharacterComboBox; // liste déroulante : personnage sur lequel filtrer
    @FXML private ListView<Scene> sceneListView; // liste affichant les scènes de l'histoire sélectionnée
    @FXML private TextField sceneTitleField; // champ de saisie : titre de la scène
    @FXML private TextField sceneLocationField; // champ de saisie : lieu de la scène
    @FXML private TextField sceneMomentField; // champ de saisie : moment de la scène
    @FXML private ComboBox<SceneStatus> sceneStatusComboBox; // liste déroulante : statut de la scène
    @FXML private TextArea sceneContentField; // champ de saisie : contenu de la scène
    @FXML private ListView<Character> scenePresentCharactersListView; // liste à sélection multiple : personnages présents dans la scène
    @FXML private Label sceneErrorLabel; // texte qui affiche une erreur liée à la scène

    // --- Onglet "Statistiques" ---
    @FXML private TextArea statisticsArea; // zone de texte affichant les statistiques de l'histoire sélectionnée

    // Liste affichée dans storyListView ; toutes les histoires chargées en mémoire.
    private final ObservableList<Story> stories = FXCollections.observableArrayList(); // contient toutes les histoires actuellement connues de l'application
    // Repositories JDBC concrets, injectés dans StoryService via les interfaces (DIP).
    private final JdbcCharacterRepository characterRepository = new JdbcCharacterRepository(); // sait lire/écrire les personnages en base
    private final JdbcSceneRepository sceneRepository = new JdbcSceneRepository(); // sait lire/écrire les scènes en base
    private final StoryService storyService =
            // le service métier central : c'est lui qu'on appelle pour toute action sur les histoires/personnages/scènes
            new StoryService(new JdbcStoryRepository(characterRepository, sceneRepository), characterRepository, sceneRepository);
    // Élément actuellement sélectionné dans chaque ListView (null = mode création).
    private Story currentStory; // l'histoire actuellement sélectionnée (null si aucune)
    private Character currentCharacter; // le personnage actuellement sélectionné (null si aucun)
    private Scene currentScene; // la scène actuellement sélectionnée (null si aucune)
    // Vue filtrée de story.getScenes() affichée dans sceneListView (voir applySceneFilter) ;
    // recréée à chaque sélection d'histoire (FilteredList ne peut pas changer de liste source).
    private FilteredList<Scene> filteredScenes; // la liste de scènes réellement affichée, après application du filtre de recherche
    // Scène en cours de glisser-déposer (voir setUpSceneDragAndDrop) : on retrouve sa position
    // réelle via Story.getScenes().indexOf plutôt que via l'index de cellule, qui ne correspond
    // plus à la liste réelle dès qu'un filtre masque des scènes.
    private Scene draggedScene; // mémorise la scène que l'utilisateur est en train de faire glisser

    /** Appelée automatiquement par JavaFX juste après le chargement du FXML. */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        storyListView.setItems(stories); // on relie la liste affichée à l'écran à notre liste d'histoires en mémoire
        // Chaque sélection met à jour le formulaire correspondant (voir selectStory/selectCharacter/selectScene).
        // on écoute les changements de sélection dans chaque liste, pour mettre à jour le formulaire correspondant automatiquement
        storyListView.getSelectionModel().selectedItemProperty().addListener((obs, old, story) -> selectStory(story));
        characterListView.getSelectionModel().selectedItemProperty().addListener((obs, old, character) -> selectCharacter(character));
        sceneListView.getSelectionModel().selectedItemProperty().addListener((obs, old, scene) -> selectScene(scene));
        // Sélection multiple : la liste reflète les personnages présents dans la scène courante.
        scenePresentCharactersListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // autorise à cocher plusieurs personnages en même temps
        sceneStatusComboBox.setItems(FXCollections.observableArrayList(SceneStatus.values())); // remplit la liste déroulante de statut avec les 4 valeurs possibles
        // null = "tous les statuts" dans le filtre de recherche, contrairement au formulaire de scène.
        ObservableList<SceneStatus> statusFilterChoices = FXCollections.observableArrayList(SceneStatus.values()); // copie des 4 statuts
        statusFilterChoices.add(0, null); // on ajoute une option vide en première position, qui signifie "pas de filtre sur le statut"
        searchStatusComboBox.setItems(statusFilterChoices); // remplit la liste déroulante de filtre avec ces choix
        setUpSceneDragAndDrop(); // active le glisser-déposer pour réordonner les scènes
        loadStories(); // charge les histoires existantes depuis la base de données dès le démarrage
    }

    /** Reconstruit le prédicat de filtrage de sceneListView à partir des champs de recherche actuels. */
    private void applySceneFilter() {
        if (filteredScenes == null) {
            // s'il n'y a pas encore de liste filtrée (aucune histoire sélectionnée), il n'y a rien à filtrer
            return;
        }
        String keyword = searchKeywordField.getText(); // lit le mot-clé actuellement saisi
        SceneStatus status = searchStatusComboBox.getValue(); // lit le statut actuellement choisi (peut être null = tous)
        Character character = searchCharacterComboBox.getValue(); // lit le personnage actuellement choisi (peut être null = tous)
        // on demande au service métier de juger, pour chaque scène, si elle correspond à ces critères
        filteredScenes.setPredicate(scene -> storyService.matchesSearch(scene, keyword, status, character));
    }

    /**
     * Active le glisser-déposer dans sceneListView pour réordonner les
     * scènes : on transporte simplement l'index de la cellule source dans le
     * Dragboard (sous forme de texte), puis au dépôt on demande à
     * StoryService de déplacer la scène à l'index de la cellule cible.
     * Story.moveScene renumérote alors toutes les scènes en conséquence
     * (voir Story.renumberScenes) — pas de gestion manuelle des positions ici.
     */
    private void setUpSceneDragAndDrop() {
        // on définit comment chaque ligne (cellule) de la liste de scènes doit se comporter
        sceneListView.setCellFactory(listView -> {
            ListCell<Scene> cell = new ListCell<>() {
                @Override
                protected void updateItem(Scene scene, boolean empty) {
                    super.updateItem(scene, empty); // laisse JavaFX faire son travail habituel
                    setText(empty || scene == null ? null : scene.toString()); // affiche le texte de la scène, ou rien si la ligne est vide
                }
            };

            // déclenché quand l'utilisateur commence à faire glisser cette cellule
            cell.setOnDragDetected(event -> {
                if (cell.getItem() == null) {
                    // une cellule vide ne contient aucune scène : rien à glisser
                    return;
                }
                draggedScene = cell.getItem(); // on mémorise quelle scène est en train d'être déplacée
                Dragboard dragboard = cell.startDragAndDrop(TransferMode.MOVE); // démarre officiellement le glisser-déposer
                ClipboardContent content = new ClipboardContent();
                content.putString(draggedScene.getTitle()); // contenu transporté (juste pour activer le mécanisme, la vraie info est draggedScene)
                dragboard.setContent(content);
                event.consume(); // indique que l'événement a été traité
            });

            // déclenché quand la souris (en train de glisser quelque chose) survole cette cellule
            cell.setOnDragOver(event -> {
                if (event.getGestureSource() != cell && event.getDragboard().hasString()) {
                    // si ce n'est pas la cellule elle-même qu'on survole, on autorise le dépôt ici
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            // déclenché quand l'utilisateur relâche la souris sur cette cellule
            cell.setOnDragDropped(event -> {
                Dragboard dragboard = event.getDragboard();
                if (dragboard.hasString() && currentStory != null && draggedScene != null) {
                    // L'index de la cellule cible se rapporte à la liste filtrée affichée, pas à
                    // story.getScenes() : on retrouve donc la position réelle de la scène cible
                    // (ou la fin de liste si on dépose hors de toute cellule) via indexOf.
                    Scene targetScene = cell.isEmpty() ? null : cell.getItem(); // la scène sur laquelle on a déposé (ou rien si zone vide)
                    int targetIndex = targetScene == null
                            ? currentStory.getScenes().size() - 1 // déposé hors de toute scène : on va à la toute fin
                            : currentStory.getScenes().indexOf(targetScene); // sinon, on prend la position réelle de la scène cible
                    try {
                        storyService.moveScene(currentStory, draggedScene, targetIndex); // demande au service de déplacer la scène
                        // Le déplacement renumérote toutes les scènes (voir Story.renumberScenes) ;
                        // refresh() force le redessin des libellés "N. Titre" même pour les
                        // scènes dont la position a changé sans que leur index de liste change.
                        sceneListView.refresh(); // force le réaffichage de la liste pour montrer les nouveaux numéros
                        sceneListView.getSelectionModel().select(draggedScene); // garde la scène déplacée sélectionnée
                        sceneErrorLabel.setText(""); // efface tout message d'erreur précédent
                    } catch (SQLException e) {
                        sceneErrorLabel.setText("Erreur base de données : " + e.getMessage()); // affiche l'erreur si la base a refusé l'opération
                    }
                    event.setDropCompleted(true); // confirme que le dépôt a bien été traité
                    event.consume();
                }
            });

            return cell; // renvoie la cellule configurée
        });
    }

    /** (Re)charge la liste des histoires depuis la base. Si la base est indisponible, l'appli continue en mémoire. */
    private void loadStories() {
        try {
            stories.clear(); // on vide d'abord la liste actuelle
            stories.addAll(storyService.loadStories()); // puis on la remplit avec ce que la base contient réellement
            storyErrorLabel.setText(""); // tout s'est bien passé, on efface les erreurs précédentes
        } catch (SQLException e) {
            // si la base est inaccessible, on prévient l'utilisateur mais l'application continue de fonctionner en mémoire
            storyErrorLabel.setText("Base de données indisponible, mode mémoire seule (" + e.getMessage() + ").");
        }
    }

    /**
     * Bouton "Se connecter" : applique les paramètres saisis, crée la base et
     * ses tables si nécessaire, puis recharge les histoires.
     */
    @FXML
    private void onConnectDatabase() {
        try {
            int port = Integer.parseInt(dbPortField.getText().trim()); // convertit le texte du port en nombre
            Database.configure(dbHostField.getText().trim(), port, dbNameField.getText().trim(),
                    dbUserField.getText().trim(), dbPasswordField.getText()); // applique les paramètres de connexion saisis
            Database.ensureDatabaseAndSchema(); // crée la base et ses tables si elles n'existent pas encore
            dbStatusLabel.setText("Connecté à " + dbHostField.getText() + ":" + port + "/" + dbNameField.getText()); // confirme la connexion réussie
            loadStories(); // recharge les histoires maintenant que la connexion est établie
        } catch (NumberFormatException e) {
            // le texte saisi pour le port n'est pas un nombre valide
            dbStatusLabel.setText("Le port doit être un nombre.");
        } catch (SQLException e) {
            // la connexion à la base a échoué pour une autre raison
            dbStatusLabel.setText("Connexion échouée : " + e.getMessage());
        }
    }

    /**
     * Bouton "Démarrer le service MySQL" : exécute `net start <service>`.
     * Cette commande exigeant les droits administrateur, on la relance dans
     * un cmd élevé via PowerShell (déclenche l'invite UAC Windows), et on
     * récupère sa sortie via un fichier temporaire (la capture directe du
     * flux standard n'est pas possible avec un process lancé en élévation).
     */
    @FXML
    private void onStartMySqlService() {
        try {
            Path logFile = Files.createTempFile("storyforge_mysql", ".log"); // crée un fichier temporaire pour récupérer le résultat de la commande
            String serviceName = dbServiceNameField.getText().trim(); // nom du service Windows à démarrer
            String cmdArgs = "/c net start " + serviceName + " > \"" + logFile + "\" 2>&1"; // commande qui démarre le service et redirige sa sortie vers le fichier
            String psCommand = "Start-Process -FilePath cmd.exe -ArgumentList '" + cmdArgs + "' -Verb RunAs -Wait"; // demande à Windows de lancer cette commande avec les droits administrateur
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command", psCommand).start(); // exécute réellement cette commande
            process.waitFor(); // attend que la commande se termine avant de continuer
            // net.exe écrit dans la console avec le codepage OEM (CP850 en France),
            // pas en UTF-8, même avec chcp 65001 — on décode donc avec ce charset.
            // new String(bytes, charset) remplace les octets invalides au lieu de
            // lever une exception (contrairement à Files.readString).
            String output = new String(Files.readAllBytes(logFile), Charset.forName("Cp850")).trim(); // lit le résultat écrit dans le fichier temporaire
            Files.deleteIfExists(logFile); // supprime le fichier temporaire, on n'en a plus besoin
            dbStatusLabel.setText(output.isEmpty() ? "Commande exécutée." : output); // affiche le résultat à l'utilisateur
        } catch (IOException | InterruptedException e) {
            // si quelque chose a empêché de lancer ou lire le résultat de la commande
            dbStatusLabel.setText("Impossible de lancer le service : " + e.getMessage());
        }
    }

    /**
     * Bouton "Tout supprimer" : vide toutes les tables (histoires,
     * personnages, scènes, associations). Action destructive et
     * irréversible, donc protégée par une boîte de confirmation avant
     * d'exécuter quoi que ce soit.
     */
    @FXML
    private void onDropAllData() {
        // on prépare une boîte de dialogue qui demande confirmation avant l'action destructrice
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Cette action supprime définitivement toutes les histoires, personnages et scènes de la base. Continuer ?");
        confirmation.setHeaderText("Tout supprimer ?");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            // si l'utilisateur n'a pas cliqué sur "OK", on arrête tout ici, rien n'est supprimé
            return;
        }
        try {
            Database.deleteAllData(); // vide réellement toutes les tables de la base
            stories.clear(); // vide aussi la liste des histoires affichée à l'écran
            selectStory(null); // remet tous les formulaires à zéro
            dbStatusLabel.setText("Toutes les données ont été supprimées."); // confirme l'opération à l'utilisateur
        } catch (SQLException e) {
            dbStatusLabel.setText("Erreur lors de la suppression : " + e.getMessage());
        }
    }

    /**
     * Met à jour le formulaire "Histoire" et les listes dépendantes
     * (personnages, scènes) suite à une sélection dans storyListView.
     * story == null signifie "aucune sélection" (mode création / vue vidée).
     */
    private void selectStory(Story story) {
        currentStory = story; // mémorise quelle histoire est désormais sélectionnée
        storyErrorLabel.setText(""); // efface tout message d'erreur précédent
        searchKeywordField.clear(); // vide le champ de recherche, on repart d'une recherche neutre
        searchStatusComboBox.setValue(null); // remet le filtre de statut sur "tous"
        searchCharacterComboBox.setValue(null); // remet le filtre de personnage sur "tous"
        if (story == null) {
            // aucune histoire sélectionnée : on vide tous les champs et toutes les listes dépendantes
            storyTitleField.clear();
            storyAuthorField.clear();
            storySummaryField.clear();
            characterListView.setItems(FXCollections.observableArrayList()); // liste de personnages vide
            filteredScenes = null; // plus de filtre actif puisqu'il n'y a plus de scènes à filtrer
            sceneListView.setItems(FXCollections.observableArrayList()); // liste de scènes vide
            searchCharacterComboBox.setItems(FXCollections.observableArrayList()); // plus de personnages proposés dans le filtre
        } else {
            // une histoire est sélectionnée : on remplit les champs avec ses informations
            storyTitleField.setText(story.getTitle());
            storyAuthorField.setText(story.getAuthor());
            storySummaryField.setText(story.getSummary());
            // Les ListView personnages/scènes pointent directement vers les ObservableList
            // de l'histoire : toute modification (ajout/suppression) s'y reflète automatiquement.
            characterListView.setItems(story.getCharacters()); // affiche les personnages de cette histoire
            // sceneListView affiche une vue filtrée de story.getScenes() (voir applySceneFilter) ;
            // sans filtre actif (cas par défaut), elle montre toutes les scènes.
            filteredScenes = new FilteredList<>(story.getScenes(), scene -> true); // crée la vue filtrée, sans aucun filtre actif au départ
            sceneListView.setItems(filteredScenes); // affiche cette vue filtrée dans la liste de scènes
            // null = "tous les personnages" dans le filtre de recherche.
            ObservableList<Character> characterFilterChoices = FXCollections.observableArrayList(story.getCharacters()); // copie des personnages de l'histoire
            characterFilterChoices.add(0, null); // on ajoute une option vide en première position, qui signifie "pas de filtre sur le personnage"
            searchCharacterComboBox.setItems(characterFilterChoices); // propose ces choix dans le filtre de personnage
        }
        // Changer d'histoire invalide forcément la sélection personnage/scène précédente.
        selectCharacter(null); // vide le formulaire personnage
        selectScene(null); // vide le formulaire scène
        refreshStatistics(); // recalcule les statistiques pour la nouvelle histoire sélectionnée
    }

    /** Met à jour le formulaire "Personnage" suite à une sélection dans characterListView. */
    private void selectCharacter(Character character) {
        currentCharacter = character; // mémorise quel personnage est désormais sélectionné
        characterErrorLabel.setText(""); // efface tout message d'erreur précédent
        if (character == null) {
            // aucun personnage sélectionné : on vide le formulaire, prêt pour une création
            characterNameField.clear();
            characterRoleField.clear();
            characterDescriptionField.clear();
        } else {
            // un personnage est sélectionné : on remplit le formulaire avec ses informations
            characterNameField.setText(character.getName());
            characterRoleField.setText(character.getRole());
            characterDescriptionField.setText(character.getDescription());
        }
    }

    /** Met à jour le formulaire "Scène" suite à une sélection dans sceneListView. */
    private void selectScene(Scene scene) {
        currentScene = scene; // mémorise quelle scène est désormais sélectionnée
        sceneErrorLabel.setText(""); // efface tout message d'erreur précédent
        // La liste des personnages "présents" proposés est toujours celle du casting de l'histoire courante.
        // on propose toujours, dans la liste à cocher, l'ensemble des personnages de l'histoire courante (ou rien si aucune histoire n'est sélectionnée)
        scenePresentCharactersListView.setItems(currentStory == null ? FXCollections.observableArrayList() : currentStory.getCharacters());
        if (scene == null) {
            // aucune scène sélectionnée : on vide le formulaire, prêt pour une création
            sceneTitleField.clear();
            sceneLocationField.clear();
            sceneMomentField.clear();
            sceneContentField.clear();
            sceneStatusComboBox.setValue(SceneStatus.BROUILLON); // valeur par défaut proposée pour une nouvelle scène
            scenePresentCharactersListView.getSelectionModel().clearSelection(); // aucun personnage cochée au départ
        } else {
            // une scène est sélectionnée : on remplit le formulaire avec ses informations
            sceneTitleField.setText(scene.getTitle());
            sceneLocationField.setText(scene.getLocation());
            sceneMomentField.setText(scene.getMoment());
            sceneContentField.setText(scene.getContent());
            sceneStatusComboBox.setValue(scene.getStatus());
            // Pré-sélectionne dans la liste les personnages déjà présents dans cette scène.
            MultipleSelectionModel<Character> selection = scenePresentCharactersListView.getSelectionModel();
            selection.clearSelection(); // on repart d'aucune sélection
            for (Character character : scene.getPresentCharacters()) {
                // puis on coche chaque personnage qui est effectivement déjà présent dans cette scène
                selection.select(character);
            }
        }
    }

    /** Bouton "Nouvelle" (histoire) : vide le formulaire pour préparer une création. */
    @FXML
    private void onNewStory() {
        storyListView.getSelectionModel().clearSelection(); // on désélectionne toute histoire dans la liste
        selectStory(null); // ce qui vide le formulaire pour préparer une nouvelle saisie
    }

    /**
     * Bouton "Charger des données de démo" : crée l'histoire arthurienne
     * via le moteur générique loadScenario (voir DemoScenario plus bas).
     */
    @FXML
    private void onLoadMockData() {
        // on construit les données de démonstration "Légendes arthuriennes" puis on les envoie au moteur de chargement
        loadScenario(new DemoScenario(
                "La Légende du Roi Arthur",
                "Légendes arthuriennes (domaine public)",
                "Arthur tire Excalibur de la pierre, fonde la Table Ronde à Camelot, "
                        + "puis voit son royaume menacé par la trahison et la magie.",
                List.of(
                        new DemoCharacter("Arthur", "Roi", "Roi légendaire de Bretagne, tire Excalibur de la pierre."),
                        new DemoCharacter("Merlin", "Enchanteur", "Conseiller et magicien d'Arthur."),
                        new DemoCharacter("Lancelot", "Chevalier", "Le plus vaillant chevalier de la Table Ronde."),
                        new DemoCharacter("Guenièvre", "Reine", "Épouse d'Arthur."),
                        new DemoCharacter("Morgane", "Sorcière", "Demi-sœur d'Arthur, rivale par la magie."),
                        new DemoCharacter("Perceval", "Chevalier", "Chevalier en quête du Graal.")),
                List.of(
                        new DemoScene("Excalibur dans la pierre", "Londres", "Jeunesse d'Arthur",
                                "Arthur retire l'épée Excalibur de la pierre, prouvant son droit au trône.",
                                SceneStatus.PUBLIEE, List.of("Arthur", "Merlin")),
                        new DemoScene("Le Serment de la Table Ronde", "Camelot", "Début du règne",
                                "Arthur réunit ses chevaliers et fonde l'ordre de la Table Ronde.",
                                SceneStatus.PUBLIEE, List.of("Arthur", "Lancelot", "Guenièvre", "Perceval")),
                        new DemoScene("La Trahison de Lancelot", "Camelot", "Plusieurs années plus tard",
                                "L'amour secret entre Lancelot et Guenièvre fragilise la Table Ronde.",
                                SceneStatus.EN_COURS, List.of("Lancelot", "Guenièvre", "Arthur")),
                        new DemoScene("La Vengeance de Morgane", "Avalon", "Fin du règne",
                                "Morgane orchestre la chute d'Arthur lors de la bataille de Camlann.",
                                SceneStatus.BROUILLON, List.of("Morgane", "Arthur")))));
    }

    /**
     * Bouton "Charger le scénario de démonstration" : crée le jeu de données
     * exact du scénario de démonstration fourni dans l'énoncé du projet
     * (« Le Secret de la Bibliothèque »), via le même moteur générique
     * loadScenario. Sert de support pour la vidéo et la démonstration en
     * classe.
     */
    @FXML
    private void onLoadDemoScenarioData() {
        // on construit les données de démonstration "Le Secret de la Bibliothèque" puis on les envoie au moteur de chargement
        loadScenario(new DemoScenario(
                "Le Secret de la Bibliothèque",
                "Scénario de démonstration (sujet du projet)",
                "Une ancienne bibliothèque renferme un secret oublié depuis plusieurs décennies. "
                        + "Plusieurs personnages vont progressivement découvrir des indices menant à une révélation inattendue.",
                List.of(
                        new DemoCharacter("Emma", "Étudiante", "Passionnée d'histoire et de littérature."),
                        new DemoCharacter("Lucas", "Journaliste", "Curieux et persévérant."),
                        new DemoCharacter("Madame Moreau", "Bibliothécaire", "Gardienne des archives de la bibliothèque."),
                        new DemoCharacter("Victor", "Historien", "Spécialiste des documents anciens.")),
                List.of(
                        new DemoScene("La découverte du livre", "Bibliothèque", "Matin",
                                "Emma découvre un livre ancien contenant des annotations mystérieuses.",
                                SceneStatus.BROUILLON, List.of("Emma", "Madame Moreau")),
                        new DemoScene("Recherche dans les archives", "Salle des archives", "Après-midi",
                                "Les deux personnages recherchent l'origine des annotations trouvées dans le livre.",
                                SceneStatus.EN_COURS, List.of("Emma", "Lucas")),
                        new DemoScene("Le témoignage de Victor", "Université", "Fin de journée",
                                "Victor apporte de nouvelles informations concernant l'histoire de la bibliothèque.",
                                SceneStatus.EN_COURS, List.of("Lucas", "Victor")),
                        new DemoScene("La révélation du secret", "Sous-sol de la bibliothèque", "Soir",
                                "Les personnages découvrent enfin le secret caché derrière les archives.",
                                SceneStatus.PRET_A_PUBLIER, List.of("Emma", "Lucas", "Madame Moreau", "Victor")))));
    }

    /** Un personnage d'un jeu de données de démo (voir DemoScenario). */
    private record DemoCharacter(String name, String role, String description) {
    }

    /** Une scène d'un jeu de données de démo ; presentCharacterNames référence DemoCharacter.name(). */
    private record DemoScene(String title, String location, String moment, String content,
                              SceneStatus status, List<String> presentCharacterNames) {
    }

    /** Un jeu de données de démo complet (une histoire, son casting, ses scènes). */
    private record DemoScenario(String title, String author, String summary,
                                 List<DemoCharacter> characters, List<DemoScene> scenes) {
    }

    /**
     * Moteur commun aux boutons "Charger des données de démo" : crée
     * l'histoire, ses personnages puis ses scènes décrits par un
     * DemoScenario, en passant uniquement par StoryService — donc avec
     * exactement les mêmes règles de validation et de persistance qu'une
     * saisie manuelle. Seules les données diffèrent d'un scénario à l'autre.
     */
    private void loadScenario(DemoScenario scenario) {
        try {
            Story story = storyService.createStory(scenario.title(), scenario.author(), scenario.summary()); // crée l'histoire elle-même
            stories.add(story); // l'ajoute à la liste affichée à l'écran

            Map<String, Character> charactersByName = new HashMap<>(); // dictionnaire nom -> personnage, pour retrouver facilement un personnage par son nom ensuite
            for (DemoCharacter demoCharacter : scenario.characters()) {
                // pour chaque personnage décrit dans le scénario, on le crée réellement via le service
                Character character = storyService.addCharacter(story, demoCharacter.name(), demoCharacter.role(), demoCharacter.description());
                charactersByName.put(demoCharacter.name(), character); // on le mémorise dans le dictionnaire pour pouvoir le retrouver par son nom
            }

            for (DemoScene demoScene : scenario.scenes()) {
                // pour chaque scène décrite dans le scénario, on la crée réellement via le service
                Scene scene = storyService.addScene(story, demoScene.title(), demoScene.location(), demoScene.moment(),
                        demoScene.content(), demoScene.status());
                for (String characterName : demoScene.presentCharacterNames()) {
                    // pour chaque nom de personnage présent dans cette scène, on retrouve l'objet correspondant et on l'associe à la scène
                    storyService.addCharacterToScene(story, scene, charactersByName.get(characterName));
                }
            }

            storyListView.getSelectionModel().select(story); // sélectionne automatiquement l'histoire nouvellement créée
            storyErrorLabel.setText(""); // tout s'est bien passé, on efface les erreurs précédentes
        } catch (IllegalArgumentException e) {
            // une règle métier a été violée (ne devrait pas arriver avec des données de démo correctes)
            storyErrorLabel.setText(e.getMessage());
        } catch (SQLException e) {
            // la base de données a refusé une des opérations
            storyErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /** Bouton "Enregistrer l'histoire" : crée ou met à jour selon que currentStory est null ou pas. */
    @FXML
    private void onSaveStory() {
        try {
            String title = storyTitleField.getText(); // lit le titre saisi dans le formulaire
            String author = storyAuthorField.getText(); // lit l'auteur saisi dans le formulaire
            String summary = storySummaryField.getText(); // lit le résumé saisi dans le formulaire
            if (currentStory == null) {
                // pas d'histoire sélectionnée : il s'agit d'une création
                Story story = storyService.createStory(title, author, summary);
                stories.add(story); // on ajoute la nouvelle histoire à la liste affichée
                storyListView.getSelectionModel().select(story); // et on la sélectionne automatiquement
            } else {
                // une histoire est déjà sélectionnée : il s'agit d'une modification
                storyService.updateStory(currentStory, title, author, summary);
                storyListView.refresh(); // force le réaffichage de la liste pour montrer le nouveau titre éventuel
            }
            storyErrorLabel.setText(""); // tout s'est bien passé, on efface les erreurs précédentes
        } catch (IllegalArgumentException e) {
            // Erreur de validation métier (ex: titre vide) : affichée telle quelle à l'utilisateur.
            storyErrorLabel.setText(e.getMessage());
        } catch (SQLException e) {
            storyErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /** Bouton "Supprimer" (histoire) : supprime l'histoire sélectionnée (et son contenu, en cascade). */
    @FXML
    private void onDeleteStory() {
        Story selected = storyListView.getSelectionModel().getSelectedItem(); // récupère l'histoire actuellement sélectionnée
        if (selected == null) {
            // rien n'est sélectionné : il n'y a rien à supprimer
            return;
        }
        try {
            storyService.deleteStory(selected); // demande au service de supprimer cette histoire (et son contenu)
            stories.remove(selected); // la retire aussi de la liste affichée à l'écran
        } catch (SQLException e) {
            storyErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /** Bouton "Nouveau" (personnage) : refuse si aucune histoire n'est sélectionnée, sinon vide le formulaire. */
    @FXML
    private void onNewCharacter() {
        if (currentStory == null) {
            // on ne peut pas créer de personnage si on ne sait pas à quelle histoire le rattacher
            characterErrorLabel.setText("Sélectionnez d'abord une histoire.");
            return;
        }
        characterListView.getSelectionModel().clearSelection(); // on désélectionne tout personnage dans la liste
        selectCharacter(null); // ce qui vide le formulaire pour préparer une nouvelle saisie
    }

    /** Bouton "Enregistrer le personnage" : crée ou met à jour selon que currentCharacter est null ou pas. */
    @FXML
    private void onSaveCharacter() {
        if (currentStory == null) {
            // un personnage doit toujours appartenir à une histoire
            characterErrorLabel.setText("Sélectionnez d'abord une histoire.");
            return;
        }
        try {
            String name = characterNameField.getText(); // lit le nom saisi dans le formulaire
            String role = characterRoleField.getText(); // lit le rôle saisi dans le formulaire
            String description = characterDescriptionField.getText(); // lit la description saisie dans le formulaire
            if (currentCharacter == null) {
                // pas de personnage sélectionné : il s'agit d'une création
                Character character = storyService.addCharacter(currentStory, name, role, description);
                characterListView.getSelectionModel().select(character); // sélectionne automatiquement le personnage créé
            } else {
                // un personnage est déjà sélectionné : il s'agit d'une modification
                storyService.updateCharacter(currentStory, currentCharacter, name, role, description);
                characterListView.refresh(); // force le réaffichage de la liste pour montrer les changements éventuels
            }
            characterErrorLabel.setText(""); // tout s'est bien passé, on efface les erreurs précédentes
            refreshStatistics(); // le nombre de personnages a pu changer, on met à jour les statistiques
        } catch (IllegalArgumentException e) {
            characterErrorLabel.setText(e.getMessage());
        } catch (SQLException e) {
            characterErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /** Bouton "Supprimer" (personnage) : supprime le personnage sélectionné (et ses présences dans les scènes). */
    @FXML
    private void onDeleteCharacter() {
        Character selected = characterListView.getSelectionModel().getSelectedItem(); // récupère le personnage actuellement sélectionné
        if (selected == null || currentStory == null) {
            // rien n'est sélectionné, ou pas d'histoire courante : il n'y a rien à supprimer
            return;
        }
        try {
            storyService.deleteCharacter(currentStory, selected); // demande au service de supprimer ce personnage
            refreshStatistics(); // le nombre de personnages a changé, on met à jour les statistiques
        } catch (SQLException e) {
            characterErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /** Bouton "Nouvelle" (scène) : refuse si aucune histoire n'est sélectionnée, sinon vide le formulaire. */
    @FXML
    private void onNewScene() {
        if (currentStory == null) {
            // une scène doit toujours appartenir à une histoire
            sceneErrorLabel.setText("Sélectionnez d'abord une histoire.");
            return;
        }
        sceneListView.getSelectionModel().clearSelection(); // on désélectionne toute scène dans la liste
        selectScene(null); // ce qui vide le formulaire pour préparer une nouvelle saisie
    }

    /**
     * Bouton "Enregistrer la scène" : crée ou met à jour la scène, puis
     * synchronise la liste des personnages présents avec la sélection
     * courante de scenePresentCharactersListView.
     */
    @FXML
    private void onSaveScene() {
        if (currentStory == null) {
            sceneErrorLabel.setText("Sélectionnez d'abord une histoire.");
            return;
        }
        try {
            String title = sceneTitleField.getText(); // lit le titre saisi dans le formulaire
            String location = sceneLocationField.getText(); // lit le lieu saisi dans le formulaire
            String moment = sceneMomentField.getText(); // lit le moment saisi dans le formulaire
            String content = sceneContentField.getText(); // lit le contenu saisi dans le formulaire
            SceneStatus status = sceneStatusComboBox.getValue(); // lit le statut choisi dans le formulaire
            Scene scene;
            if (currentScene == null) {
                // Pas de refresh() ici : ajouter une scène en fin de liste ne change
                // jamais la position des scènes déjà présentes (voir Story.addScene).
                // pas de scène sélectionnée : il s'agit d'une création
                scene = storyService.addScene(currentStory, title, location, moment, content, status);
                sceneListView.getSelectionModel().select(scene); // sélectionne automatiquement la scène créée
            } else {
                // une scène est déjà sélectionnée : il s'agit d'une modification
                scene = currentScene;
                storyService.updateScene(currentStory, scene, title, location, moment, content, status);
                sceneListView.refresh(); // force le réaffichage de la liste pour montrer les changements éventuels
            }
            applyPresentCharactersSelection(scene); // synchronise les personnages présents avec ce qui est cochée dans le formulaire
            sceneErrorLabel.setText(""); // tout s'est bien passé, on efface les erreurs précédentes
            refreshStatistics(); // le nombre de scènes ou les statuts ont pu changer, on met à jour les statistiques
        } catch (IllegalArgumentException e) {
            sceneErrorLabel.setText(e.getMessage());
        } catch (SQLException e) {
            sceneErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /**
     * Compare la sélection courante de la liste multi-sélection avec les
     * personnages déjà présents dans la scène, et n'appelle le service que
     * pour les différences (ajouts et retraits), plutôt que de tout réécrire.
     */
    private void applyPresentCharactersSelection(Scene scene) throws SQLException {
        // récupère la liste des personnages actuellement cochés dans le formulaire
        List<Character> selected = new ArrayList<>(scenePresentCharactersListView.getSelectionModel().getSelectedItems());
        for (Character character : new ArrayList<>(scene.getPresentCharacters())) {
            if (!selected.contains(character)) {
                // ce personnage était présent avant, mais n'est plus cochée : on le retire de la scène
                storyService.removeCharacterFromScene(currentStory, scene, character);
            }
        }
        for (Character character : selected) {
            if (!scene.getPresentCharacters().contains(character)) {
                // ce personnage est cochée mais n'était pas encore présent : on l'ajoute à la scène
                storyService.addCharacterToScene(currentStory, scene, character);
            }
        }
    }

    /** Bouton "Supprimer" (scène) : supprime la scène sélectionnée. */
    @FXML
    private void onDeleteScene() {
        Scene selected = sceneListView.getSelectionModel().getSelectedItem(); // récupère la scène actuellement sélectionnée
        if (selected == null || currentStory == null) {
            // rien n'est sélectionné, ou pas d'histoire courante : il n'y a rien à supprimer
            return;
        }
        try {
            storyService.deleteScene(currentStory, selected); // demande au service de supprimer cette scène
            // La suppression décale la position des scènes suivantes (voir Story.renumberScenes) ;
            // refresh() force le redessin de leurs libellés "N. Titre".
            sceneListView.refresh(); // force le réaffichage de la liste pour montrer les nouveaux numéros
            refreshStatistics(); // le nombre de scènes a changé, on met à jour les statistiques
        } catch (SQLException e) {
            sceneErrorLabel.setText("Erreur base de données : " + e.getMessage());
        }
    }

    /** Bouton "Rechercher" : applique le mot-clé/statut/personnage saisis comme filtre de sceneListView (en mémoire, pas de SQL). */
    @FXML
    private void onSearchScenes() {
        applySceneFilter(); // recalcule simplement quelles scènes doivent être affichées
    }

    /** Bouton "Réinitialiser" : vide les champs de recherche et réaffiche toutes les scènes de l'histoire. */
    @FXML
    private void onResetSceneFilters() {
        searchKeywordField.clear(); // vide le mot-clé recherché
        searchStatusComboBox.setValue(null); // remet le filtre de statut sur "tous"
        searchCharacterComboBox.setValue(null); // remet le filtre de personnage sur "tous"
        applySceneFilter(); // applique ce filtre désormais vide, ce qui réaffiche toutes les scènes
    }

    /**
     * Recalcule et affiche les statistiques de l'histoire courante (nombre
     * total de personnages/scènes, répartition par statut, apparitions par
     * personnage). Appelée après chaque opération qui modifie les
     * personnages ou les scènes, pour que l'affichage reste à jour sans
     * action manuelle (voir onSaveCharacter, onDeleteCharacter, onSaveScene,
     * onDeleteScene, onLoadMockData et selectStory).
     */
    private void refreshStatistics() {
        if (currentStory == null) {
            // pas d'histoire sélectionnée : on ne peut afficher aucune statistique
            statisticsArea.setText("Sélectionnez d'abord une histoire.");
            return;
        }
        StringBuilder text = new StringBuilder(); // on construit le texte des statistiques morceau par morceau
        text.append("Nombre total de personnages : ").append(storyService.countTotalCharacters(currentStory)).append('\n');
        text.append("Nombre total de scènes : ").append(storyService.countTotalScenes(currentStory)).append('\n');
        text.append("\nScènes par statut :\n");
        // pour chaque statut et son nombre de scènes associées, on ajoute une ligne au texte
        storyService.countScenesByStatus(currentStory).forEach((status, count) -> text.append("  - ").append(status).append(" : ").append(count).append('\n'));
        text.append("\nApparitions par personnage :\n");
        // pour chaque personnage et son nombre d'apparitions, on ajoute une ligne au texte
        storyService.countSceneAppearancesByCharacter(currentStory).forEach((character, count) -> text.append("  - ").append(character.getName()).append(" : ").append(count).append('\n'));
        statisticsArea.setText(text.toString()); // affiche enfin le texte complet construit
    }
}
