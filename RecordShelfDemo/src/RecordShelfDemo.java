package src;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
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
        frame.setSize(980, 620);
        frame.setMinimumSize(new Dimension(900, 560));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTextField searchField = new JTextField();
        JButton searchBtn = new JButton("Search");
        JComboBox<String> searchType = new JComboBox<>(new String[]{"Albums", "Songs", "Both"});
        searchType.setSelectedItem("Both");

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel searchPanel = new JPanel(new BorderLayout(8, 8));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchBtn, BorderLayout.EAST);
        top.add(searchPanel, BorderLayout.CENTER);
        top.add(searchType, BorderLayout.SOUTH);

        DefaultListModel<MediaItem> listModel = new DefaultListModel<>();
        RecordShelfPanel shelfPanel = new RecordShelfPanel(listModel);

        JButton saveBtn = new JButton("Save to My Account");
        JButton rateBtn = new JButton("Rate");
        JButton addBtn  = new JButton("Add Song / Album");
        JButton profileBtn = new JButton("View Profile");

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        bottom.add(saveBtn);
        bottom.add(rateBtn);
        bottom.add(addBtn);
        bottom.add(profileBtn);

        frame.add(top, BorderLayout.NORTH);
        frame.add(shelfPanel, BorderLayout.CENTER);
        frame.add(bottom, BorderLayout.SOUTH);

        Runnable updateList = () -> {
            String selectedType = searchType.getSelectedItem() == null ? "Both" : String.valueOf(searchType.getSelectedItem());
            populateList(listModel, searchMedia(searchField.getText().trim(), selectedType));
            shelfPanel.resetView();
            shelfPanel.revalidate();
            shelfPanel.repaint();
        };
        updateList.run();

        searchType.addActionListener(e -> {
            if (searchField.getText().trim().isEmpty()) {
                updateList.run();
            }
        });

        searchBtn.addActionListener(e -> {
            String keyword = searchField.getText().trim();
            String type = searchType.getSelectedItem() == null ? "Both" : String.valueOf(searchType.getSelectedItem());
            ArrayList<MediaItem> results = searchMedia(keyword, type);
            populateList(listModel, results);
            if (results.isEmpty()) {
                handleNoResults(frame, type, listModel);
            }
            shelfPanel.resetView();
            shelfPanel.repaint();
        });

        saveBtn.addActionListener(e -> {
            MediaItem selected = shelfPanel.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(frame, "Select an album or song first");
                return;
            }
            addUserSelectedItem(selected);
            JOptionPane.showMessageDialog(frame, "Saved to your account!");
        });

        rateBtn.addActionListener(e -> {
            MediaItem selected = shelfPanel.getSelectedValue();
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

        addBtn.addActionListener(e -> {
            String[] typeOptions = {"Song", "Album"};
            int typeChoice = JOptionPane.showOptionDialog(frame,
                    "What would you like to add?",
                    "Add to Music Shelf",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, typeOptions, typeOptions[0]);
            if (typeChoice < 0) return;

            boolean addingSong = typeChoice == 0;

            JPanel formPanel = new JPanel(new java.awt.GridBagLayout());
            java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
            gc.insets = new java.awt.Insets(4, 6, 4, 6);
            gc.fill = java.awt.GridBagConstraints.HORIZONTAL;

            JTextField artistField  = new JTextField(22);
            JTextField albumField   = new JTextField(22);
            JTextField yearField    = new JTextField(8);
            JTextField trackField   = addingSong ? new JTextField(8) : null;
            JTextField titleField   = addingSong ? new JTextField(22) : null;

            int row = 0;
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
            formPanel.add(new JLabel("Artist *"), gc);
            gc.gridx = 1; gc.gridwidth = 2;
            formPanel.add(artistField, gc);

            row++;
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
            formPanel.add(new JLabel("Album Title *"), gc);
            gc.gridx = 1; gc.gridwidth = 2;
            formPanel.add(albumField, gc);

            row++;
            gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
            formPanel.add(new JLabel("Year *"), gc);
            gc.gridx = 1; gc.gridwidth = 2;
            formPanel.add(yearField, gc);

            if (addingSong) {
                row++;
                gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
                formPanel.add(new JLabel("Track #"), gc);
                gc.gridx = 1; gc.gridwidth = 2;
                formPanel.add(trackField, gc);

                row++;
                gc.gridx = 0; gc.gridy = row; gc.gridwidth = 1;
                formPanel.add(new JLabel("Song Title *"), gc);
                gc.gridx = 1; gc.gridwidth = 2;
                formPanel.add(titleField, gc);
            }

            int confirm = JOptionPane.showConfirmDialog(frame, formPanel,
                    "Add " + (addingSong ? "Song" : "Album"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (confirm != JOptionPane.OK_OPTION) return;

            String artist = artistField.getText().trim();
            String albumTitle = albumField.getText().trim();
            String yearStr = yearField.getText().trim();

            if (artist.isEmpty() || albumTitle.isEmpty() || yearStr.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Artist, Album Title and Year are required.", "Missing fields", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int year;
            try {
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Year must be a number.", "Invalid Year", JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (addingSong) {
                String songTitle = titleField.getText().trim();
                if (songTitle.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Song title is required.", "Missing fields", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                int trackNum = 0;
                if (!trackField.getText().trim().isEmpty()) {
                    try {
                        trackNum = Integer.parseInt(trackField.getText().trim());
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(frame, "Track number must be a number.", "Invalid Track", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }

                Song newSong = new Song(songTitle, artist, albumTitle, year, trackNum, 0);
                ensureAlbumForSong(newSong);
                persistNewSong(newSong);
                populateList(listModel, searchMedia(searchField.getText().trim(), String.valueOf(searchType.getSelectedItem())));
                shelfPanel.resetView();
                JOptionPane.showMessageDialog(frame, "Song \"" + songTitle + "\" added!");
                return;
            }

            Album newAlbum = new Album(albumTitle, artist, year, 0, "");
            persistNewAlbum(newAlbum);
            populateList(listModel, searchMedia(searchField.getText().trim(), String.valueOf(searchType.getSelectedItem())));
            shelfPanel.resetView();
            JOptionPane.showMessageDialog(frame, "Album \"" + albumTitle + "\" added!");
        });

        profileBtn.addActionListener(e -> {
            frame.setVisible(false);
            showProfile(frame);
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        SwingUtilities.invokeLater(updateList);
    }


    static class RecordShelfPanel extends JPanel {
        private final DefaultListModel<MediaItem> model;
        private int selectedIndex = -1;
        // smoothCenter is a float index: the card currently centred on screen
        private float smoothCenter = 0f;
        private Timer animTimer;

        // Layout constants
        private static final int CARD_SIZE      = 160;
        private static final int SIDE_SPREAD    = 200;
        private static final int SIDE_SCALE_PCT = 68;
        private static final int MAX_SIDE       = 4;

        // Image cache: artUrl -> loaded Image (null sentinel = failed/no URL)
        private static final Map<String, java.awt.Image> IMG_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
        private static final java.awt.Image LOADING_IMAGE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        private static final java.awt.Image FAILED_IMAGE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);

        RecordShelfPanel(DefaultListModel<MediaItem> model) {
            this.model = model;
            setFocusable(true);
            setBackground(new Color(30, 28, 36));

            addMouseWheelListener(this::onWheel);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    int idx = indexAtPoint(e.getX(), e.getY());
                    if (idx >= 0 && idx < model.size()) {
                        navigate(idx);
                    }
                }
            });

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (model.isEmpty()) return;
                    if (selectedIndex < 0) selectedIndex = 0;
                    if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        navigate(Math.min(model.size() - 1, selectedIndex + 1));
                    } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                        navigate(Math.max(0, selectedIndex - 1));
                    }
                }
            });
        }

        void resetView() {
            selectedIndex = model.isEmpty() ? -1 : 0;
            smoothCenter = selectedIndex < 0 ? 0f : selectedIndex;
            repaint();
        }

        MediaItem getSelectedValue() {
            if (selectedIndex < 0 || selectedIndex >= model.size()) return null;
            return model.get(selectedIndex);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(900, 420);
        }

        private void navigate(int target) {
            if (target < 0 || target >= model.size()) return;
            selectedIndex = target;
            animateTo(target);
        }

        private void onWheel(MouseWheelEvent e) {
            if (model.isEmpty()) return;
            int next = Math.max(0, Math.min(model.size() - 1, selectedIndex + e.getWheelRotation()));
            navigate(next);
        }

        private void animateTo(float target) {
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            animTimer = new Timer(14, null);
            animTimer.addActionListener(ev -> {
                smoothCenter += (target - smoothCenter) * 0.22f;
                if (Math.abs(target - smoothCenter) < 0.008f) {
                    smoothCenter = target;
                    animTimer.stop();
                }
                repaint();
            });
            animTimer.start();
        }

        /** Hit-test: returns the model index of the card the user clicked, or -1. */
        private int indexAtPoint(int mx, int my) {
            int cx = getWidth() / 2;
            int cy = cardCentreY();
            for (int delta = MAX_SIDE; delta >= -MAX_SIDE; delta--) {
                int idx = Math.round(smoothCenter) + delta;
                if (idx < 0 || idx >= model.size()) continue;
                float dist = idx - smoothCenter;
                float scale = cardScale(dist);
                int size  = Math.round(CARD_SIZE * scale);
                int textH = textAreaHeight(scale);
                int totalH = size + textH;
                int x = Math.round(cx + dist * SIDE_SPREAD * scale) - size / 2;
                int y = cy - size / 2;
                if (mx >= x && mx <= x + size && my >= y && my <= y + totalH) return idx;
            }
            return -1;
        }

        /** Vertical centre of the art square. */
        private int cardCentreY() {
            return Math.max(150, getHeight() / 2 - 28);
        }

        /** Scale factor based on distance from the centre position. */
        private float cardScale(float dist) {
            float absDist = Math.abs(dist);
            if (absDist <= 0f) return 1.0f;
            float side = SIDE_SCALE_PCT / 100f;
            // Exponential fall-off: each step multiplies by `side`
            return (float) Math.pow(side, absDist);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,  RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,          RenderingHints.VALUE_RENDER_QUALITY);

            int w = Math.max(1, getWidth());
            int h = Math.max(1, getHeight());

            GradientPaint bg = new GradientPaint(0, 0, new Color(38, 35, 50), 0, h, new Color(18, 16, 24));
            g2.setPaint(bg);
            g2.fillRect(0, 0, w, h);

            g2.setColor(new Color(255, 255, 255, 12));
            g2.fillRoundRect(24, 24, w - 48, h - 48, 28, 28);

            if (model.isEmpty()) {
                g2.setColor(new Color(220, 220, 235));
                g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
                String msg = "No records found";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
                g2.dispose();
                return;
            }

            int centreIdx = Math.max(0, Math.min(model.size() - 1, Math.round(smoothCenter)));
            java.util.List<Integer> drawOrder = new java.util.ArrayList<>();
            for (int delta = -MAX_SIDE; delta <= MAX_SIDE; delta++) {
                drawOrder.add(delta);
            }
            drawOrder.sort((a, b) -> Integer.compare(Math.abs(b), Math.abs(a)));

            for (int delta : drawOrder) {
                int idx = centreIdx + delta;
                if (idx < 0 || idx >= model.size()) continue;
                paintCard(g2, idx, w / 2, cardCentreY());
            }

            g2.dispose();
        }

        private void paintCard(Graphics2D g2, int idx, int cx, int cy) {
            float dist = idx - smoothCenter;
            float scale = cardScale(dist);
            int size = Math.round(CARD_SIZE * scale);
            if (size < 18) return;

            int x = Math.round(cx + dist * SIDE_SPREAD * 0.82f) - size / 2;
            int y = cy - size / 2;

            if (x > getWidth() || x + size < 0) return;

            boolean isCentre = (idx == selectedIndex);
            int textAreaH = textAreaHeight(scale);

            if (isCentre) {
                for (int s = 18; s >= 1; s--) {
                    int alpha = (int) (110 * (s / 18f));
                    g2.setColor(new Color(0, 0, 0, alpha));
                    g2.fillRoundRect(x - s + 4, y - s / 2 + 10, size + s * 2, size + s * 2 + textAreaH - 8, 22, 22);
                }
            }

            g2.setColor(new Color(10, 10, 16, isCentre ? 210 : 155));
            g2.fillRoundRect(x - 8, y - 8, size + 16, size + textAreaH, 22, 22);

            paintArt(g2, idx, x, y, size);

            if (isCentre) {
                g2.setColor(new Color(255, 220, 80, 220));
                g2.setStroke(new BasicStroke(3f));
                g2.drawRoundRect(x, y, size, size, 16, 16);
                g2.setStroke(new BasicStroke(1f));
            }

            paintCardText(g2, idx, x, y + size + Math.round(10 * scale), size, scale);
        }

        private void paintArt(Graphics2D g2, int idx, int x, int y, int size) {
            MediaItem item = model.get(idx);
            String itemKey;
            try {
                itemKey = item == null || item.getItemKey() == null ? ("item-" + idx) : item.getItemKey();
            } catch (Exception ignored) {
                itemKey = "item-" + idx;
            }
            int hash = itemKey.hashCode() & 0x7fffffff;

            int r1 = 60 + (hash % 140);
            int g1 = 50 + ((hash / 13) % 140);
            int b1 = 80 + ((hash / 170) % 140);
            int r2 = Math.max(10, r1 - 50 + ((hash / 7) % 60));
            int g2c = Math.max(10, g1 - 50 + ((hash / 31) % 60));
            int b2 = Math.max(10, b1 - 30 + ((hash / 53) % 50));
            Color col1 = new Color(r1, g1, b1);
            Color col2 = new Color(r2, g2c, b2);

            String artUrl = null;
            if (item instanceof Album) artUrl = ((Album) item).artUrl;
            if (artUrl != null) artUrl = artUrl.trim();

            java.awt.Image img = null;
            if (artUrl != null && !artUrl.isEmpty()) {
                java.awt.Image cached = IMG_CACHE.get(artUrl);
                if (cached == null) {
                    java.awt.Image prior = IMG_CACHE.putIfAbsent(artUrl, LOADING_IMAGE);
                    if (prior == null) {
                        final String url = artUrl;
                        final RecordShelfPanel panel = this;
                        new Thread(() -> {
                            java.awt.Image loaded = null;
                            try {
                                loaded = javax.imageio.ImageIO.read(java.net.URI.create(url).toURL());
                            } catch (Exception ignored) {
                            }
                            IMG_CACHE.put(url, loaded != null ? loaded : FAILED_IMAGE);
                            SwingUtilities.invokeLater(panel::repaint);
                        }, "art-loader").start();
                    }
                } else if (cached != LOADING_IMAGE && cached != FAILED_IMAGE) {
                    img = cached;
                }
            }

            java.awt.Shape clip = new RoundRectangle2D.Float(x, y, size, size, 16, 16);
            Graphics2D g3 = (Graphics2D) g2.create();
            g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g3.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g3.clip(clip);

            if (img != null) {
                int iw = img.getWidth(null);
                int ih = img.getHeight(null);
                if (iw > 0 && ih > 0) {
                    float scaleF = Math.max((float) size / iw, (float) size / ih);
                    int dw = Math.round(iw * scaleF);
                    int dh = Math.round(ih * scaleF);
                    int dx = x + (size - dw) / 2;
                    int dy = y + (size - dh) / 2;
                    g3.drawImage(img, dx, dy, dw, dh, null);
                } else {
                    img = null;
                }
            }

            if (img == null) {
                GradientPaint gp = new GradientPaint(x, y, col1, x + size, y + size, col2);
                g3.setPaint(gp);
                g3.fillRect(x, y, size, size);

                String initials = initials(item);
                float fontSize = Math.max(12f, size * 0.38f);
                g3.setFont(getFont().deriveFont(Font.BOLD, fontSize));
                g3.setColor(new Color(255, 255, 255, 160));
                FontMetrics fm = g3.getFontMetrics();
                int tx = x + (size - fm.stringWidth(initials)) / 2;
                int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
                g3.drawString(initials, tx, ty);
            }

            g3.dispose();

            g2.setColor(new Color(255, 255, 255, 40));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(clip);
            g2.setStroke(new BasicStroke(1f));
        }

        private String initials(MediaItem item) {
            String t;
            try {
                t = (item instanceof Song) ? ((Song) item).title :
                        (item instanceof Album) ? ((Album) item).title : String.valueOf(item);
            } catch (Exception ignored) {
                t = "?";
            }
            if (t == null || t.trim().isEmpty()) return "?";
            String[] words = t.trim().split("\\s+");
            if (words.length == 1) return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }

        private void paintCardText(Graphics2D g2, int idx, int x, int y, int cardW, float scale) {
            MediaItem item = model.get(idx);
            String title = mainTitle(item);
            String artist = subTitle(item);

            float titleSize = Math.max(10f, 15f * scale);
            float artistSize = Math.max(9f, 12f * scale);
            int innerW = Math.max(16, cardW - 10);
            int maxTextH = textAreaHeight(scale) - Math.round(16 * scale);

            Font titleFont = getFont().deriveFont(Font.BOLD, titleSize);
            Font artistFont = getFont().deriveFont(Font.PLAIN, artistSize);

            Graphics2D gt = (Graphics2D) g2.create();
            gt.clipRect(x + 5, y - 2, Math.max(1, innerW), Math.max(1, maxTextH + 8));

            gt.setFont(titleFont);
            FontMetrics titleFm = gt.getFontMetrics();
            int titleLinesAllowed = scale >= 0.9f ? 2 : 1;
            java.util.List<String> titleLines = wrapTextLines(title, titleFm, innerW, titleLinesAllowed);

            gt.setFont(artistFont);
            FontMetrics artistFm = gt.getFontMetrics();
            int artistLinesAllowed = scale >= 0.98f ? 2 : 1;
            java.util.List<String> artistLines = wrapTextLines(artist, artistFm, innerW, artistLinesAllowed);

            int titleBlockH = titleLines.size() * titleFm.getHeight();
            int artistBlockH = artistLines.size() * artistFm.getHeight();
            int gap = Math.max(2, Math.round(4 * scale));
            int totalH = titleBlockH + (artistLines.isEmpty() ? 0 : gap + artistBlockH);

            int startY = y + Math.max(titleFm.getAscent(), (maxTextH - totalH) / 2 + titleFm.getAscent());

            gt.setFont(titleFont);
            gt.setColor(new Color(244, 242, 248));
            int lineY = startY;
            for (String line : titleLines) {
                int tx = x + (cardW - titleFm.stringWidth(line)) / 2;
                gt.drawString(line, tx, lineY);
                lineY += titleFm.getHeight();
            }

            if (!artistLines.isEmpty()) {
                lineY += gap - artistFm.getDescent();
                gt.setFont(artistFont);
                gt.setColor(new Color(185, 178, 205));
                for (String line : artistLines) {
                    int ax = x + (cardW - artistFm.stringWidth(line)) / 2;
                    gt.drawString(line, ax, lineY);
                    lineY += artistFm.getHeight();
                }
            }

            gt.dispose();
        }

        private int textAreaHeight(float scale) {
            return Math.max(36, Math.round(78 * scale));
        }

        private java.util.List<String> wrapTextLines(String text, FontMetrics fm, int maxW, int maxLines) {
            java.util.List<String> lines = new java.util.ArrayList<>();
            if (text == null || text.trim().isEmpty() || maxLines <= 0) return lines;

            String[] words = text.trim().split("\\s+");
            StringBuilder current = new StringBuilder();

            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (fm.stringWidth(candidate) <= maxW) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }

                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    if (lines.size() == maxLines - 1) {
                        StringBuilder remaining = new StringBuilder(word);
                        for (int j = i + 1; j < words.length; j++) {
                            remaining.append(' ').append(words[j]);
                        }
                        lines.add(fitWithEllipsis(remaining.toString(), fm, maxW));
                        return lines;
                    }
                    current.setLength(0);
                    current.append(word);
                } else {
                    lines.add(fitWithEllipsis(word, fm, maxW));
                    if (lines.size() >= maxLines) return lines;
                }
            }

            if (!current.isEmpty() && lines.size() < maxLines) {
                lines.add(current.toString());
            }
            return lines;
        }

        private String fitWithEllipsis(String text, FontMetrics fm, int maxW) {
            if (text == null || text.isEmpty()) return "";
            if (fm.stringWidth(text) <= maxW) return text;
            String ellipsis = "…";
            StringBuilder sb = new StringBuilder(text);
            while (!sb.isEmpty() && fm.stringWidth(sb + ellipsis) > maxW) {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb + ellipsis;
        }

        private String wrapOrEllipsis(Graphics2D g2, String text, int maxW) {
            if (text == null || text.isEmpty()) return "";
            FontMetrics fm = g2.getFontMetrics();
            return fitWithEllipsis(text, fm, maxW);
        }

        private String mainTitle(MediaItem item) {
            if (item instanceof Song) return ((Song) item).title;
            if (item instanceof Album) return ((Album) item).title;
            return item == null ? "" : item.toString();
        }

        private String subTitle(MediaItem item) {
            if (item instanceof Song) return ((Song) item).artist;
            if (item instanceof Album) return ((Album) item).artist;
            return "";
        }
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

