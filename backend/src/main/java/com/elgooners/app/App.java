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
        String sql = "SELECT id, username, password, avatar_color FROM users WHERE username = ?";
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
        String sql = "INSERT INTO users (username, password, avatar_color) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setString(3, avatarColor);
            stmt.executeUpdate();
            return stmt.getGeneratedKeys().getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Could not create user: " + e.getMessage());
        }
    }

    // ------- ALBUM METHODS -------

    // Get all albums with their average rating
    List<Map<String, Object>> getAllAlbums() {
        String sql = """
            SELECT a.id, a.title, a.artist, a.release_year, a.genre,
                   a.description, a.color1, a.color2,
                   ROUND(AVG(r.stars), 1) AS avg_rating,
                   COUNT(r.id) AS rating_count
            FROM albums a
            LEFT JOIN ratings r ON r.album_id = a.id
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
                   a.description, a.color1, a.color2,
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
        String sql = "SELECT id, title, track_number, duration_seconds FROM songs WHERE album_id = ? ORDER BY track_number";
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
                songs.add(song);
            }
        } catch (SQLException e) {
            System.out.println("Error getting songs: " + e.getMessage());
        }
        return songs;
    }

    // Search albums by title, artist, or genre
    List<Map<String, Object>> searchAlbums(String query) {
        String like = "%" + query.toLowerCase() + "%";
        String sql = """
            SELECT a.id, a.title, a.artist, a.release_year, a.genre,
                   a.color1, a.color2,
                   ROUND(AVG(r.stars), 1) AS avg_rating,
                   COUNT(r.id) AS rating_count
            FROM albums a
            LEFT JOIN ratings r ON r.album_id = a.id
            WHERE LOWER(a.title) LIKE ? OR LOWER(a.artist) LIKE ? OR LOWER(a.genre) LIKE ?
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
            SELECT s.id, s.title, s.track_number, s.duration_seconds,
                   a.id AS album_id, a.title AS album_title, a.artist, a.release_year, a.genre,
                   a.color1, a.color2
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
                song.put("albumId", rs.getLong("album_id"));
                song.put("albumTitle", rs.getString("album_title"));
                song.put("artist", rs.getString("artist"));
                song.put("releaseYear", rs.getInt("release_year"));
                song.put("genre", rs.getString("genre"));
                song.put("color1", rs.getString("color1"));
                song.put("color2", rs.getString("color2"));
                results.add(song);
            }
        } catch (SQLException e) {
            System.out.println("Error searching songs: " + e.getMessage());
        }
        return results;
    }

    long createAlbum(String title,
                     String artist,
                     int releaseYear,
                     String genre,
                     String description,
                     String color1,
                     String color2) {
        String sql = "INSERT INTO albums (title, artist, release_year, genre, description, color1, color2) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, title);
            stmt.setString(2, artist);
            stmt.setInt(3, releaseYear);
            stmt.setString(4, genre);
            stmt.setString(5, description);
            stmt.setString(6, color1);
            stmt.setString(7, color2);
            stmt.executeUpdate();
            ResultSet keys = stmt.getGeneratedKeys();
            if (keys.next()) return keys.getLong(1);
            throw new RuntimeException("Album insert succeeded but no ID returned");
        } catch (SQLException e) {
            throw new RuntimeException("Could not create album: " + e.getMessage());
        }
    }

    long createSong(long albumId, String title, int trackNumber, int durationSeconds) {
        String sql = "INSERT INTO songs (album_id, title, track_number, duration_seconds) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, albumId);
            stmt.setString(2, title);
            stmt.setInt(3, trackNumber);
            stmt.setInt(4, durationSeconds);
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
            INSERT INTO ratings (user_id, album_id, stars)
            VALUES (?, ?, ?)
            ON CONFLICT(user_id, album_id) DO UPDATE SET stars = excluded.stars
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
            SELECT r.stars, a.id AS album_id, a.title, a.artist, a.color1, a.color2
            FROM ratings r
            JOIN albums a ON a.id = r.album_id
            WHERE r.user_id = ?
            ORDER BY r.id DESC
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
                ratings.add(rating);
            }
        } catch (SQLException e) {
            System.out.println("Error getting user ratings: " + e.getMessage());
        }
        return ratings;
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
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
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
        return User.withUsername(username)
            .password((String) user.get("password"))
            .authorities("ROLE_USER")
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
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/albums/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/search/add").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/profile/**").permitAll()
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
            "avatarColor", color
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        // Let Spring Security check the username and password
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Wrong username or password"));
        }

        Map<String, Object> user = db.findUser(username);
        String token = jwtHelper.createToken(username);
        return ResponseEntity.ok(Map.of(
            "token",       token,
            "userId",      user.get("id"),
            "username",    username,
            "avatarColor", user.getOrDefault("avatarColor", "#FF6B6B")
        ));
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
            String albumTitle = String.valueOf(body.getOrDefault("albumTitle", "Singles")).trim();
            long albumId = db.createAlbum(albumTitle, artist.isBlank() ? "Unknown Artist" : artist, releaseYear, genre, "Auto-created while adding a missing song.", color1, color2);
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
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        long userId = ((Number) user.get("id")).longValue();
        List<Map<String, Object>> ratings = db.getRatingsByUser(userId);

        // Build the profile response (don't include the password!)
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("username",    user.get("username"));
        profile.put("avatarColor", user.get("avatarColor"));
        profile.put("ratingCount", ratings.size());
        profile.put("ratings",     ratings);

        return ResponseEntity.ok(profile);
    }
}
