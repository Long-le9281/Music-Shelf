package com.elgooners.app;

/*
 * Elgooners Record Shelf - Backend (Spring Boot)
 * ------------------------------------------------
 * HOW TO RUN:
 *   1. Open the "backend" folder in IntelliJ as a Maven project
 *   2. Wait for Maven to download dependencies (first time only)
 *   3. Click the green Run button next to "main" below
 *   4. Backend runs at http://localhost:8080
 *
 * WHAT THIS FILE DOES:
 *   - Connects to the SQLite database
 *   - Provides API endpoints the React frontend calls
 *   - Handles login/signup with password hashing
 *   - Protects private endpoints with JWT tokens
 *
 * API ENDPOINTS:
 *   POST /api/auth/signup       - create an account
 *   POST /api/auth/login        - login, get a token back
 *   GET  /api/albums            - get all albums
 *   GET  /api/albums/{id}       - get one album + its songs
 *   GET  /api/search?q=nirvana  - search albums
 *   POST /api/ratings/{albumId} - rate an album (login required)
 *   GET  /api/ratings/{albumId} - get your rating for an album
 *   GET  /api/profile/{username}- get a user's profile + ratings
 */

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

// ============================================================
// ENTRY POINT - Spring Boot starts here
// ============================================================

@SpringBootApplication
class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}

// ============================================================
// DATABASE
// Handles all SQL queries to elgooners.db
// To change the database location, update DB_PATH below.
// ============================================================

@Component
class Database {

    // Path to the SQLite database file (relative to where you run the app)
    private static final String DB_PATH = System.getProperty("user.dir")
            .replace("\\backend", "")
            .replace("/backend", "")
            + "/database/elgooners.db";

    private final BCryptPasswordEncoder seedPasswordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    void init() {
        ensureSchema();
        seedUsers();
        ensurePrimaryAlbumsHaveSongs();
    }

    private void ensureSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            addColumnIfMissing(conn, "users", "is_admin", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "users", "bio", "TEXT DEFAULT ''");
            addColumnIfMissing(conn, "users", "created_at", "TEXT");
            addColumnIfMissing(conn, "users", "display_name", "TEXT");
            addColumnIfMissing(conn, "users", "is_active", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(conn, "users", "disabled_at", "TEXT");
            addColumnIfMissing(conn, "users", "deleted_at", "TEXT");
            addColumnIfMissing(conn, "users", "password_reset_at", "TEXT");
            addColumnIfMissing(conn, "albums", "album_art_url", "TEXT DEFAULT ''");
            addColumnIfMissing(conn, "albums", "is_single", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(conn, "songs", "lyrics", "TEXT DEFAULT ''");
            addColumnIfMissing(conn, "ratings", "updated_at", "TEXT");
            stmt.execute("UPDATE users SET created_at = COALESCE(NULLIF(created_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("UPDATE users SET display_name = COALESCE(NULLIF(display_name, ''), username)");
            stmt.execute("UPDATE users SET bio = COALESCE(bio, '')");
            stmt.execute("UPDATE users SET is_active = COALESCE(is_active, 1)");
            stmt.execute("UPDATE albums SET is_single = COALESCE(is_single, 0)");
            stmt.execute("UPDATE albums SET is_single = 1 WHERE LOWER(TRIM(title)) IN ('single', 'singles')");
            stmt.execute("UPDATE ratings SET updated_at = COALESCE(NULLIF(updated_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_album_identity ON albums(title, artist)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_song_identity ON songs(album_id, track_number, title)");
        } catch (SQLException e) {
            System.out.println("Error ensuring schema: " + e.getMessage());
        }
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String ddl) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase());
            }
        }
        if (!columns.contains(column.toLowerCase())) {
            try (Statement alter = conn.createStatement()) {
                alter.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddl);
            }
        }
    }

    private void seedUsers() {
        createUserIfMissing("demo", "demo123", "#45B7D1", false, "Demo Listener", "Just browsing the shelf and rating classics.");
        createUserIfMissing("nostalgia", "demo123", "#F7DC6F", false, "Nostalgia Tapes", "Collector of 70s and 80s favorites.");
        createUserIfMissing("admin", "admin123", "#D4744F", true, "Shelf Admin", "Maintains the catalog and community.");
    }

    private void createUserIfMissing(String username,
                                     String plainPassword,
                                     String avatarColor,
                                     boolean isAdmin,
                                     String displayName,
                                     String bio) {
        if (usernameExists(username)) return;
        String sql = """
            INSERT INTO users (username, password, avatar_color, is_admin, display_name, bio)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, seedPasswordEncoder.encode(plainPassword));
            stmt.setString(3, avatarColor);
            stmt.setInt(4, isAdmin ? 1 : 0);
            stmt.setString(5, displayName);
            stmt.setString(6, bio);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error seeding user '" + username + "': " + e.getMessage());
        }
    }

    private void ensurePrimaryAlbumsHaveSongs() {
        String sql = """
            SELECT a.id, a.title
            FROM albums a
            LEFT JOIN songs s ON s.album_id = a.id
            WHERE COALESCE(a.is_single, 0) = 0
            GROUP BY a.id
            HAVING COUNT(s.id) = 0
            """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                long albumId = rs.getLong("id");
                String title = rs.getString("title");
                createSong(albumId, title + " (Title Track)", 1, 180);
            }
        } catch (SQLException e) {
            System.out.println("Error ensuring default songs: " + e.getMessage());
        }
    }

    // Opens a connection to the database
    Connection getConnection() throws SQLException {
        System.out.println("Looking for DB at: " + DB_PATH);
        java.io.File f = new java.io.File(DB_PATH);
        System.out.println("File exists: " + f.exists());
        System.out.println("Absolute path: " + f.getAbsolutePath());
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    // ------- USER METHODS -------

    // Find a user by their username - returns null if not found
    Map<String, Object> findUser(String username) {
        String sql = """
            SELECT id, username, password, avatar_color, is_admin, bio, created_at,
                   is_active, disabled_at, deleted_at, password_reset_at,
                   COALESCE(display_name, username) AS display_name
            FROM users
            WHERE username = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id",          rs.getLong("id"));
                user.put("username",    rs.getString("username"));
                user.put("password",    rs.getString("password"));
                user.put("avatarColor", rs.getString("avatar_color"));
                user.put("isAdmin",     rs.getInt("is_admin") == 1);
                user.put("bio",         rs.getString("bio"));
                user.put("createdAt",   rs.getString("created_at"));
                user.put("displayName", rs.getString("display_name"));
                user.put("isActive",    rs.getInt("is_active") == 1);
                user.put("disabledAt",  rs.getString("disabled_at"));
                user.put("deletedAt",   rs.getString("deleted_at"));
                user.put("passwordResetAt", rs.getString("password_reset_at"));
                return user;
            }
        } catch (SQLException e) {
            System.out.println("Error finding user: " + e.getMessage());
        }
        return null;
    }

    // Check if a username is already taken
    boolean usernameExists(String username) {
        String sql = "SELECT id FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // Create a new user account
    long createUser(String username, String hashedPassword, String avatarColor) {
        String sql = "INSERT INTO users (username, password, avatar_color, display_name, bio) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, avatarColor);
            stmt.setString(4, username);
            stmt.setString(5, "");
            stmt.executeUpdate();
            return stmt.getGeneratedKeys().getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Could not create user: " + e.getMessage());
        }
    }

    List<Map<String, Object>> lookupUsers(String query) {
        String like = "%" + query.toLowerCase() + "%";
        String sql = """
            SELECT u.id, u.username, u.avatar_color, u.is_admin,
                   COALESCE(u.display_name, u.username) AS display_name,
                   (SELECT COUNT(*) FROM ratings r WHERE r.user_id = u.id) AS rating_count
            FROM users u
            WHERE u.deleted_at IS NULL
              AND u.is_active = 1
              AND (LOWER(u.username) LIKE ? OR LOWER(COALESCE(u.display_name, u.username)) LIKE ?)
            ORDER BY u.username
            LIMIT 12
            """;
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, like);
            stmt.setString(2, like);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getLong("id"));
                user.put("username", rs.getString("username"));
                user.put("displayName", rs.getString("display_name"));
                user.put("avatarColor", rs.getString("avatar_color"));
                user.put("isAdmin", rs.getInt("is_admin") == 1);
                user.put("ratingCount", rs.getInt("rating_count"));
                users.add(user);
            }
        } catch (SQLException e) {
            System.out.println("Error looking up users: " + e.getMessage());
        }
        return users;
    }

    boolean promoteUser(long userId) {
        return setUserAdminById(userId, true);
    }

    boolean setUserAdminById(long userId, boolean isAdmin) {
        String sql = "UPDATE users SET is_admin = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, isAdmin ? 1 : 0);
            stmt.setLong(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating user role: " + e.getMessage());
            return false;
        }
    }

    boolean setUserAdminByUsername(String username, boolean isAdmin) {
        String sql = "UPDATE users SET is_admin = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, isAdmin ? 1 : 0);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating user role: " + e.getMessage());
            return false;
        }
    }

    long createUserByAdmin(String username,
                           String hashedPassword,
                           String avatarColor,
                           String displayName,
                           String bio,
                           boolean isAdmin) {
        String sql = """
            INSERT INTO users (username, password, avatar_color, display_name, bio, is_admin, is_active)
            VALUES (?, ?, ?, ?, ?, ?, 1)
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, avatarColor);
            stmt.setString(4, displayName);
            stmt.setString(5, bio);
            stmt.setInt(6, isAdmin ? 1 : 0);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
            throw new RuntimeException("User insert succeeded but no ID returned");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create admin user: " + e.getMessage());
        }
    }

    boolean setUserActiveByUsername(String username, boolean isActive) {
        String sql = """
            UPDATE users
            SET is_active = ?,
                disabled_at = CASE WHEN ? = 1 THEN NULL ELSE CURRENT_TIMESTAMP END
            WHERE username = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, isActive ? 1 : 0);
            stmt.setInt(2, isActive ? 1 : 0);
            stmt.setString(3, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error updating user status: " + e.getMessage());
            return false;
        }
    }

    boolean softDeleteUserByUsername(String username) {
        String sql = """
            UPDATE users
            SET deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP),
                is_active = 0,
                disabled_at = COALESCE(disabled_at, CURRENT_TIMESTAMP)
            WHERE username = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error soft deleting user: " + e.getMessage());
            return false;
        }
    }

    boolean resetUserPasswordByUsername(String username, String hashedPassword) {
        String sql = """
            UPDATE users
            SET password = ?, password_reset_at = CURRENT_TIMESTAMP
            WHERE username = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, hashedPassword);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error resetting password: " + e.getMessage());
            return false;
        }
    }

    int countActiveAdmins() {
        String sql = """
            SELECT COUNT(*) AS c
            FROM users
            WHERE is_admin = 1 AND is_active = 1 AND deleted_at IS NULL
            """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.getInt("c");
        } catch (SQLException e) {
            System.out.println("Error counting admins: " + e.getMessage());
            return 0;
        }
    }

    List<Map<String, Object>> getAllUsersForAdmin() {
        String sql = """
            SELECT u.id, u.username, u.avatar_color, u.is_admin, u.created_at,
                   u.is_active, u.disabled_at, u.deleted_at, u.password_reset_at,
                   COALESCE(u.display_name, u.username) AS display_name,
                   u.bio,
                   (SELECT COUNT(*) FROM ratings r WHERE r.user_id = u.id) AS rating_count
            FROM users u
            ORDER BY u.is_admin DESC, u.username ASC
            """;
        List<Map<String, Object>> users = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> user = new HashMap<>();
                user.put("id", rs.getLong("id"));
                user.put("username", rs.getString("username"));
                user.put("displayName", rs.getString("display_name"));
                user.put("avatarColor", rs.getString("avatar_color"));
                user.put("isAdmin", rs.getInt("is_admin") == 1);
                user.put("isActive", rs.getInt("is_active") == 1);
                user.put("disabledAt", rs.getString("disabled_at"));
                user.put("deletedAt", rs.getString("deleted_at"));
                user.put("passwordResetAt", rs.getString("password_reset_at"));
                user.put("createdAt", rs.getString("created_at"));
                user.put("bio", rs.getString("bio"));
                user.put("ratingCount", rs.getInt("rating_count"));
                users.add(user);
            }
        } catch (SQLException e) {
            System.out.println("Error getting users for admin: " + e.getMessage());
        }
        return users;
    }

    // ------- ALBUM METHODS -------

    // Get all albums with their average rating
    List<Map<String, Object>> getAllAlbums() {
        String sql = """
            SELECT a.id, a.title, a.artist, a.release_year, a.genre,
                   a.description, a.color1, a.color2, a.album_art_url,
                   ROUND(AVG(r.stars), 1) AS avg_rating,
                   COUNT(r.id) AS rating_count
            FROM albums a
            LEFT JOIN ratings r ON r.album_id = a.id
            WHERE COALESCE(a.is_single, 0) = 0
            GROUP BY a.id
            ORDER BY a.title
            """;
        List<Map<String, Object>> albums = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> album = new HashMap<>();
                album.put("id",          rs.getLong("id"));
                album.put("title",       rs.getString("title"));
                album.put("artist",      rs.getString("artist"));
                album.put("releaseYear", rs.getInt("release_year"));
                album.put("genre",       rs.getString("genre"));
                album.put("description", rs.getString("description"));
                album.put("color1",      rs.getString("color1"));
                album.put("color2",      rs.getString("color2"));
                album.put("albumArtUrl", rs.getString("album_art_url"));
                album.put("avgRating",   rs.getObject("avg_rating"));
                album.put("ratingCount", rs.getLong("rating_count"));
                albums.add(album);
            }
        } catch (SQLException e) {
            System.out.println("Error getting albums: " + e.getMessage());
        }
        return albums;
    }

    // Get a single album by ID
    Map<String, Object> getAlbumById(long id) {
        String sql = """
            SELECT a.id, a.title, a.artist, a.release_year, a.genre,
                   a.description, a.color1, a.color2, a.album_art_url,
                   ROUND(AVG(r.stars), 1) AS avg_rating,
                   COUNT(r.id) AS rating_count
            FROM albums a
            LEFT JOIN ratings r ON r.album_id = a.id
            WHERE a.id = ?
            GROUP BY a.id
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> album = new HashMap<>();
                album.put("id",          rs.getLong("id"));
                album.put("title",       rs.getString("title"));
                album.put("artist",      rs.getString("artist"));
                album.put("releaseYear", rs.getInt("release_year"));
                album.put("genre",       rs.getString("genre"));
                album.put("description", rs.getString("description"));
                album.put("color1",      rs.getString("color1"));
                album.put("color2",      rs.getString("color2"));
                album.put("albumArtUrl", rs.getString("album_art_url"));
                album.put("avgRating",   rs.getObject("avg_rating"));
                album.put("ratingCount", rs.getLong("rating_count"));
                return album;
            }
        } catch (SQLException e) {
            System.out.println("Error getting album: " + e.getMessage());
        }
        return null;
    }

    // Get all songs for an album
    List<Map<String, Object>> getSongsForAlbum(long albumId) {
        String sql = "SELECT id, title, track_number, duration_seconds, lyrics FROM songs WHERE album_id = ? ORDER BY track_number";
        List<Map<String, Object>> songs = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, albumId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("id",              rs.getLong("id"));
                song.put("title",           rs.getString("title"));
                song.put("trackNumber",     rs.getInt("track_number"));
                song.put("durationSeconds", rs.getInt("duration_seconds"));
                song.put("lyrics",          rs.getString("lyrics"));
                songs.add(song);
            }
        } catch (SQLException e) {
            System.out.println("Error getting songs: " + e.getMessage());
        }
        return songs;
    }

    List<Map<String, Object>> getAllSongs() {
        String sql = """
            SELECT s.id, s.title, s.track_number, s.duration_seconds, s.lyrics,
                   a.id AS album_id, a.title AS album_title, a.artist,
                   a.release_year, a.genre, a.color1, a.color2, a.album_art_url
            FROM songs s
            JOIN albums a ON a.id = s.album_id
            ORDER BY a.title, s.track_number
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("id",              rs.getLong("id"));
                song.put("title",           rs.getString("title"));
                song.put("trackNumber",     rs.getInt("track_number"));
                song.put("durationSeconds", rs.getInt("duration_seconds"));
                song.put("lyrics",          rs.getString("lyrics"));
                song.put("albumId",         rs.getLong("album_id"));
                song.put("albumTitle",      rs.getString("album_title"));
                song.put("artist",          rs.getString("artist"));
                song.put("releaseYear",     rs.getInt("release_year"));
                song.put("genre",           rs.getString("genre"));
                song.put("color1",          rs.getString("color1"));
                song.put("color2",          rs.getString("color2"));
                song.put("albumArtUrl",     rs.getString("album_art_url"));
                results.add(song);
            }
        } catch (SQLException e) {
            System.out.println("Error getting all songs: " + e.getMessage());
        }
        return results;
    }

    // Search albums by title, artist, or genre
    List<Map<String, Object>> searchAlbums(String query) {
        String like = "%" + query.toLowerCase() + "%";
        String sql = """
            SELECT a.id, a.title, a.artist, a.release_year, a.genre,
                   a.color1, a.color2, a.album_art_url,
                   ROUND(AVG(r.stars), 1) AS avg_rating,
                   COUNT(r.id) AS rating_count
            FROM albums a
            LEFT JOIN ratings r ON r.album_id = a.id
            WHERE COALESCE(a.is_single, 0) = 0
              AND (LOWER(a.title) LIKE ? OR LOWER(a.artist) LIKE ? OR LOWER(a.genre) LIKE ?)
            GROUP BY a.id
            ORDER BY a.title
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> album = new HashMap<>();
                album.put("id",          rs.getLong("id"));
                album.put("title",       rs.getString("title"));
                album.put("artist",      rs.getString("artist"));
                album.put("releaseYear", rs.getInt("release_year"));
                album.put("genre",       rs.getString("genre"));
                album.put("color1",      rs.getString("color1"));
                album.put("color2",      rs.getString("color2"));
                album.put("albumArtUrl", rs.getString("album_art_url"));
                album.put("avgRating",   rs.getObject("avg_rating"));
                album.put("ratingCount", rs.getLong("rating_count"));
                results.add(album);
            }
        } catch (SQLException e) {
            System.out.println("Error searching: " + e.getMessage());
        }
        return results;
    }

    // Search songs by title, album title, artist, or genre
    List<Map<String, Object>> searchSongs(String query) {
        String like = "%" + query.toLowerCase() + "%";
        String sql = """
            SELECT s.id, s.title, s.track_number, s.duration_seconds, s.lyrics,
                   a.id AS album_id, a.title AS album_title, a.artist, a.release_year, a.genre,
                   a.color1, a.color2, a.album_art_url
            FROM songs s
            JOIN albums a ON a.id = s.album_id
            WHERE LOWER(s.title) LIKE ?
               OR LOWER(a.title) LIKE ?
               OR LOWER(a.artist) LIKE ?
               OR LOWER(a.genre) LIKE ?
            ORDER BY a.title, s.track_number
            """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, like);
            stmt.setString(2, like);
            stmt.setString(3, like);
            stmt.setString(4, like);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("id", rs.getLong("id"));
                song.put("title", rs.getString("title"));
                song.put("trackNumber", rs.getInt("track_number"));
                song.put("durationSeconds", rs.getInt("duration_seconds"));
                song.put("lyrics", rs.getString("lyrics"));
                song.put("albumId", rs.getLong("album_id"));
                song.put("albumTitle", rs.getString("album_title"));
                song.put("artist", rs.getString("artist"));
                song.put("releaseYear", rs.getInt("release_year"));
                song.put("genre", rs.getString("genre"));
                song.put("color1", rs.getString("color1"));
                song.put("color2", rs.getString("color2"));
                song.put("albumArtUrl", rs.getString("album_art_url"));
                results.add(song);
            }
        } catch (SQLException e) {
            System.out.println("Error searching songs: " + e.getMessage());
        }
        return results;
    }

    private Long findAlbumIdByIdentity(String title, String artist) {
        String sql = "SELECT id FROM albums WHERE LOWER(title) = LOWER(?) AND LOWER(artist) = LOWER(?) LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, title);
            stmt.setString(2, artist);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("id");
        } catch (SQLException e) {
            System.out.println("Error checking existing album: " + e.getMessage());
        }
        return null;
    }

    long createAlbum(String title,
                     String artist,
                     int releaseYear,
                     String genre,
                     String description,
                     String color1,
                     String color2,
                     boolean isSingle) {
        Long existingId = findAlbumIdByIdentity(title, artist);
        if (existingId != null) return existingId;

        String sql = "INSERT INTO albums (title, artist, release_year, genre, description, color1, color2, is_single) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, title);
            stmt.setString(2, artist);
            stmt.setInt(3, releaseYear);
            stmt.setString(4, genre);
            stmt.setString(5, description);
            stmt.setString(6, color1);
            stmt.setString(7, color2);
            stmt.setInt(8, isSingle ? 1 : 0);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
            throw new RuntimeException("Album insert succeeded but no ID returned");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create album: " + e.getMessage());
        }
    }

    long createAlbum(String title,
                     String artist,
                     int releaseYear,
                     String genre,
                     String description,
                     String color1,
                     String color2) {
        return createAlbum(title, artist, releaseYear, genre, description, color1, color2, false);
    }

    long createSong(long albumId, String title, int trackNumber, int durationSeconds) {
        String sql = "INSERT INTO songs (album_id, title, track_number, duration_seconds, lyrics) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, albumId);
            stmt.setString(2, title);
            stmt.setInt(3, trackNumber);
            stmt.setInt(4, durationSeconds);
            stmt.setString(5, buildFallbackLyrics(title));
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
            throw new RuntimeException("Song insert succeeded but no ID returned");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create song: " + e.getMessage());
        }
    }

    // ------- RATING METHODS -------

    // Save or update a rating (if user already rated this album, update it)
    void saveRating(long userId, long albumId, int stars) {
        String sql = """
            INSERT INTO ratings (user_id, album_id, stars, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id, album_id) DO UPDATE SET
                stars = excluded.stars,
                updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, albumId);
            stmt.setInt(3, stars);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not save rating: " + e.getMessage());
        }
    }

    // Get a user's rating for a specific album (returns 0 if not rated)
    int getUserRating(long userId, long albumId) {
        String sql = "SELECT stars FROM ratings WHERE user_id = ? AND album_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, albumId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("stars");
        } catch (SQLException e) {
            System.out.println("Error getting rating: " + e.getMessage());
        }
        return 0;
    }

    // Get all ratings a user has given (for their profile page)
    List<Map<String, Object>> getRatingsByUser(long userId) {
        String sql = """
            SELECT r.stars, r.updated_at, a.id AS album_id, a.title, a.artist, a.color1, a.color2
            FROM ratings r
            JOIN albums a ON a.id = r.album_id
            WHERE r.user_id = ?
            ORDER BY COALESCE(r.updated_at, '') DESC, r.id DESC
            """;
        List<Map<String, Object>> ratings = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> rating = new HashMap<>();
                rating.put("stars",   rs.getInt("stars"));
                rating.put("albumId", rs.getLong("album_id"));
                rating.put("title",   rs.getString("title"));
                rating.put("artist",  rs.getString("artist"));
                rating.put("color1",  rs.getString("color1"));
                rating.put("color2",  rs.getString("color2"));
                rating.put("updatedAt", rs.getString("updated_at"));
                ratings.add(rating);
            }
        } catch (SQLException e) {
            System.out.println("Error getting user ratings: " + e.getMessage());
        }
        return ratings;
    }

    List<Map<String, Object>> getRecentHistory(long userId, int limit) {
        String sql = """
            SELECT r.stars, r.updated_at, a.id AS album_id, a.title, a.artist, a.color1, a.color2
            FROM ratings r
            JOIN albums a ON a.id = r.album_id
            WHERE r.user_id = ?
            ORDER BY COALESCE(r.updated_at, '') DESC, r.id DESC
            LIMIT ?
            """;
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setInt(2, Math.max(1, limit));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("albumId", rs.getLong("album_id"));
                item.put("title", rs.getString("title"));
                item.put("artist", rs.getString("artist"));
                item.put("color1", rs.getString("color1"));
                item.put("color2", rs.getString("color2"));
                item.put("stars", rs.getInt("stars"));
                item.put("updatedAt", rs.getString("updated_at"));
                history.add(item);
            }
        } catch (SQLException e) {
            System.out.println("Error getting account history: " + e.getMessage());
        }
        return history;
    }

    private String buildFallbackLyrics(String title) {
        return """
            We are still collecting official lyrics for "%s".
            This shelf entry was user-added, so only metadata is available for now.

            Tip: open Admin tools to edit song records and add full lyrics later.
            """.formatted(title);
    }
}

// ============================================================
// JWT HELPER
// Creates and reads login tokens.
// You don't need to change this.
// ============================================================

@Component
class JwtHelper {

    private static final String SECRET = "ElgoonersSecretKey2024ForJWTSigning!!";
    private static final long   ONE_DAY_MS = 86400000L;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    // Create a token for a logged-in user
    String createToken(String username) {
        return Jwts.builder()
            .setSubject(username)
            .setIssuedAt(new java.util.Date())
            .setExpiration(new java.util.Date(System.currentTimeMillis() + ONE_DAY_MS))
            .signWith(getKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    // Read the username from a token (returns null if invalid)
    String getUsernameFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        } catch (Exception e) {
            return null; // token was invalid or expired
        }
    }
}

// ============================================================
// JWT FILTER
// Runs on every request to check if the user is logged in.
// You don't need to change this.
// ============================================================

@Component
class JwtFilter extends OncePerRequestFilter {

    @Autowired JwtHelper jwtHelper;
    @Autowired UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Check the Authorization header for a token
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token    = header.substring(7); // remove "Bearer " prefix
            String username = jwtHelper.getUsernameFromToken(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (UsernameNotFoundException | DisabledException ignored) {
                    // Invalid, deleted, or disabled users should continue unauthenticated.
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}

// Loads user details from the database for Spring Security
@Service
class MyUserDetailsService implements UserDetailsService {

    @Autowired Database db;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Map<String, Object> user = db.findUser(username);
        if (user == null) throw new UsernameNotFoundException("User not found: " + username);
        if (user.get("deletedAt") != null) throw new UsernameNotFoundException("User deleted: " + username);
        if (Boolean.FALSE.equals(user.get("isActive"))) throw new DisabledException("User disabled: " + username);
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");
        if (Boolean.TRUE.equals(user.get("isAdmin"))) roles.add("ROLE_ADMIN");
        return User.withUsername(username)
            .password((String) user.get("password"))
            .authorities(roles.toArray(new String[0]))
            .build();
    }
}

// ============================================================
// SECURITY CONFIG
// Controls which endpoints need login and which are public.
// ============================================================

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Autowired JwtFilter jwtFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfig()))
            .csrf(csrf -> csrf.disable()) // disabled because we use JWT instead
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // These endpoints anyone can access (no login needed)
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/albums/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/songs").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/search/add").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/profile/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/lookup").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // Everything else requires the user to be logged in
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Allow requests from the React app on port 3000
    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

// ============================================================
// AUTH CONTROLLER
// Handles signup and login
//
// POST /api/auth/signup  { "username": "bob", "password": "secret" }
// POST /api/auth/login   { "username": "bob", "password": "secret" }
// Both return a token + user info
// ============================================================

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @Autowired Database db;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthenticationManager authManager;
    @Autowired JwtHelper jwtHelper;

    private static final String ADMIN_PROMOTION_CODE = "12345";

    // A few colours to randomly assign to new users as their avatar colour
    private static final List<String> COLORS = List.of(
        "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4",
        "#F7DC6F", "#DDA0DD", "#85C1E9", "#82E0AA"
    );

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // Basic validation
        if (username == null || username.length() < 3)
            return ResponseEntity.badRequest().body(Map.of("error", "Username must be at least 3 characters"));
        if (password == null || password.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        if (db.usernameExists(username))
            return ResponseEntity.badRequest().body(Map.of("error", "Username is already taken"));

        // Pick a random avatar colour
        String color = COLORS.get(new Random().nextInt(COLORS.size()));

        // Hash the password before saving (never store plain text passwords!)
        String hashed = passwordEncoder.encode(password);
        long userId = db.createUser(username, hashed, color);

        String token = jwtHelper.createToken(username);
        return ResponseEntity.ok(Map.of(
            "token",       token,
            "userId",      userId,
            "username",    username,
            "avatarColor", color,
            "isAdmin",     false,
            "displayName", username
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // Let Spring Security check the username and password
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (DisabledException e) {
            return ResponseEntity.status(403).body(Map.of("error", "Account is disabled"));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Wrong username or password"));
        }

        Map<String, Object> user = db.findUser(username);
        String token = jwtHelper.createToken(username);
        return ResponseEntity.ok(Map.of(
            "token",       token,
            "userId",      user.get("id"),
            "username",    username,
            "avatarColor", user.getOrDefault("avatarColor", "#FF6B6B"),
            "isAdmin",     user.getOrDefault("isAdmin", false),
            "displayName", user.getOrDefault("displayName", username),
            "createdAt",   user.get("createdAt")
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> user = db.findUser(principal.getUsername());
        if (user == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return ResponseEntity.ok(Map.of(
            "userId", user.get("id"),
            "username", user.get("username"),
            "displayName", user.get("displayName"),
            "avatarColor", user.get("avatarColor"),
            "isAdmin", user.get("isAdmin"),
            "createdAt", user.get("createdAt"),
            "bio", user.get("bio")
        ));
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promote(@AuthenticationPrincipal UserDetails principal,
                                     @RequestBody Map<String, String> body) {
        String code = String.valueOf(body.getOrDefault("code", "")).trim();
        if (!ADMIN_PROMOTION_CODE.equals(code)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid admin code"));
        }

        Map<String, Object> user = db.findUser(principal.getUsername());
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));

        boolean changed = db.promoteUser(((Number) user.get("id")).longValue());
        if (!changed) return ResponseEntity.status(500).body(Map.of("error", "Could not promote user"));

        return ResponseEntity.ok(Map.of("message", "Admin access granted", "isAdmin", true));
    }
}

// ============================================================
// ALBUM CONTROLLER
// Returns albums and handles search
//
// GET /api/albums          - all albums
// GET /api/albums/{id}     - one album + its songs
// GET /api/search?q=...    - search
// ============================================================

@RestController
@RequestMapping("/api")
class AlbumController {

    @Autowired Database db;

    @GetMapping("/albums")
    public List<Map<String, Object>> getAllAlbums() {
        return db.getAllAlbums();
    }

    @GetMapping("/songs")
    public List<Map<String, Object>> getAllSongs() {
        return db.getAllSongs();
    }

    @GetMapping("/albums/{id}")
    public ResponseEntity<?> getOneAlbum(@PathVariable long id) {
        Map<String, Object> album = db.getAlbumById(id);
        if (album == null) {
            return ResponseEntity.notFound().build();
        }
        // Add the song list to the album response
        album.put("songs", db.getSongsForAlbum(id));
        return ResponseEntity.ok(album);
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) {
            return Map.of(
                "albums", List.of(),
                "songs", List.of(),
                "meta", Map.of("albumCount", 0, "songCount", 0)
            );
        }
        List<Map<String, Object>> albums = db.searchAlbums(q);
        List<Map<String, Object>> songs = db.searchSongs(q);
        return Map.of(
            "albums", albums,
            "songs", songs,
            "meta", Map.of("albumCount", albums.size(), "songCount", songs.size())
        );
    }

    @PostMapping("/search/add")
    public ResponseEntity<?> addMissingSearchItem(@RequestBody Map<String, Object> body) {
        String type = String.valueOf(body.getOrDefault("type", "album")).trim().toLowerCase();
        String title = String.valueOf(body.getOrDefault("title", "")).trim();
        String artist = String.valueOf(body.getOrDefault("artist", "Unknown Artist")).trim();

        if (title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title is required"));
        }

        int releaseYear = 2026;
        try {
            Object raw = body.get("releaseYear");
            if (raw != null && !raw.toString().isBlank()) {
                releaseYear = Integer.parseInt(raw.toString());
            }
        } catch (Exception ignored) {}

        String genre = String.valueOf(body.getOrDefault("genre", "Unknown")).trim();
        String description = String.valueOf(body.getOrDefault("description", "Added from search when no result was found.")).trim();
        String color1 = String.valueOf(body.getOrDefault("color1", "#5f5aa2")).trim();
        String color2 = String.valueOf(body.getOrDefault("color2", "#a3bffa")).trim();

        if (type.equals("song")) {
            String cleanArtist = artist.isBlank() ? "Unknown Artist" : artist;
            String albumTitle = String.valueOf(body.getOrDefault("albumTitle", "")).trim();
            if (albumTitle.isBlank()) {
                albumTitle = title + " (Single)";
            }
            long albumId = db.createAlbum(
                albumTitle,
                cleanArtist,
                releaseYear,
                genre,
                "Auto-created while adding a missing song.",
                color1,
                color2,
                true
            );
            int trackNumber = 1;
            int durationSeconds = 180;
            try {
                Object rawTrack = body.get("trackNumber");
                if (rawTrack != null && !rawTrack.toString().isBlank()) trackNumber = Integer.parseInt(rawTrack.toString());
            } catch (Exception ignored) {}
            try {
                Object rawDuration = body.get("durationSeconds");
                if (rawDuration != null && !rawDuration.toString().isBlank()) durationSeconds = Integer.parseInt(rawDuration.toString());
            } catch (Exception ignored) {}

            long songId = db.createSong(albumId, title, trackNumber, durationSeconds);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Song added",
                "type", "song",
                "songId", songId,
                "albumId", albumId
            ));
        }

        long albumId = db.createAlbum(
            title,
            artist.isBlank() ? "Unknown Artist" : artist,
            releaseYear,
            genre,
            description,
            color1,
            color2
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message", "Album added",
            "type", "album",
            "albumId", albumId
        ));
    }

    @GetMapping("/users/lookup")
    public Map<String, Object> lookupUsers(@RequestParam(defaultValue = "") String q) {
        if (q.isBlank()) return Map.of("users", List.of(), "count", 0);
        List<Map<String, Object>> users = db.lookupUsers(q);
        return Map.of("users", users, "count", users.size());
    }
}

// ============================================================
// RATING CONTROLLER
// Lets users rate albums 1-5 stars (login required)
//
// POST /api/ratings/{albumId}  { "stars": 4 }
// GET  /api/ratings/{albumId}  - get your current rating
// ============================================================

@RestController
@RequestMapping("/api/ratings")
class RatingController {

    @Autowired Database db;

    @PostMapping("/{albumId}")
    public ResponseEntity<?> rateAlbum(@PathVariable long albumId,
                                       @RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal UserDetails principal) {
        // Parse and validate the star value
        int stars;
        try {
            stars = Integer.parseInt(body.get("stars").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "stars must be a number 1-5"));
        }
        if (stars < 1 || stars > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "stars must be between 1 and 5"));
        }

        // Get the logged-in user's ID
        Map<String, Object> user = db.findUser(principal.getUsername());
        long userId = ((Number) user.get("id")).longValue();

        db.saveRating(userId, albumId, stars);

        // Return the updated album stats so the UI can refresh
        Map<String, Object> album = db.getAlbumById(albumId);
        return ResponseEntity.ok(Map.of(
            "message",     "Rating saved!",
            "stars",       stars,
            "avgRating",   album.getOrDefault("avgRating", 0),
            "ratingCount", album.getOrDefault("ratingCount", 0)
        ));
    }

    @GetMapping("/{albumId}")
    public ResponseEntity<?> getMyRating(@PathVariable long albumId,
                                         @AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> user = db.findUser(principal.getUsername());
        long userId = ((Number) user.get("id")).longValue();
        int stars = db.getUserRating(userId, albumId);
        return ResponseEntity.ok(Map.of("stars", stars));
    }
}

// ============================================================
// PROFILE CONTROLLER
// Returns a user's public profile and their ratings
//
// GET /api/profile/{username}
// ============================================================

@RestController
@RequestMapping("/api/profile")
class ProfileController {

    @Autowired Database db;

    @GetMapping("/{username}")
    public ResponseEntity<?> getProfile(@PathVariable String username) {
        Map<String, Object> user = db.findUser(username);
        if (user == null || user.get("deletedAt") != null || Boolean.FALSE.equals(user.get("isActive"))) {
            return ResponseEntity.notFound().build();
        }

        long userId = ((Number) user.get("id")).longValue();
        List<Map<String, Object>> ratings = db.getRatingsByUser(userId);

        // Build the profile response (don't include the password!)
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username",    user.get("username"));
        profile.put("displayName", user.get("displayName"));
        profile.put("avatarColor", user.get("avatarColor"));
        profile.put("bio", user.getOrDefault("bio", ""));
        profile.put("createdAt", user.get("createdAt"));
        profile.put("isAdmin", user.getOrDefault("isAdmin", false));
        profile.put("ratingCount", ratings.size());
        profile.put("ratings",     ratings);

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/me")
    public ResponseEntity<?> myAccount(@AuthenticationPrincipal UserDetails principal,
                                       @RequestParam(defaultValue = "8") int historyLimit) {
        Map<String, Object> user = db.findUser(principal.getUsername());
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        long userId = ((Number) user.get("id")).longValue();
        List<Map<String, Object>> history = db.getRecentHistory(userId, Math.min(Math.max(historyLimit, 1), 25));
        return ResponseEntity.ok(Map.of(
            "userId", user.get("id"),
            "username", user.get("username"),
            "displayName", user.get("displayName"),
            "avatarColor", user.get("avatarColor"),
            "bio", user.getOrDefault("bio", ""),
            "createdAt", user.get("createdAt"),
            "isAdmin", user.getOrDefault("isAdmin", false),
            "recentHistory", history
        ));
    }
}

@RestController
@RequestMapping("/api/admin")
class AdminController {

    @Autowired Database db;
    @Autowired PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public Map<String, Object> getUsers() {
        List<Map<String, Object>> users = db.getAllUsersForAdmin();
        return Map.of("users", users, "count", users.size());
    }

    @PutMapping("/users/{username}/role")
    public ResponseEntity<?> setRole(@PathVariable String username,
                                     @RequestBody Map<String, Object> body,
                                     @AuthenticationPrincipal UserDetails principal) {
        boolean isAdmin = Boolean.parseBoolean(String.valueOf(body.getOrDefault("isAdmin", false)));
        Map<String, Object> target = db.findUser(username);
        if (target == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        if (target.get("deletedAt") != null) return ResponseEntity.status(400).body(Map.of("error", "Cannot modify a deleted user"));

        if (principal.getUsername().equals(username) && !isAdmin) {
            return ResponseEntity.status(400).body(Map.of("error", "You cannot remove your own admin role"));
        }
        if (Boolean.TRUE.equals(target.get("isAdmin")) && !isAdmin && db.countActiveAdmins() <= 1) {
            return ResponseEntity.status(400).body(Map.of("error", "At least one active admin is required"));
        }

        boolean updated = db.setUserAdminByUsername(username, isAdmin);
        if (!updated) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        return ResponseEntity.ok(Map.of("message", "Role updated", "username", username, "isAdmin", isAdmin));
    }

    @PutMapping("/users/{username}/status")
    public ResponseEntity<?> setStatus(@PathVariable String username,
                                       @RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal UserDetails principal) {
        boolean isActive = Boolean.parseBoolean(String.valueOf(body.getOrDefault("isActive", true)));
        Map<String, Object> target = db.findUser(username);
        if (target == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        if (target.get("deletedAt") != null) return ResponseEntity.status(400).body(Map.of("error", "Cannot enable or disable a deleted user"));

        if (principal.getUsername().equals(username) && !isActive) {
            return ResponseEntity.status(400).body(Map.of("error", "You cannot disable your own account"));
        }
        if (Boolean.TRUE.equals(target.get("isAdmin")) && !isActive && db.countActiveAdmins() <= 1) {
            return ResponseEntity.status(400).body(Map.of("error", "At least one active admin is required"));
        }

        boolean updated = db.setUserActiveByUsername(username, isActive);
        if (!updated) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        return ResponseEntity.ok(Map.of("message", isActive ? "User enabled" : "User disabled", "username", username, "isActive", isActive));
    }

    @PostMapping("/users/{username}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String username,
                                           @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> target = db.findUser(username);
        if (target == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        if (target.get("deletedAt") != null) return ResponseEntity.status(400).body(Map.of("error", "Cannot reset password for a deleted user"));

        String requested = body == null ? "" : String.valueOf(body.getOrDefault("newPassword", "")).trim();
        String nextPassword = requested.isBlank() ? generateTempPassword() : requested;
        if (nextPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        boolean updated = db.resetUserPasswordByUsername(username, passwordEncoder.encode(nextPassword));
        if (!updated) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        return ResponseEntity.ok(Map.of(
            "message", "Password reset",
            "username", username,
            "temporaryPassword", requested.isBlank() ? nextPassword : "(custom value provided)"
        ));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        String username = String.valueOf(body.getOrDefault("username", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", "")).trim();
        String avatarColor = String.valueOf(body.getOrDefault("avatarColor", "#45B7D1")).trim();
        String displayName = String.valueOf(body.getOrDefault("displayName", username)).trim();
        String bio = String.valueOf(body.getOrDefault("bio", "")).trim();
        boolean isAdmin = Boolean.parseBoolean(String.valueOf(body.getOrDefault("isAdmin", false)));

        if (username.length() < 3) return ResponseEntity.badRequest().body(Map.of("error", "Username must be at least 3 characters"));
        if (password.length() < 6) return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        if (db.usernameExists(username)) return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));

        long userId = db.createUserByAdmin(username, passwordEncoder.encode(password), avatarColor, displayName, bio, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", "User created", "userId", userId, "username", username));
    }

    @DeleteMapping("/users/{username}")
    public ResponseEntity<?> deleteUser(@PathVariable String username,
                                        @AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> target = db.findUser(username);
        if (target == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        if (target.get("deletedAt") != null) return ResponseEntity.status(400).body(Map.of("error", "User already deleted"));

        if (principal.getUsername().equals(username)) {
            return ResponseEntity.status(400).body(Map.of("error", "You cannot delete your own account"));
        }
        if (Boolean.TRUE.equals(target.get("isAdmin")) && db.countActiveAdmins() <= 1) {
            return ResponseEntity.status(400).body(Map.of("error", "At least one active admin is required"));
        }

        boolean updated = db.softDeleteUserByUsername(username);
        if (!updated) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        return ResponseEntity.ok(Map.of("message", "User soft deleted", "username", username));
    }

    private String generateTempPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        Random random = new Random();
        StringBuilder out = new StringBuilder("Tmp-");
        for (int i = 0; i < 8; i++) {
            out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return out.toString();
    }
}
