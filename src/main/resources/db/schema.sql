CREATE DATABASE IF NOT EXISTS storyforge CHARACTER SET utf8mb4;
USE storyforge;

CREATE TABLE IF NOT EXISTS story (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    summary TEXT
);

CREATE TABLE IF NOT EXISTS characters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    `role` VARCHAR(255) NOT NULL,
    description TEXT,
    CONSTRAINT fk_character_story FOREIGN KEY (story_id)
        REFERENCES story(id) ON DELETE CASCADE,
    CONSTRAINT uq_character_story_name UNIQUE (story_id, name)
);

CREATE TABLE IF NOT EXISTS scenes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    story_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    moment VARCHAR(255),
    content TEXT NOT NULL,
    position INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_scene_story FOREIGN KEY (story_id)
        REFERENCES story(id) ON DELETE CASCADE
    -- Pas de contrainte UNIQUE(story_id, position) : l'unicité/contiguïté
    -- des positions est garantie côté Java par Story.renumberScenes, et une
    -- contrainte stricte gênerait les mises à jour intermédiaires lors d'un
    -- réordonnancement (deux scènes peuvent se partager une position pendant
    -- la séquence d'UPDATE, avant renumérotation complète).
);

CREATE TABLE IF NOT EXISTS scene_characters (
    scene_id BIGINT NOT NULL,
    character_id BIGINT NOT NULL,
    PRIMARY KEY (scene_id, character_id),
    CONSTRAINT fk_sc_scene FOREIGN KEY (scene_id) REFERENCES scenes(id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_character FOREIGN KEY (character_id) REFERENCES characters(id) ON DELETE CASCADE
);
