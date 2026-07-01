package com.storyforge;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Classe d'application JavaFX principale de StoryForge.
 * Son seul rôle est de construire la fenêtre principale à partir du fichier
 * FXML (main-view.fxml), dont le contrôleur est MainController.
 */
public class StoryForgeApp extends Application {

    /**
     * Appelée automatiquement par le framework JavaFX au démarrage.
     * Charge la vue FXML, l'installe dans une Scene, puis affiche la fenêtre.
     */
    @Override
    public void start(Stage stage) throws IOException {
        // on charge la description de l'interface depuis le fichier FXML
        FXMLLoader fxmlLoader = new FXMLLoader(StoryForgeApp.class.getResource("main-view.fxml"));
        // on construit la fenêtre (900x650 pixels) à partir de cette interface
        Scene scene = new Scene(fxmlLoader.load(), 900, 650);
        // on applique la feuille de style (couleurs, formes, effets) à la fenêtre
        scene.getStylesheets().add(StoryForgeApp.class.getResource("styles.css").toExternalForm());
        stage.setTitle("StoryForge"); // on donne un titre à la fenêtre
        stage.setScene(scene); // on installe le contenu construit dans la fenêtre
        stage.show(); // on affiche enfin la fenêtre à l'écran
    }
}
