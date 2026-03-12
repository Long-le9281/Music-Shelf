package src;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecordShelfDemo {

    // -----------------------------
    // Storage
    // -----------------------------
    static final ArrayList<User> users = new ArrayList<>();
    static final ArrayList<Album> albums = new ArrayList<>();
    static final ArrayList<Song> songs = new ArrayList<>();
    static final Map<String, Album> albumLookup = new HashMap<>();
    static final Map<String, Song> songLookup = new HashMap<>();

    // Resolve the src/ directory from the location of this .class file so that
    // all CSV paths work regardless of which working directory IntelliJ uses.
    static final File SRC_DIR = resolveSrcDir();
    static final File USERS_FILE = new File(SRC_DIR, "users.csv");
    static final File USER_LIBRARY_FILE = new File(SRC_DIR, "user_library.csv");
    static final File ALBUMS_SOURCE_FILE = new File(SRC_DIR, "albums.csv");
    static final File SONGS_SOURCE_FILE = new File(SRC_DIR, "songs.csv");
    static User currentUser;

    static File resolveSrcDir() {
        try {
            File classLocation = new File(
                    RecordShelfDemo.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            // classLocation is the output root (e.g. RecordShelfDemo/out/src or RecordShelfDemo/out)
            // Walk up the tree; skip any directory named "out" or "src" that is an output dir,
            // and look for a sibling "src" that contains "albums.csv".
            File dir = classLocation.isDirectory() ? classLocation : classLocation.getParentFile();
            for (int i = 0; i < 6; i++) {
                File candidate = new File(dir, "src");
                if (candidate.isDirectory() && new File(candidate, "albums.csv").exists()) {
                    return candidate;
                }
                if (dir.getParentFile() == null) break;
                dir = dir.getParentFile();
            }
        } catch (Exception ignored) {}
        // Fallback: try common relative paths from working directory
        for (String rel : new String[]{"src", "RecordShelfDemo/src"}) {
            File f = new File(rel);
            if (f.isDirectory() && new File(f, "albums.csv").exists()) return f;
        }
        return new File("src"); // last resort
    }

    public static void main(String[] args) {
        System.out.println("Working dir      : " + new File(".").getAbsolutePath());
        System.out.println("SRC_DIR          : " + SRC_DIR.getAbsolutePath());
        System.out.println("albums.csv       : " + ALBUMS_SOURCE_FILE.getAbsolutePath() + " exists=" + ALBUMS_SOURCE_FILE.exists());
        System.out.println("songs.csv        : " + SONGS_SOURCE_FILE.getAbsolutePath()  + " exists=" + SONGS_SOURCE_FILE.exists());
        System.out.println("users.csv        : " + USERS_FILE.getAbsolutePath());
        System.out.println("user_library.csv : " + USER_LIBRARY_FILE.getAbsolutePath());
        loadUsers();
        loadCatalog();
        loadUserLibrary();
        System.out.println("Loaded " + albums.size() + " albums, " + songs.size() + " songs.");
        SwingUtilities.invokeLater(RecordShelfDemo::showLogin);
    }

    // -----------------------------
    // CSV + Persistence Helpers
    // -----------------------------
    static void loadCatalog() {
        albums.clear();
        songs.clear();
        albumLookup.clear();
        songLookup.clear();
        loadAlbumsFromCsv();
        loadSongsFromCsv();
    }

    static void loadAlbumsFromCsv() {
        if (!ALBUMS_SOURCE_FILE.exists()) {
            System.out.println("Could not find albums source: " + ALBUMS_SOURCE_FILE.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(ALBUMS_SOURCE_FILE, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCsvLine(line);
                if (parts.size() < 5) {
                    continue;
                }
                addAlbumToCatalog(new Album(
                        parts.get(1).trim(),
                        parts.get(0).trim(),
                        parseIntSafe(parts.get(2)),
                        parseIntSafe(parts.get(3)),
                        parts.get(4).trim()
                ));
            }
        } catch (Exception e) {
            System.out.println("Could not load albums.");
        }
    }

    static void loadSongsFromCsv() {
        if (!SONGS_SOURCE_FILE.exists()) {
            System.out.println("Could not find songs source: " + SONGS_SOURCE_FILE.getAbsolutePath());
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(SONGS_SOURCE_FILE, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCsvLine(line);
                if (parts.size() < 6) {
                    continue;
                }
                Song song = new Song(
                        parts.get(4).trim(),
                        parts.get(0).trim(),
                        parts.get(1).trim(),
                        parseIntSafe(parts.get(2)),
                        parseIntSafe(parts.get(3)),
                        parseIntSafe(parts.get(5))
                );
                addSongToCatalog(song);
            }
        } catch (Exception e) {
            System.out.println("Could not load songs.");
        }
    }

    static void loadUsers() {
        users.clear();
        ensureUsersFileExists();

        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCsvLine(line);
                if (parts.size() < 2) {
                    continue;
                }
                users.add(new User(parts.get(0).trim(), parts.get(1).trim()));
            }
        } catch (Exception e) {
            System.out.println("Could not load users.");
        }
    }

    static void saveUsers() {
        ensureUsersFileExists();

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(USERS_FILE), StandardCharsets.UTF_8))) {
            writer.println("username,password");
            for (User u : users) {
                writer.println(csvRow(u.username, u.password));
            }
        } catch (Exception e) {
            System.out.println("Could not save users.");
        }
    }

    static void loadUserLibrary() {
        ensureUserLibraryFileExists();

        try (BufferedReader reader = new BufferedReader(new FileReader(USER_LIBRARY_FILE, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                List<String> parts = parseCsvLine(line);
                if (parts.size() < 5) {
                    continue;
                }

                User user = findUser(parts.get(0).trim());
                if (user == null) {
                    continue;
                }

                String itemType = parts.get(1).trim();
                String itemKey = parts.get(2).trim();
                int rating = parseIntSafe(parts.get(3));
                boolean saved = Boolean.parseBoolean(parts.get(4).trim());
                MediaItem item = resolveMediaItem(itemType, itemKey);
                if (item == null) {
                    continue;
                }
                upsertUserEntry(user, item, rating, saved, false);
            }
        } catch (Exception e) {
            System.out.println("Could not load user library.");
        }
    }

    static void saveUserLibrary() {
        ensureUserLibraryFileExists();

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(USER_LIBRARY_FILE), StandardCharsets.UTF_8))) {
            writer.println("username,item_type,item_key,rating,saved");
            for (User user : users) {
                for (UserMediaEntry entry : user.library) {
                    writer.println(csvRow(
                            user.username,
                            entry.item.getItemType(),
                            entry.item.getItemKey(),
                            String.valueOf(entry.rating),
                            String.valueOf(entry.saved)
                    ));
                }
            }
        } catch (Exception e) {
            System.out.println("Could not save user library.");
        }
    }

    static void ensureUsersFileExists() {
        if (USERS_FILE.exists()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(USERS_FILE), StandardCharsets.UTF_8))) {
            writer.println("username,password");
        } catch (Exception e) {
            System.out.println("Could not create users file.");
        }
    }

    static void ensureUserLibraryFileExists() {
        if (USER_LIBRARY_FILE.exists()) {
            return;
        }
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(USER_LIBRARY_FILE), StandardCharsets.UTF_8))) {
            writer.println("username,item_type,item_key,rating,saved");
        } catch (Exception e) {
            System.out.println("Could not create user library file.");
        }
    }

    static List<String> parseCsvLine(String line) {
        ArrayList<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }

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

    static String csvEscape(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    static String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(csvEscape(values[i]));
        }
        return row.toString();
    }

    static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    static String albumKey(String artist, String title) {
        return normalize(artist) + "|" + normalize(title);
    }

    static String songKey(String artist, String albumTitle, String title) {
        return normalize(artist) + "|" + normalize(albumTitle) + "|" + normalize(title);
    }

    static void addAlbumToCatalog(Album album) {
        String key = album.getItemKey();
        if (!albumLookup.containsKey(key)) {
            albums.add(album);
            albumLookup.put(key, album);
        }
    }

    static void addSongToCatalog(Song song) {
        String key = song.getItemKey();
        if (!songLookup.containsKey(key)) {
            songs.add(song);
            songLookup.put(key, song);
        }
    }

    static MediaItem resolveMediaItem(String itemType, String itemKey) {
        if ("album".equalsIgnoreCase(itemType)) {
            return albumLookup.get(itemKey);
        }
        if ("song".equalsIgnoreCase(itemType)) {
            return songLookup.get(itemKey);
        }
        return null;
    }

    static User findUser(String username) {
        for (User u : users) {
            if (u.username.equals(username)) {
                return u;
            }
        }
        return null;
    }

    static boolean userExists(String username) {
        return findUser(username) != null;
    }

    static UserMediaEntry findUserEntry(User user, MediaItem item) {
        for (UserMediaEntry entry : user.library) {
            if (entry.item.getItemType().equals(item.getItemType())
                    && entry.item.getItemKey().equals(item.getItemKey())) {
                return entry;
            }
        }
        return null;
    }

    static void upsertUserEntry(User user, MediaItem item, int rating, boolean saved, boolean persist) {
        UserMediaEntry existing = findUserEntry(user, item);
        if (existing == null) {
            user.library.add(new UserMediaEntry(item, rating, saved));
        } else {
            existing.saved = saved || existing.saved;
            if (rating > 0) {
                existing.rating = rating;
            }
        }

        if (persist) {
            saveUserLibrary();
        }
    }

    static ArrayList<MediaItem> searchMedia(String keyword, String type) {
        ArrayList<MediaItem> results = new ArrayList<>();
        String normalizedKeyword = normalize(keyword);

        if (type.equals("Albums") || type.equals("Both")) {
            for (Album album : albums) {
                if (normalizedKeyword.isEmpty()
                        || normalize(album.title).contains(normalizedKeyword)
                        || normalize(album.artist).contains(normalizedKeyword)) {
                    results.add(album);
                }
            }
        }

        if (type.equals("Songs") || type.equals("Both")) {
            for (Song song : songs) {
                if (normalizedKeyword.isEmpty()
                        || normalize(song.title).contains(normalizedKeyword)
                        || normalize(song.artist).contains(normalizedKeyword)
                        || normalize(song.albumTitle).contains(normalizedKeyword)) {
                    results.add(song);
                }
            }
        }

        return results;
    }

    static void populateList(DefaultListModel<MediaItem> listModel, ArrayList<MediaItem> items) {
        listModel.clear();
        for (MediaItem item : items) {
            listModel.addElement(item);
        }
    }

    static Album promptForNewAlbum(Component parent) {
        JTextField artistField = new JTextField();
        JTextField albumTitleField = new JTextField();
        JTextField yearField = new JTextField();
        JTextField trackCountField = new JTextField();
        JTextField artUrlField = new JTextField();

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Artist"));
        panel.add(artistField);
        panel.add(new JLabel("Album title"));
        panel.add(albumTitleField);
        panel.add(new JLabel("Year"));
        panel.add(yearField);
        panel.add(new JLabel("Track count"));
        panel.add(trackCountField);
        panel.add(new JLabel("Art URL"));
        panel.add(artUrlField);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Add Missing Album", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String artist = artistField.getText().trim();
        String albumTitle = albumTitleField.getText().trim();
        if (artist.isEmpty() || albumTitle.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Artist and album title are required.");
            return null;
        }

        Album existing = albumLookup.get(albumKey(artist, albumTitle));
        if (existing != null) {
            return existing;
        }

        return new Album(
                albumTitle,
                artist,
                parseIntSafe(yearField.getText()),
                parseIntSafe(trackCountField.getText()),
                artUrlField.getText().trim()
        );
    }

    static Song promptForNewSong(Component parent) {
        JTextField artistField = new JTextField();
        JTextField albumTitleField = new JTextField();
        JTextField yearField = new JTextField();
        JTextField trackNumberField = new JTextField();
        JTextField songTitleField = new JTextField();
        JTextField durationSecondsField = new JTextField();

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Artist"));
        panel.add(artistField);
        panel.add(new JLabel("Album title"));
        panel.add(albumTitleField);
        panel.add(new JLabel("Year"));
        panel.add(yearField);
        panel.add(new JLabel("Track number"));
        panel.add(trackNumberField);
        panel.add(new JLabel("Song title"));
        panel.add(songTitleField);
        panel.add(new JLabel("Duration (seconds)"));
        panel.add(durationSecondsField);

        int result = JOptionPane.showConfirmDialog(parent, panel, "Add Missing Song", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String artist = artistField.getText().trim();
        String albumTitle = albumTitleField.getText().trim();
        String songTitle = songTitleField.getText().trim();
        if (artist.isEmpty() || albumTitle.isEmpty() || songTitle.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Artist, album title, and song title are required.");
            return null;
        }

        Song existing = songLookup.get(songKey(artist, albumTitle, songTitle));
        if (existing != null) {
            return existing;
        }

        return new Song(
                songTitle,
                artist,
                albumTitle,
                parseIntSafe(yearField.getText()),
                parseIntSafe(trackNumberField.getText()),
                parseIntSafe(durationSecondsField.getText())
        );
    }

    static void addUserSelectedItem(MediaItem item) {
        if (currentUser == null || item == null) {
            return;
        }
        upsertUserEntry(currentUser, item, 0, true, true);
    }

    static void persistNewAlbum(Album album) {
        if (album == null) {
            return;
        }
        addAlbumToCatalog(album);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(ALBUMS_SOURCE_FILE, true), StandardCharsets.UTF_8))) {
            if (ALBUMS_SOURCE_FILE.length() == 0) {
                writer.println("artist,album_title,year,track_count,art_url");
            }
            writer.println(csvRow(album.artist, album.title, String.valueOf(album.year), String.valueOf(album.trackCount), album.artUrl));
        } catch (Exception e) {
            System.out.println("Could not save album.");
        }
    }

    static void persistNewSong(Song song) {
        if (song == null) {
            return;
        }
        addSongToCatalog(song);
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(SONGS_SOURCE_FILE, true), StandardCharsets.UTF_8))) {
            if (SONGS_SOURCE_FILE.length() == 0) {
                writer.println("artist,album_title,year,track_number,song_title,duration_seconds");
            }
            writer.println(csvRow(song.artist, song.albumTitle, String.valueOf(song.year), String.valueOf(song.trackNumber), song.title, String.valueOf(song.durationSeconds)));
        } catch (Exception e) {
            System.out.println("Could not save song.");
        }
    }

    static void handleNoResults(Component parent, String type, DefaultListModel<MediaItem> listModel) {
        if ("Both".equals(type)) {
            Object[] options = {"Add Album", "Add Song", "Cancel"};
            int choice = JOptionPane.showOptionDialog(parent,
                    "No results found. Would you like to add an album or song?",
                    "No Results",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]);

            if (choice == 0) {
                Album album = promptForNewAlbum(parent);
                if (album != null) {
                    persistNewAlbum(album);
                    addUserSelectedItem(album);
                    populateList(listModel, new ArrayList<>(List.of(album)));
                    JOptionPane.showMessageDialog(parent, "Album added and saved to your account.");
                }
            } else if (choice == 1) {
                Song song = promptForNewSong(parent);
                if (song != null) {
                    ensureAlbumForSong(song);
                    persistNewSong(song);
                    addUserSelectedItem(song);
                    populateList(listModel, new ArrayList<>(List.of(song)));
                    JOptionPane.showMessageDialog(parent, "Song added and saved to your account.");
                }
            }
            return;
        }

        if ("Albums".equals(type)) {
            int choice = JOptionPane.showConfirmDialog(parent,
                    "No matching albums found. Add one now?",
                    "No Results",
                    JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
                Album album = promptForNewAlbum(parent);
                if (album != null) {
                    persistNewAlbum(album);
                    addUserSelectedItem(album);
                    populateList(listModel, new ArrayList<>(List.of(album)));
                    JOptionPane.showMessageDialog(parent, "Album added and saved to your account.");
                }
            }
            return;
        }

        int choice = JOptionPane.showConfirmDialog(parent,
                "No matching songs found. Add one now?",
                "No Results",
                JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            Song song = promptForNewSong(parent);
            if (song != null) {
                ensureAlbumForSong(song);
                persistNewSong(song);
                addUserSelectedItem(song);
                populateList(listModel, new ArrayList<>(List.of(song)));
                JOptionPane.showMessageDialog(parent, "Song added and saved to your account.");
            }
        }
    }

    static void ensureAlbumForSong(Song song) {
        if (song == null) {
            return;
        }
        if (!albumLookup.containsKey(albumKey(song.artist, song.albumTitle))) {
            persistNewAlbum(new Album(song.albumTitle, song.artist, song.year, 0, ""));
        }
    }

    // -----------------------------
    // LOGIN WINDOW
    // -----------------------------
    static void showLogin() {

        JFrame frame = new JFrame("Record Shelf - Login");
        frame.setSize(300, 200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(6, 1));

        JTextField username = new JTextField();
        JPasswordField password = new JPasswordField();

        JButton loginBtn = new JButton("Login");
        JButton signupBtn = new JButton("Sign Up");

        frame.add(new JLabel("Username"));
        frame.add(username);
        frame.add(new JLabel("Password"));
        frame.add(password);
        frame.add(loginBtn);
        frame.add(signupBtn);

        loginBtn.addActionListener(e -> {
            for (User u : users) {
                if (u.username.equals(username.getText().trim())
                        && u.password.equals(new String(password.getPassword()))) {
                    currentUser = u;
                    frame.dispose();
                    showMain();
                    return;
                }
            }
            JOptionPane.showMessageDialog(frame, "Login failed");
        });

        signupBtn.addActionListener(e -> {
            String user = username.getText().trim();
            String pass = new String(password.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Username and password are required");
                return;
            }

            if (userExists(user)) {
                JOptionPane.showMessageDialog(frame, "Username already exists");
                return;
            }

            users.add(new User(user, pass));
            saveUsers();
            saveUserLibrary();
            JOptionPane.showMessageDialog(frame, "User created!");
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // -----------------------------
    // MAIN APP WINDOW
    // -----------------------------
    static void showMain() {

        JFrame frame = new JFrame("Elgooners Record Shelf");
        frame.setSize(650, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTextField searchField = new JTextField();
        JButton searchBtn = new JButton("Search");
        JComboBox<String> searchType = new JComboBox<>(new String[]{"Albums", "Songs", "Both"});
        searchType.setSelectedItem("Both");

        JPanel top = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchBtn, BorderLayout.EAST);
        top.add(searchPanel, BorderLayout.CENTER);
        top.add(searchType, BorderLayout.SOUTH);

        DefaultListModel<MediaItem> listModel = new DefaultListModel<>();
        JList<MediaItem> mediaList = new JList<>(listModel);

        JButton saveBtn = new JButton("Save to My Account");
        JButton rateBtn = new JButton("Rate");
        JButton profileBtn = new JButton("View Profile");

        JPanel bottom = new JPanel();
        bottom.add(saveBtn);
        bottom.add(rateBtn);
        bottom.add(profileBtn);

        frame.add(top, BorderLayout.NORTH);
        frame.add(new JScrollPane(mediaList), BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        Runnable updateList = () -> populateList(listModel, searchMedia("", (String) searchType.getSelectedItem()));
        updateList.run();

        searchType.addActionListener(e -> {
            if (searchField.getText().trim().isEmpty()) {
                updateList.run();
            }
        });

        searchBtn.addActionListener(e -> {
            String keyword = searchField.getText().trim();
            String type = (String) searchType.getSelectedItem();
            ArrayList<MediaItem> results = searchMedia(keyword, type);
            populateList(listModel, results);
            if (results.isEmpty()) {
                handleNoResults(frame, type, listModel);
            }
        });

        saveBtn.addActionListener(e -> {
            MediaItem selected = mediaList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(frame, "Select an album or song first");
                return;
            }
            addUserSelectedItem(selected);
            JOptionPane.showMessageDialog(frame, "Saved to your account!");
        });

        rateBtn.addActionListener(e -> {
            MediaItem selected = mediaList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(frame, "Select an item first");
                return;
            }

            String input = JOptionPane.showInputDialog(frame, "Rating (1-5)");
            if (input == null) {
                return;
            }

            try {
                int rating = Integer.parseInt(input.trim());
                if (rating < 1 || rating > 5) {
                    JOptionPane.showMessageDialog(frame, "Rating must be between 1 and 5");
                    return;
                }

                upsertUserEntry(currentUser, selected, rating, true, true);
                JOptionPane.showMessageDialog(frame, "Rating saved to your account!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, "Invalid rating");
            }
        });

        profileBtn.addActionListener(e -> {
            frame.setVisible(false);
            showProfile(frame);
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // -----------------------------
    // PROFILE WINDOW
    // -----------------------------
    static void showProfile(JFrame mainFrame) {

        JFrame frame = new JFrame(currentUser.username + " Profile");
        frame.setSize(500, 400);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();
        if (currentUser.library.isEmpty()) {
            model.addElement("No saved albums or songs yet.");
        } else {
            for (UserMediaEntry entry : currentUser.library) {
                String ratingText = entry.rating > 0 ? entry.rating + "/5" : "Not rated";
                model.addElement(entry.item.toProfileString() + " - " + ratingText);
            }
        }

        JList<String> list = new JList<>(model);

        JButton backBtn = new JButton("Back");
        backBtn.addActionListener(e -> {
            frame.dispose();
            mainFrame.setVisible(true);
        });

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(backBtn);

        frame.add(new JScrollPane(list), BorderLayout.CENTER);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        frame.setLocationRelativeTo(mainFrame);
        frame.setVisible(true);
    }

    // -----------------------------
    // DATA CLASSES
    // -----------------------------

    interface MediaItem {
        String getItemType();
        String getItemKey();
        String toProfileString();
    }

    static class User {
        String username;
        String password;
        ArrayList<UserMediaEntry> library = new ArrayList<>();

        User(String u, String p) {
            username = u;
            password = p;
        }
    }

    static class UserMediaEntry {
        MediaItem item;
        int rating;
        boolean saved;

        UserMediaEntry(MediaItem item, int rating, boolean saved) {
            this.item = item;
            this.rating = rating;
            this.saved = saved;
        }
    }

    static class Album implements MediaItem {
        String title;
        String artist;
        int year;
        int trackCount;
        String artUrl;

        Album(String t, String a, int y, int tc, String art) {
            title = t;
            artist = a;
            year = y;
            trackCount = tc;
            artUrl = art;
        }

        public String getItemType() {
            return "album";
        }

        public String getItemKey() {
            return albumKey(artist, title);
        }

        public String toProfileString() {
            return title + " (Album) - " + artist + yearSuffix(year);
        }

        public String toString() {
            return title + " - " + artist + yearSuffix(year);
        }
    }

    static class Song implements MediaItem {
        String title;
        String artist;
        String albumTitle;
        int year;
        int trackNumber;
        int durationSeconds;

        Song(String t, String a, String albumTitle, int y, int tn, int durationSeconds) {
            title = t;
            artist = a;
            this.albumTitle = albumTitle;
            year = y;
            trackNumber = tn;
            this.durationSeconds = durationSeconds;
        }

        public String getItemType() {
            return "song";
        }

        public String getItemKey() {
            return songKey(artist, albumTitle, title);
        }

        public String toProfileString() {
            return title + " (Song) - " + artist + " [" + albumTitle + "]" + yearSuffix(year);
        }

        public String toString() {
            String duration = durationSeconds > 0 ? " - " + durationSeconds + "s" : "";
            return title + " - " + artist + " (" + albumTitle + ")" + duration;
        }
    }

    static String yearSuffix(int year) {
        return year > 0 ? " (" + year + ")" : "";
    }
}

