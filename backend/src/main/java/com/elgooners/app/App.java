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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

    private static final String PROJECT_ROOT = System.getProperty("user.dir")
            .replace("\\backend", "")
            .replace("/backend", "");

    @Value("${recordshelf.db.path:}")
    private String configuredDbPath;

    @Value("${recordshelf.catalog.dir:}")
    private String configuredCatalogDir;

    private final BCryptPasswordEncoder seedPasswordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    void init() {
        ensureSchema();
        seedUsers();
        importCatalogFromCsv();
        ensurePrimaryAlbumsHaveSongs();
    }

    private void importCatalogFromCsv() {
        Path albumsCsv = resolveCatalogPath("albums.csv");
        Path songsCsv = resolveCatalogPath("songs.csv");

        if (!Files.exists(albumsCsv) || !Files.exists(songsCsv)) {
            System.out.println("CSV import skipped (catalog files not found): " + albumsCsv + " and " + songsCsv);
            return;
        }

        int albumsInserted = 0;
        int songsInserted = 0;
        int albumRowsSeen = 0;
        int songRowsSeen = 0;

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, Long> albumIdsByKey = loadAlbumIdentityMap(conn);

                try (java.io.BufferedReader reader = Files.newBufferedReader(albumsCsv)) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) {
                            first = false;
                            continue;
                        }
                        if (line.isBlank()) continue;
                        albumRowsSeen++;

                        List<String> parts = parseCsvLine(line);
                        if (parts.size() < 2) continue;

                        String artist = csvCell(parts, 0);
                        String albumTitle = csvCell(parts, 1);
                        int releaseYear = normalizeReleaseYear(parseIntOrDefault(csvCell(parts, 2), 0));
                        String albumArtUrl = csvCell(parts, 4);

                        if (artist.isBlank() || albumTitle.isBlank()) continue;

                        String albumKey = normalizeKey(albumTitle) + "|" + normalizeKey(artist);
                        Long albumId = albumIdsByKey.get(albumKey);
                        if (albumId == null) {
                            albumId = insertImportedAlbum(conn, albumTitle, artist, releaseYear, albumArtUrl);
                            albumIdsByKey.put(albumKey, albumId);
                            albumsInserted++;
                        } else {
                            updateImportedAlbumMetadata(conn, albumId, releaseYear, albumArtUrl);
                        }
                    }
                }

                String songInsertSql = """
                    INSERT OR IGNORE INTO songs (album_id, title, track_number, duration_seconds, lyrics)
                    VALUES (?, ?, ?, ?, ?)
                    """;

                try (java.io.BufferedReader reader = Files.newBufferedReader(songsCsv);
                     PreparedStatement insertSong = conn.prepareStatement(songInsertSql)) {
                    String line;
                    boolean first = true;
                    while ((line = reader.readLine()) != null) {
                        if (first) {
                            first = false;
                            continue;
                        }
                        if (line.isBlank()) continue;
                        songRowsSeen++;

                        List<String> parts = parseCsvLine(line);
                        if (parts.size() < 6) continue;

                        String artist = csvCell(parts, 0);
                        String albumTitle = csvCell(parts, 1);
                        int releaseYear = normalizeReleaseYear(parseIntOrDefault(csvCell(parts, 2), 0));
                        int trackNumber = Math.max(0, parseIntOrDefault(csvCell(parts, 3), 0));
                        String songTitle = csvCell(parts, 4);
                        int durationSeconds = Math.max(0, parseIntOrDefault(csvCell(parts, 5), 0));

                        if (artist.isBlank() || albumTitle.isBlank() || songTitle.isBlank()) continue;

                        String albumKey = normalizeKey(albumTitle) + "|" + normalizeKey(artist);
                        Long albumId = albumIdsByKey.get(albumKey);
                        if (albumId == null) {
                            albumId = insertImportedAlbum(conn, albumTitle, artist, releaseYear, "");
                            albumIdsByKey.put(albumKey, albumId);
                            albumsInserted++;
                        }

                        insertSong.setLong(1, albumId);
                        insertSong.setString(2, songTitle);
                        insertSong.setInt(3, trackNumber);
                        insertSong.setInt(4, durationSeconds);
                        insertSong.setString(5, "");
                        songsInserted += insertSong.executeUpdate();
                    }
                }

                conn.commit();
                System.out.println("CSV import complete: scanned " + albumRowsSeen + " albums and " + songRowsSeen + " songs; inserted " + albumsInserted + " albums and " + songsInserted + " songs.");
            } catch (Exception e) {
                conn.rollback();
                System.out.println("CSV import failed; rolled back transaction: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Error during CSV import setup: " + e.getMessage());
        }
    }

    private Path resolveCatalogPath(String fileName) {
        if (configuredCatalogDir != null && !configuredCatalogDir.isBlank()) {
            Path catalogDir = Paths.get(configuredCatalogDir);
            if (!catalogDir.isAbsolute()) {
                catalogDir = Paths.get(PROJECT_ROOT).resolve(catalogDir);
            }
            return catalogDir.resolve(fileName).toAbsolutePath().normalize();
        }
        return Paths.get(PROJECT_ROOT, "database", fileName).toAbsolutePath().normalize();
    }

    private String resolveDbPath() {
        if (configuredDbPath != null && !configuredDbPath.isBlank()) {
            Path dbPath = Paths.get(configuredDbPath);
            if (!dbPath.isAbsolute()) {
                dbPath = Paths.get(PROJECT_ROOT).resolve(dbPath);
            }
            return dbPath.toAbsolutePath().normalize().toString();
        }
        return Paths.get(PROJECT_ROOT, "database", "recordshelf.db").toAbsolutePath().normalize().toString();
    }

    private Map<String, Long> loadAlbumIdentityMap(Connection conn) throws SQLException {
        Map<String, Long> map = new HashMap<>();
        String sql = "SELECT id, title, artist FROM albums";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String key = normalizeKey(rs.getString("title")) + "|" + normalizeKey(rs.getString("artist"));
                map.put(key, rs.getLong("id"));
            }
        }
        return map;
    }

    private long insertImportedAlbum(Connection conn,
                                     String title,
                                     String artist,
                                     int releaseYear,
                                     String albumArtUrl) throws SQLException {
        String sql = """
            INSERT INTO albums (title, artist, release_year, genre, description, color1, color2, album_art_url, is_single)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, title);
            stmt.setString(2, artist);
            stmt.setInt(3, normalizeReleaseYear(releaseYear));
            stmt.setString(4, "Imported");
            stmt.setString(5, "Imported from RecordShelfDemo catalog");
            stmt.setString(6, "#333333");
            stmt.setString(7, "#555555");
            stmt.setString(8, albumArtUrl == null ? "" : albumArtUrl);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
        }
        throw new SQLException("Album insert succeeded but no ID was generated");
    }

    private void updateImportedAlbumMetadata(Connection conn,
                                             long albumId,
                                             int releaseYear,
                                             String albumArtUrl) throws SQLException {
        String sql = """
            UPDATE albums
            SET album_art_url = CASE
                    WHEN COALESCE(NULLIF(album_art_url, ''), '') = '' AND ? <> '' THEN ?
                    ELSE album_art_url
                END,
                release_year = CASE
                    WHEN COALESCE(release_year, 0) = 0 AND ? > 0 THEN ?
                    ELSE release_year
                END
            WHERE id = ?
            """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String safeArt = albumArtUrl == null ? "" : albumArtUrl;
            stmt.setString(1, safeArt);
            stmt.setString(2, safeArt);
            int safeReleaseYear = normalizeReleaseYear(releaseYear);
            stmt.setInt(3, safeReleaseYear);
            stmt.setInt(4, safeReleaseYear);
            stmt.setLong(5, albumId);
            stmt.executeUpdate();
        }
    }

    private String csvCell(List<String> cells, int index) {
        if (index < 0 || index >= cells.size()) return "";
        String value = cells.get(index);
        return value == null ? "" : value.trim();
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    int normalizeReleaseYear(int year) {
        int maxYear = Year.now().getValue() + 1;
        if (year < 1880 || year > maxYear) return 0;
        return year;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCommentTargetType(String targetType) {
        return targetType == null ? "" : targetType.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>();
        if (line == null) return values;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        return values;
    }

    private void ensureSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password TEXT NOT NULL,
                    avatar_color TEXT DEFAULT '#FF6B6B',
                    is_admin INTEGER NOT NULL DEFAULT 0,
                    bio TEXT DEFAULT '',
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    display_name TEXT,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    disabled_at TEXT,
                    deleted_at TEXT,
                    password_reset_at TEXT
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS albums (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    release_year INTEGER,
                    genre TEXT,
                    description TEXT,
                    color1 TEXT DEFAULT '#333333',
                    color2 TEXT DEFAULT '#555555',
                    album_art_url TEXT DEFAULT '',
                    is_single INTEGER NOT NULL DEFAULT 0
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS songs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    album_id INTEGER,
                    title TEXT NOT NULL,
                    track_number INTEGER,
                    duration_seconds INTEGER,
                    lyrics TEXT DEFAULT ''
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS ratings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    album_id INTEGER NOT NULL,
                    stars INTEGER NOT NULL,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, album_id)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS song_ratings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    song_id INTEGER NOT NULL,
                    stars INTEGER NOT NULL,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, song_id)
                )
                """);
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
            addColumnIfMissing(conn, "song_ratings", "updated_at", "TEXT");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playlists (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    category TEXT DEFAULT 'Custom',
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS playlist_songs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    playlist_id INTEGER NOT NULL,
                    song_id INTEGER NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(playlist_id, song_id)
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS comments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    target_type TEXT NOT NULL,
                    target_id INTEGER NOT NULL,
                    text TEXT NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
                """);
            // Migrate older playlist schemas that were created before these columns existed.
            addColumnIfMissing(conn, "playlists", "description", "TEXT DEFAULT ''");
            addColumnIfMissing(conn, "playlists", "category", "TEXT DEFAULT 'Custom'");
            addColumnIfMissing(conn, "playlists", "created_at", "TEXT DEFAULT CURRENT_TIMESTAMP");
            addColumnIfMissing(conn, "playlist_songs", "created_at", "TEXT DEFAULT CURRENT_TIMESTAMP");
            addColumnIfMissing(conn, "comments", "target_type", "TEXT");
            addColumnIfMissing(conn, "comments", "target_id", "INTEGER");
            addColumnIfMissing(conn, "comments", "text", "TEXT");
            addColumnIfMissing(conn, "comments", "created_at", "TEXT");
            addColumnIfMissing(conn, "comments", "updated_at", "TEXT");
            stmt.execute("UPDATE users SET created_at = COALESCE(NULLIF(created_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("UPDATE users SET display_name = COALESCE(NULLIF(display_name, ''), username)");
            stmt.execute("UPDATE users SET bio = COALESCE(bio, '')");
            stmt.execute("UPDATE users SET is_active = COALESCE(is_active, 1)");
            stmt.execute("UPDATE albums SET is_single = COALESCE(is_single, 0)");
            stmt.execute("UPDATE albums SET is_single = 1 WHERE LOWER(TRIM(title)) IN ('single', 'singles')");
            stmt.execute("UPDATE albums SET release_year = 0 WHERE release_year IS NULL OR release_year < 1880 OR release_year > " + (Year.now().getValue() + 1));
            stmt.execute("UPDATE ratings SET updated_at = COALESCE(NULLIF(updated_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("UPDATE song_ratings SET updated_at = COALESCE(NULLIF(updated_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("UPDATE comments SET created_at = COALESCE(NULLIF(created_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("UPDATE comments SET updated_at = COALESCE(NULLIF(updated_at, ''), CURRENT_TIMESTAMP)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_album_identity ON albums(title, artist)");
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_song_identity ON songs(album_id, track_number, title)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_song_ratings_song ON song_ratings(song_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_playlists_user ON playlists(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_playlist_songs_playlist ON playlist_songs(playlist_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_comments_target ON comments(target_type, target_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_comments_user ON comments(user_id)");
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
        String dbPath = resolveDbPath();
        java.io.File f = new java.io.File(dbPath);
        java.io.File parent = f.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        System.out.println("Looking for DB at: " + dbPath);
        System.out.println("File exists: " + f.exists());
        System.out.println("Absolute path: " + f.getAbsolutePath());
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
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
                album.put("releaseYear", normalizeReleaseYear(rs.getInt("release_year")));
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
                album.put("releaseYear", normalizeReleaseYear(rs.getInt("release_year")));
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
                song.put("releaseYear",     normalizeReleaseYear(rs.getInt("release_year")));
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
                album.put("releaseYear", normalizeReleaseYear(rs.getInt("release_year")));
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
                song.put("releaseYear", normalizeReleaseYear(rs.getInt("release_year")));
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
            stmt.setInt(3, normalizeReleaseYear(releaseYear));
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

    // ------- PLAYLIST METHODS -------

    private boolean playlistOwnedByUser(Connection conn, long playlistId, long userId) throws SQLException {
        String sql = "SELECT id FROM playlists WHERE id = ? AND user_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, playlistId);
            stmt.setLong(2, userId);
            return stmt.executeQuery().next();
        }
    }

    private boolean songExists(Connection conn, long songId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM songs WHERE id = ?")) {
            stmt.setLong(1, songId);
            return stmt.executeQuery().next();
        }
    }

    private boolean albumExists(Connection conn, long albumId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM albums WHERE id = ?")) {
            stmt.setLong(1, albumId);
            return stmt.executeQuery().next();
        }
    }

    private boolean userExists(Connection conn, long userId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
            stmt.setLong(1, userId);
            return stmt.executeQuery().next();
    void updateBio(long userId, String bio) {
        String sql = "UPDATE users SET bio = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bio);
            stmt.setLong(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update bio: " + e.getMessage());
        }
    }

    long createPlaylist(long userId, String name, String description, String category) {
        String sql = "INSERT INTO playlists (user_id, name, description, category) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, userId);
            stmt.setString(2, name);
            stmt.setString(3, description);
            stmt.setString(4, category);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
            throw new RuntimeException("Playlist insert succeeded but no ID returned");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create playlist: " + e.getMessage());
        }
    }

    List<Map<String, Object>> getPlaylistsByUser(long userId) {
        String sql = """
            SELECT p.id, p.name, p.description, p.category, p.created_at,
                   COUNT(ps.id) AS song_count
            FROM playlists p
            LEFT JOIN playlist_songs ps ON ps.playlist_id = p.id
            WHERE p.user_id = ?
            GROUP BY p.id
            ORDER BY p.id DESC
            """;
        List<Map<String, Object>> playlists = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> playlist = new HashMap<>();
                playlist.put("id", rs.getLong("id"));
                playlist.put("name", rs.getString("name"));
                playlist.put("description", rs.getString("description"));
                playlist.put("category", rs.getString("category"));
                playlist.put("createdAt", rs.getString("created_at"));
                playlist.put("songCount", rs.getLong("song_count"));
                playlists.add(playlist);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not get playlists: " + e.getMessage());
        }
        return playlists;
    }

    Map<String, Object> getPlaylistByIdPublic(long playlistId) {
        String playlistSql = """
            SELECT id, name, description, category, created_at
            FROM playlists
            WHERE id = ?
            """;
        String songsSql = """
            SELECT s.id, s.title, s.track_number, s.duration_seconds,
                   a.id AS album_id, a.title AS album_title, a.artist
            FROM playlist_songs ps
            JOIN songs s ON s.id = ps.song_id
            LEFT JOIN albums a ON a.id = s.album_id
            WHERE ps.playlist_id = ?
            ORDER BY ps.id DESC
            """;
        try (Connection conn = getConnection();
             PreparedStatement playlistStmt = conn.prepareStatement(playlistSql);
             PreparedStatement songsStmt = conn.prepareStatement(songsSql)) {
            playlistStmt.setLong(1, playlistId);
            ResultSet playlistRs = playlistStmt.executeQuery();
            if (!playlistRs.next()) return null;

            Map<String, Object> playlist = new HashMap<>();
            playlist.put("id", playlistRs.getLong("id"));
            playlist.put("name", playlistRs.getString("name"));
            playlist.put("description", playlistRs.getString("description"));
            playlist.put("category", playlistRs.getString("category"));
            playlist.put("createdAt", playlistRs.getString("created_at"));

            songsStmt.setLong(1, playlistId);
            ResultSet songsRs = songsStmt.executeQuery();
            List<Map<String, Object>> songs = new ArrayList<>();
            while (songsRs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("id", songsRs.getLong("id"));
                song.put("title", songsRs.getString("title"));
                song.put("trackNumber", songsRs.getInt("track_number"));
                song.put("durationSeconds", songsRs.getInt("duration_seconds"));
                song.put("albumId", songsRs.getLong("album_id"));
                song.put("albumTitle", songsRs.getString("album_title"));
                song.put("artist", songsRs.getString("artist"));
                songs.add(song);
            }
            playlist.put("songs", songs);
            playlist.put("songCount", songs.size());
            return playlist;
        } catch (SQLException e) {
            throw new RuntimeException("Could not get playlist: " + e.getMessage());
        }
    }

    Map<String, Object> getPlaylistByIdForUser(long playlistId, long userId) {
        String playlistSql = """
            SELECT id, name, description, category, created_at
            FROM playlists
            WHERE id = ? AND user_id = ?
            """;
        String songsSql = """
            SELECT s.id, s.title, s.track_number, s.duration_seconds,
                   a.id AS album_id, a.title AS album_title, a.artist
            FROM playlist_songs ps
            JOIN songs s ON s.id = ps.song_id
            LEFT JOIN albums a ON a.id = s.album_id
            WHERE ps.playlist_id = ?
            ORDER BY ps.id DESC
            """;

        try (Connection conn = getConnection();
             PreparedStatement playlistStmt = conn.prepareStatement(playlistSql);
             PreparedStatement songsStmt = conn.prepareStatement(songsSql)) {
            playlistStmt.setLong(1, playlistId);
            playlistStmt.setLong(2, userId);
            ResultSet playlistRs = playlistStmt.executeQuery();
            if (!playlistRs.next()) return null;

            Map<String, Object> playlist = new HashMap<>();
            playlist.put("id", playlistRs.getLong("id"));
            playlist.put("name", playlistRs.getString("name"));
            playlist.put("description", playlistRs.getString("description"));
            playlist.put("category", playlistRs.getString("category"));
            playlist.put("createdAt", playlistRs.getString("created_at"));

            songsStmt.setLong(1, playlistId);
            ResultSet songsRs = songsStmt.executeQuery();
            List<Map<String, Object>> songs = new ArrayList<>();
            while (songsRs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("id", songsRs.getLong("id"));
                song.put("title", songsRs.getString("title"));
                song.put("trackNumber", songsRs.getInt("track_number"));
                song.put("durationSeconds", songsRs.getInt("duration_seconds"));
                song.put("albumId", songsRs.getLong("album_id"));
                song.put("albumTitle", songsRs.getString("album_title"));
                song.put("artist", songsRs.getString("artist"));
                songs.add(song);
            }
            playlist.put("songs", songs);
            playlist.put("songCount", songs.size());
            return playlist;
        } catch (SQLException e) {
            throw new RuntimeException("Could not get playlist: " + e.getMessage());
        }
    }

    boolean updatePlaylist(long playlistId, long userId, String name, String description, String category) {
        String sql = """
            UPDATE playlists
            SET name = ?, description = ?, category = ?
            WHERE id = ? AND user_id = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setString(3, category);
            stmt.setLong(4, playlistId);
            stmt.setLong(5, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not update playlist: " + e.getMessage());
        }
    }

    boolean deletePlaylist(long playlistId, long userId) {
        String deleteSongsSql = "DELETE FROM playlist_songs WHERE playlist_id = ?";
        String deletePlaylistSql = "DELETE FROM playlists WHERE id = ? AND user_id = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteSongsStmt = conn.prepareStatement(deleteSongsSql);
                 PreparedStatement deletePlaylistStmt = conn.prepareStatement(deletePlaylistSql)) {
                deleteSongsStmt.setLong(1, playlistId);
                deleteSongsStmt.executeUpdate();

                deletePlaylistStmt.setLong(1, playlistId);
                deletePlaylistStmt.setLong(2, userId);
                int deleted = deletePlaylistStmt.executeUpdate();

                conn.commit();
                return deleted > 0;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not delete playlist: " + e.getMessage());
        }
    }

    boolean addSongToPlaylist(long playlistId, long userId, long songId) {
        String insertSql = "INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            if (!playlistOwnedByUser(conn, playlistId, userId) || !songExists(conn, songId)) return false;
            insertStmt.setLong(1, playlistId);
            insertStmt.setLong(2, songId);
            insertStmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException("Could not add song to playlist: " + e.getMessage());
        }
    }

    // Returns -1 if playlist ownership check fails, -2 if album does not exist, else inserted row count.
    int addAlbumSongsToPlaylist(long playlistId, long userId, long albumId) {
        String insertSql = """
            INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id)
            SELECT ?, s.id FROM songs s WHERE s.album_id = ?
            """;
        try (Connection conn = getConnection();
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            if (!playlistOwnedByUser(conn, playlistId, userId)) return -1;
            if (!albumExists(conn, albumId)) return -2;
            insertStmt.setLong(1, playlistId);
            insertStmt.setLong(2, albumId);
            return insertStmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not add album songs to playlist: " + e.getMessage());
        }
    }

    boolean removeSongFromPlaylist(long playlistId, long userId, long songId) {
        String sql = """
            DELETE FROM playlist_songs
            WHERE playlist_id = ? AND song_id = ?
              AND EXISTS (SELECT 1 FROM playlists p WHERE p.id = ? AND p.user_id = ?)
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, playlistId);
            stmt.setLong(2, songId);
            stmt.setLong(3, playlistId);
            stmt.setLong(4, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not remove song from playlist: " + e.getMessage());
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

    boolean songExists(long songId) {
        String sql = "SELECT 1 FROM songs WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, songId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.out.println("Error checking song: " + e.getMessage());
        }
        return false;
    }

    boolean albumExists(long albumId) {
        String sql = "SELECT 1 FROM albums WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, albumId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.out.println("Error checking album: " + e.getMessage());
        }
        return false;
    }

    boolean userExists(long userId) {
        String sql = "SELECT 1 FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            return stmt.executeQuery().next();
        } catch (SQLException e) {
            System.out.println("Error checking user: " + e.getMessage());
        }
        return false;
    }

    void saveSongRating(long userId, long songId, int stars) {
        String sql = """
            INSERT INTO song_ratings (user_id, song_id, stars, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id, song_id) DO UPDATE SET
                stars = excluded.stars,
                updated_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, songId);
            stmt.setInt(3, stars);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not save song rating: " + e.getMessage());
        }
    }

    int getUserSongRating(long userId, long songId) {
        String sql = "SELECT stars FROM song_ratings WHERE user_id = ? AND song_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, songId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("stars");
        } catch (SQLException e) {
            System.out.println("Error getting song rating: " + e.getMessage());
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

    // Get all song ratings a user has given, sorted by highest stars first
    List<Map<String, Object>> getSongRatingsByUser(long userId) {
        String sql = """
            SELECT sr.stars, sr.updated_at, s.id AS song_id, s.title, s.track_number,
                   a.title AS album_title, a.artist, a.color1, a.color2, a.album_art_url
            FROM song_ratings sr
            JOIN songs s ON s.id = sr.song_id
            JOIN albums a ON a.id = s.album_id
            WHERE sr.user_id = ?
            ORDER BY sr.stars DESC, COALESCE(sr.updated_at, '') DESC
            LIMIT 20
            """;
        List<Map<String, Object>> ratings = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> rating = new HashMap<>();
                rating.put("stars",       rs.getInt("stars"));
                rating.put("songId",      rs.getLong("song_id"));
                rating.put("title",       rs.getString("title"));
                rating.put("trackNumber", rs.getInt("track_number"));
                rating.put("albumTitle",  rs.getString("album_title"));
                rating.put("artist",      rs.getString("artist"));
                rating.put("color1",      rs.getString("color1"));
                rating.put("color2",      rs.getString("color2"));
                rating.put("albumArtUrl", rs.getString("album_art_url"));
                rating.put("updatedAt",   rs.getString("updated_at"));
                ratings.add(rating);
            }
        } catch (SQLException e) {
            System.out.println("Error getting user song ratings: " + e.getMessage());
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

    private boolean commentTargetExists(Connection conn, String targetType, long targetId) throws SQLException {
        return switch (normalizeCommentTargetType(targetType)) {
            case "album" -> albumExists(conn, targetId);
            case "song" -> songExists(conn, targetId);
            case "profile" -> userExists(conn, targetId);
            default -> false;
        };
    }

    private Map<String, Object> mapCommentRow(ResultSet rs) throws SQLException {
        Map<String, Object> comment = new LinkedHashMap<>();
        comment.put("id", rs.getLong("id"));
        comment.put("userId", rs.getLong("user_id"));
        comment.put("username", rs.getString("username"));
        comment.put("displayName", rs.getString("display_name"));
        comment.put("avatarColor", rs.getString("avatar_color"));
        comment.put("targetType", rs.getString("target_type"));
        comment.put("targetId", rs.getLong("target_id"));
        comment.put("text", rs.getString("text"));
        comment.put("createdAt", rs.getString("created_at"));
        comment.put("updatedAt", rs.getString("updated_at"));
        return comment;
    }

    long createComment(long userId, String targetType, long targetId, String text) {
        String normalizedType = normalizeCommentTargetType(targetType);
        if (!Set.of("album", "song", "profile").contains(normalizedType)) {
            throw new IllegalArgumentException("Unsupported comment target type: " + targetType);
        }

        String cleanText = text == null ? "" : text.trim();
        if (cleanText.isBlank()) {
            throw new IllegalArgumentException("Comment text cannot be blank");
        }

        String sql = """
            INSERT INTO comments (user_id, target_type, target_id, text, created_at, updated_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, userId);
            stmt.setString(2, normalizedType);
            stmt.setLong(3, targetId);
            stmt.setString(4, cleanText);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
            throw new RuntimeException("Comment insert succeeded but no ID returned");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create comment: " + e.getMessage());
        }
    }

    List<Map<String, Object>> getCommentsByTarget(String targetType, long targetId) {
        String normalizedType = normalizeCommentTargetType(targetType);
        String sql = """
            SELECT c.id, c.user_id, c.target_type, c.target_id, c.text, c.created_at, c.updated_at,
                   u.username, u.avatar_color,
                   COALESCE(u.display_name, u.username) AS display_name
            FROM comments c
            JOIN users u ON u.id = c.user_id
            WHERE c.target_type = ? AND c.target_id = ?
            ORDER BY COALESCE(c.updated_at, c.created_at, '') DESC, c.id DESC
            """;
        List<Map<String, Object>> comments = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, normalizedType);
            stmt.setLong(2, targetId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                comments.add(mapCommentRow(rs));
            }
        } catch (SQLException e) {
            System.out.println("Error getting comments: " + e.getMessage());
        }
        return comments;
    }

    boolean commentTargetExists(String targetType, long targetId) {
        try (Connection conn = getConnection()) {
            return commentTargetExists(conn, targetType, targetId);
        } catch (SQLException e) {
            System.out.println("Error checking comment target: " + e.getMessage());
            return false;
        }
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
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
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
        releaseYear = db.normalizeReleaseYear(releaseYear);

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

    @PostMapping("/songs/{songId}")
    public ResponseEntity<?> rateSong(@PathVariable long songId,
                                      @RequestBody Map<String, Object> body,
                                      @AuthenticationPrincipal UserDetails principal) {
        int stars;
        try {
            stars = Integer.parseInt(body.get("stars").toString());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "stars must be a number 1-5"));
        }
        if (stars < 1 || stars > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "stars must be between 1 and 5"));
        }
        if (!db.songExists(songId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Song not found"));
        }

        Map<String, Object> user = db.findUser(principal.getUsername());
        long userId = ((Number) user.get("id")).longValue();
        db.saveSongRating(userId, songId, stars);

        return ResponseEntity.ok(Map.of(
            "message", "Song rating saved!",
            "stars", stars,
            "songId", songId
        ));
    }

    @GetMapping("/songs/{songId}")
    public ResponseEntity<?> getMySongRating(@PathVariable long songId,
                                             @AuthenticationPrincipal UserDetails principal) {
        if (!db.songExists(songId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Song not found"));
        }
        Map<String, Object> user = db.findUser(principal.getUsername());
        long userId = ((Number) user.get("id")).longValue();
        int stars = db.getUserSongRating(userId, songId);
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
        List<Map<String, Object>> songRatings = db.getSongRatingsByUser(userId);
        List<Map<String, Object>> playlists = db.getPlaylistsByUser(userId);

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
        profile.put("songRatings", songRatings);
        profile.put("playlists",   playlists);

        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{username}/playlists/{playlistId}")
    public ResponseEntity<?> getPublicPlaylist(@PathVariable String username,
                                               @PathVariable long playlistId) {
        Map<String, Object> user = db.findUser(username);
        if (user == null || user.get("deletedAt") != null || Boolean.FALSE.equals(user.get("isActive"))) {
            return ResponseEntity.notFound().build();
        }
        long userId = ((Number) user.get("id")).longValue();
        Map<String, Object> playlist = db.getPlaylistByIdForUser(playlistId, userId);
        if (playlist == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(playlist);
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

    @PutMapping("/me/bio")
    public ResponseEntity<?> updateBio(@RequestBody Map<String, Object> body,
                                       @AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> user = db.findUser(principal.getUsername());
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        String bio = String.valueOf(body.getOrDefault("bio", "")).trim();
        if (bio.length() > 300) return ResponseEntity.badRequest().body(Map.of("error", "Bio must be 300 characters or fewer"));
        long userId = ((Number) user.get("id")).longValue();
        db.updateBio(userId, bio);
        return ResponseEntity.ok(Map.of("bio", bio));
    }
}

// ============================================================
// PLAYLIST CONTROLLER
// Lets logged-in users manage playlists made of songs only.
// ============================================================

@RestController
@RequestMapping("/api/playlists")
class PlaylistController {

    @Autowired Database db;

    private long currentUserId(UserDetails principal) {
        Map<String, Object> user = db.findUser(principal.getUsername());
        if (user == null) throw new UsernameNotFoundException("User not found: " + principal.getUsername());
        return ((Number) user.get("id")).longValue();
    }

    @GetMapping
    public List<Map<String, Object>> getMyPlaylists(@AuthenticationPrincipal UserDetails principal) {
        return db.getPlaylistsByUser(currentUserId(principal));
    }

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestBody Map<String, Object> body,
                                            @AuthenticationPrincipal UserDetails principal) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        String category = String.valueOf(body.getOrDefault("category", "Custom")).trim();

        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Playlist name is required"));
        }

        long userId = currentUserId(principal);
        long playlistId = db.createPlaylist(userId, name, description, category.isBlank() ? "Custom" : category);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "id", playlistId,
            "name", name,
            "description", description,
            "category", category.isBlank() ? "Custom" : category,
            "songCount", 0
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPlaylist(@PathVariable long id,
                                         @AuthenticationPrincipal UserDetails principal) {
        Map<String, Object> playlist = db.getPlaylistByIdForUser(id, currentUserId(principal));
        if (playlist == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(playlist);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlaylist(@PathVariable long id,
                                            @RequestBody Map<String, Object> body,
                                            @AuthenticationPrincipal UserDetails principal) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        String category = String.valueOf(body.getOrDefault("category", "Custom")).trim();
        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Playlist name is required"));
        }

        boolean updated = db.updatePlaylist(id, currentUserId(principal), name, description, category.isBlank() ? "Custom" : category);
        if (!updated) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("message", "Playlist updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@PathVariable long id,
                                            @AuthenticationPrincipal UserDetails principal) {
        boolean deleted = db.deletePlaylist(id, currentUserId(principal));
        if (!deleted) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("message", "Playlist deleted"));
    }

    @PostMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<?> addSongToPlaylist(@PathVariable long playlistId,
                                               @PathVariable long songId,
                                               @AuthenticationPrincipal UserDetails principal) {
        boolean ok = db.addSongToPlaylist(playlistId, currentUserId(principal), songId);
        if (!ok) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Playlist or song not found"));
        }
        return ResponseEntity.ok(Map.of("message", "Song added to playlist"));
    }

    // Adding an album stores each song separately in playlist_songs.
    @PostMapping("/{playlistId}/albums/{albumId}")
    public ResponseEntity<?> addAlbumToPlaylist(@PathVariable long playlistId,
                                                @PathVariable long albumId,
                                                @AuthenticationPrincipal UserDetails principal) {
        int inserted = db.addAlbumSongsToPlaylist(playlistId, currentUserId(principal), albumId);
        if (inserted == -1) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Playlist not found"));
        }
        if (inserted == -2) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Album not found"));
        }
        return ResponseEntity.ok(Map.of(
            "message", "Album songs added to playlist",
            "addedSongs", inserted
        ));
    }

    @DeleteMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<?> removeSongFromPlaylist(@PathVariable long playlistId,
                                                    @PathVariable long songId,
                                                    @AuthenticationPrincipal UserDetails principal) {
        boolean removed = db.removeSongFromPlaylist(playlistId, currentUserId(principal), songId);
        if (!removed) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Song not found in playlist"));
        return ResponseEntity.ok(Map.of("message", "Song removed from playlist"));
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
