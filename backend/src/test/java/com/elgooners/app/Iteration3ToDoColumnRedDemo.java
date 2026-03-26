package com.elgooners.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Intentionally red tests for not-yet-implemented tasks.
 * Run explicitly during demo:
 * mvn -Dtest=Iteration3ToDoColumnRedDemo test
 */
class Iteration3ToDoColumnRedDemo {

    @Test
    @DisplayName("EL-15 Finding other/friends profiles is not implemented yet")
    void el15_findingFriendsProfiles_red() {
        fail("EL-15 TODO: implement friends profile discovery endpoint and UI flow.");
    }

    @Test
    @DisplayName("EL-5 Number/total time of song listens is not implemented yet")
    void el5_songListenCountAndDuration_red() {
        fail("EL-5 TODO: track per-user listen counts and total listen time.");
    }

    @Test
    @DisplayName("EL-16 Sorting profiles by genre is not implemented yet")
    void el16_sortProfilesByGenre_red() {
        fail("EL-16 TODO: add profile genre indexing and sorting/filter endpoint.");
    }

    @Test
    @DisplayName("EL-20 Comments are not implemented yet")
    void el20_commentsFeature_red() {
        fail("EL-20 TODO: implement comments persistence, moderation, and API contract.");
    }
}

