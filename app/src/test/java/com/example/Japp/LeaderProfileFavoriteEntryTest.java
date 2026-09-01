package com.example.Japp;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LeaderProfileFavoriteEntryTest {

    @Test
    public void leaderProfileExposesFavoriteOrdersLikeUserProfile() throws Exception {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        String layout = new String(Files.readAllBytes(
                projectRoot.resolve("app/src/main/res/layout/leader_fragment_profile.xml")),
                StandardCharsets.UTF_8);
        String fragment = new String(Files.readAllBytes(
                projectRoot.resolve("app/src/main/java/com/example/Japp/leader/fragment/profile/profile.java")),
                StandardCharsets.UTF_8);

        assertTrue(layout.contains("@+id/btnFavoriteOrders"));
        assertTrue(layout.contains("android:text=\"我的收藏\""));
        assertTrue(fragment.contains("FavoriteOrdersActivity.class"));
        assertTrue(fragment.contains("R.id.btnFavoriteOrders"));
    }

    @Test
    public void leaderStatsAppearBeforePersonalInfoEntry() throws Exception {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        String layout = new String(Files.readAllBytes(
                projectRoot.resolve("app/src/main/res/layout/leader_fragment_profile.xml")),
                StandardCharsets.UTF_8);

        int pendingOrders = layout.indexOf("@+id/btnPendingOrders");
        int completedOrders = layout.indexOf("@+id/btnCompletedOrders");
        int rating = layout.indexOf("@+id/btnRating");
        int personalInfo = layout.indexOf("@+id/btnPersonalInfo");

        assertTrue(pendingOrders >= 0);
        assertTrue(completedOrders > pendingOrders);
        assertTrue(rating > completedOrders);
        assertTrue(personalInfo > rating);
    }

    @Test
    public void leaderIdentityBadgeAppearsBesideName() throws Exception {
        Path projectRoot = Paths.get(System.getProperty("user.dir")).getParent();
        String layout = new String(Files.readAllBytes(
                projectRoot.resolve("app/src/main/res/layout/leader_fragment_profile.xml")),
                StandardCharsets.UTF_8);

        int name = layout.indexOf("@+id/txtName");
        int badge = layout.indexOf("@+id/txtLeaderBadge");
        int region = layout.indexOf("@+id/txtStats");

        assertTrue(name >= 0);
        assertTrue(badge > name);
        assertTrue(region > badge);
        assertTrue(layout.contains("android:text=\"领队\""));
        assertTrue(layout.contains("@drawable/bg_leader_badge"));
    }
}
