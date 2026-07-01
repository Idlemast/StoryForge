package com.storyforge.service;

import com.storyforge.dao.CharacterRepository;
import com.storyforge.dao.SceneRepository;
import com.storyforge.dao.StoryRepository;
import com.storyforge.model.Character;
import com.storyforge.model.Scene;
import com.storyforge.model.SceneStatus;
import com.storyforge.model.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test d'intégration : fait collaborer StoryService avec de vrais objets du
 * modèle et des repositories en mémoire (pas de base MySQL), pour vérifier le
 * câblage complet règle métier -> persistance, sans dépendre de JDBC.
 */
class StoryServiceTest {

    /** Repository en mémoire minimal : se contente d'affecter un id auto-incrémenté à l'insertion. */
    private static class InMemoryStoryRepository implements StoryRepository {
        long nextId = 1; // prochain identifiant à distribuer, comme le ferait une vraie base de données
        @Override public List<Story> findAll() { return new ArrayList<>(); } // pas besoin de vraies données pour ces tests
        @Override public void insert(Story story) { story.setId(nextId++); } // donne un identifiant unique, puis le fait progresser pour la prochaine fois
        @Override public void update(Story story) { } // rien à faire : il n'y a pas de vraie base à mettre à jour
        @Override public void delete(long id) { } // rien à faire : il n'y a pas de vraie base où supprimer
    }

    private static class InMemoryCharacterRepository implements CharacterRepository {
        long nextId = 1; // prochain identifiant à distribuer
        @Override public List<Character> findByStoryId(long storyId) { return new ArrayList<>(); }
        @Override public void insert(Character character, long storyId) { character.setId(nextId++); } // donne un identifiant unique au personnage
        @Override public void update(Character character) { }
        @Override public void delete(long id) { }
    }

    private static class InMemorySceneRepository implements SceneRepository {
        long nextId = 1; // prochain identifiant à distribuer
        final Set<String> links = new HashSet<>(); // mémorise les liens scène-personnage sous forme de texte "idScene:idPersonnage"
        @Override public List<Scene> findByStoryId(long storyId, Map<Long, Character> charactersById) { return new ArrayList<>(); }
        @Override public void insert(Scene scene, long storyId) { scene.setId(nextId++); } // donne un identifiant unique à la scène
        @Override public void update(Scene scene) { }
        @Override public void delete(long id) { }
        @Override public void addCharacter(long sceneId, long characterId) { links.add(sceneId + ":" + characterId); } // mémorise ce nouveau lien
        @Override public void removeCharacter(long sceneId, long characterId) { links.remove(sceneId + ":" + characterId); } // oublie ce lien
    }

    private StoryService service; // le service métier qu'on teste, branché sur les faux repositories ci-dessus
    private InMemorySceneRepository sceneRepository; // gardé à part pour pouvoir vérifier son contenu (links) dans les tests

    @BeforeEach
    void setUp() {
        // avant chaque test, on repart d'un service neuf, avec des faux repositories vides
        sceneRepository = new InMemorySceneRepository();
        service = new StoryService(new InMemoryStoryRepository(), new InMemoryCharacterRepository(), sceneRepository);
    }

    @Test
    void fullWorkflowPersistsAndLinksCorrectly() throws SQLException {
        Story story = service.createStory("Titre", "Auteur", "Résumé"); // crée une histoire via le service
        assertEquals(1L, story.getId()); // on vérifie qu'elle a bien reçu le premier identifiant disponible

        Character arthur = service.addCharacter(story, "Arthur", "Roi", ""); // crée un personnage
        Scene scene = service.addScene(story, "Excalibur", "Londres", "Jeunesse", "Il tire l'épée.", SceneStatus.PUBLIEE); // crée une scène
        service.addCharacterToScene(story, scene, arthur); // associe le personnage à la scène

        assertTrue(scene.getPresentCharacters().contains(arthur)); // le personnage doit apparaître dans la scène en mémoire
        assertTrue(sceneRepository.links.contains(scene.getId() + ":" + arthur.getId())); // et le lien doit avoir été enregistré dans le faux repository

        service.removeCharacterFromScene(story, scene, arthur); // retire l'association
        assertTrue(sceneRepository.links.isEmpty()); // le lien doit avoir disparu du faux repository
    }

    @Test
    void searchScenesFiltersByKeywordAndStatus() throws SQLException {
        Story story = service.createStory("Titre", "Auteur", ""); // histoire de test
        Character arthur = service.addCharacter(story, "Arthur", "Roi", ""); // deux personnages
        Character lancelot = service.addCharacter(story, "Lancelot", "Chevalier", "");
        Scene s1 = service.addScene(story, "Excalibur dans la pierre", "Londres", "", "Arthur tire l'épée.", SceneStatus.PUBLIEE); // deux scènes
        Scene s2 = service.addScene(story, "Trahison", "Camelot", "", "Lancelot trahit Arthur.", SceneStatus.BROUILLON);
        service.addCharacterToScene(story, s1, arthur); // arthur est dans la scène 1
        service.addCharacterToScene(story, s2, lancelot); // lancelot est dans la scène 2

        List<Scene> byKeyword = service.searchScenes(story, "arthur", null, null); // recherche le mot "arthur" dans toutes les scènes
        assertEquals(2, byKeyword.size()); // les deux scènes mentionnent "Arthur" dans leur titre ou contenu

        List<Scene> byStatus = service.searchScenes(story, "", SceneStatus.PUBLIEE, null); // filtre uniquement sur le statut "Publiée"
        assertEquals(1, byStatus.size()); // seule la scène 1 a ce statut
        assertEquals("Excalibur dans la pierre", byStatus.get(0).getTitle());

        List<Scene> byBoth = service.searchScenes(story, "trahison", SceneStatus.BROUILLON, null); // mot-clé ET statut combinés
        assertEquals(1, byBoth.size()); // seule la scène 2 correspond aux deux critères à la fois

        // Le mot-clé ne porte que sur le titre et le contenu (énoncé), pas sur le lieu.
        List<Scene> byLocationOnly = service.searchScenes(story, "camelot", null, null); // "Camelot" n'est que le lieu de la scène 2, pas son titre/contenu
        assertEquals(0, byLocationOnly.size()); // donc aucune scène ne doit être trouvée

        List<Scene> byCharacter = service.searchScenes(story, "", null, lancelot); // filtre uniquement sur la présence de Lancelot
        assertEquals(1, byCharacter.size()); // seule la scène 2 contient Lancelot
        assertEquals("Trahison", byCharacter.get(0).getTitle());

        List<Scene> byCharacterAndStatus = service.searchScenes(story, "", SceneStatus.PUBLIEE, lancelot); // Lancelot ET statut "Publiée"
        assertEquals(0, byCharacterAndStatus.size()); // aucune scène ne correspond aux deux critères à la fois
    }

    @Test
    void countTotalCharactersAndScenes() throws SQLException {
        Story story = service.createStory("Titre", "Auteur", ""); // histoire de test
        service.addCharacter(story, "Arthur", "Roi", ""); // un seul personnage
        service.addScene(story, "S1", "", "", "c", SceneStatus.PUBLIEE); // deux scènes
        service.addScene(story, "S2", "", "", "c", SceneStatus.PUBLIEE);

        assertEquals(1, service.countTotalCharacters(story)); // on doit compter exactement 1 personnage
        assertEquals(2, service.countTotalScenes(story)); // on doit compter exactement 2 scènes
    }

    @Test
    void countScenesByStatusCountsEveryStatusIncludingZero() throws SQLException {
        Story story = service.createStory("Titre", "Auteur", ""); // histoire de test
        service.addScene(story, "S1", "", "", "c", SceneStatus.PUBLIEE); // deux scènes publiées, aucune dans les autres statuts
        service.addScene(story, "S2", "", "", "c", SceneStatus.PUBLIEE);

        Map<SceneStatus, Long> counts = service.countScenesByStatus(story);
        assertEquals(2L, counts.get(SceneStatus.PUBLIEE)); // 2 scènes publiées
        assertEquals(0L, counts.get(SceneStatus.BROUILLON)); // 0 scène en brouillon, mais la clé doit malgré tout exister dans le résultat
    }

    @Test
    void countSceneAppearancesByCharacterCountsPresences() throws SQLException {
        Story story = service.createStory("Titre", "Auteur", ""); // histoire de test
        Character arthur = service.addCharacter(story, "Arthur", "Roi", ""); // un personnage
        Scene s1 = service.addScene(story, "S1", "", "", "c", SceneStatus.PUBLIEE); // deux scènes
        Scene s2 = service.addScene(story, "S2", "", "", "c", SceneStatus.PUBLIEE);
        service.addCharacterToScene(story, s1, arthur); // arthur apparaît dans les deux scènes
        service.addCharacterToScene(story, s2, arthur);

        Map<Character, Long> counts = service.countSceneAppearancesByCharacter(story);
        assertEquals(2L, counts.get(arthur)); // on doit donc compter 2 apparitions pour arthur
    }
}
