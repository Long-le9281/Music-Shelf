import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class RecordShelfDemo {

    // -----------------------------
    // Dummy Data Storage
    // -----------------------------
    static ArrayList<User> users = new ArrayList<>();
    static ArrayList<Album> albums = new ArrayList<>();
    static ArrayList<Song> songs = new ArrayList<>();
    static User currentUser;

    public static void main(String[] args) {

        // Hard coded demo data
        users.add(new User("demo","demo"));

        albums.add(new Album("Abbey Road","The Beatles"));
        albums.add(new Album("Thriller","Michael Jackson"));
        albums.add(new Album("Back in Black","AC/DC"));
        albums.add(new Album("Random Access Memories","Daft Punk"));

        songs.add(new Song("Come Together","The Beatles","Abbey Road"));
        songs.add(new Song("Billie Jean","Michael Jackson","Thriller"));
        songs.add(new Song("Back in Black","AC/DC","Back in Black"));
        songs.add(new Song("Get Lucky","Daft Punk","Random Access Memories"));
        songs.add(new Song("Bohemian Rhapsody","Queen","A Night at the Opera"));

        SwingUtilities.invokeLater(() -> showLogin());
    }

    // -----------------------------
    // LOGIN WINDOW
    // -----------------------------
    static void showLogin(){

        JFrame frame = new JFrame("Record Shelf - Login");
        frame.setSize(300,200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(5,1));

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

            for(User u : users){

                if(u.username.equals(username.getText())
                        && u.password.equals(new String(password.getPassword()))){

                    currentUser = u;
                    frame.dispose();
                    showMain();
                    return;

                }
            }

            JOptionPane.showMessageDialog(frame,"Login failed");

        });

        signupBtn.addActionListener(e -> {

            String user = username.getText();
            String pass = new String(password.getPassword());

            users.add(new User(user,pass));

            JOptionPane.showMessageDialog(frame,"User created!");

        });

        frame.setVisible(true);
    }

    // -----------------------------
    // MAIN APP WINDOW
    // -----------------------------
    static void showMain(){

        JFrame frame = new JFrame("Elgooners Record Shelf");
        frame.setSize(500,500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JTextField searchField = new JTextField();
        JButton searchBtn = new JButton("Search");
        JComboBox<String> searchType = new JComboBox<>(new String[]{"Albums","Songs","Both"});
        searchType.setSelectedItem("Both"); // Set default selection

        JPanel top = new JPanel(new BorderLayout());
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(searchField,BorderLayout.CENTER);
        searchPanel.add(searchBtn,BorderLayout.EAST);
        top.add(searchPanel,BorderLayout.CENTER);
        top.add(searchType,BorderLayout.SOUTH);

        DefaultListModel<Object> listModel = new DefaultListModel<>();
        JList<Object> mediaList = new JList<>(listModel);

        JButton rateBtn = new JButton("Rate");
        JButton profileBtn = new JButton("View Profile");

        JPanel bottom = new JPanel();
        bottom.add(rateBtn);
        bottom.add(profileBtn);

        frame.add(top,BorderLayout.NORTH);
        frame.add(new JScrollPane(mediaList),BorderLayout.CENTER);
        frame.add(bottom,BorderLayout.SOUTH);

        // Method to populate list based on type selection
        Runnable updateList = () -> {
            listModel.clear();
            String type = (String) searchType.getSelectedItem();

            if(type.equals("Albums") || type.equals("Both")){
                for(Album a : albums){
                    listModel.addElement(a);
                }
            }

            if(type.equals("Songs") || type.equals("Both")){
                for(Song s : songs){
                    listModel.addElement(s);
                }
            }
        };

        // Populate list initially
        updateList.run();

        // Add listener to update list when dropdown changes
        searchType.addActionListener(e -> {
            if(searchField.getText().isEmpty()){
                updateList.run();
            }
        });

        searchBtn.addActionListener(e -> {

            listModel.clear();

            String keyword = searchField.getText().toLowerCase();
            String type = (String) searchType.getSelectedItem();

            if(type.equals("Albums") || type.equals("Both")){
                for(Album a : albums){
                    if(a.title.toLowerCase().contains(keyword) || a.artist.toLowerCase().contains(keyword)){
                        listModel.addElement(a);
                    }
                }
            }

            if(type.equals("Songs") || type.equals("Both")){
                for(Song s : songs){
                    if(s.title.toLowerCase().contains(keyword) || s.artist.toLowerCase().contains(keyword) || s.album.toLowerCase().contains(keyword)){
                        listModel.addElement(s);
                    }
                }
            }

        });

        rateBtn.addActionListener(e -> {

            Object selected = mediaList.getSelectedValue();

            if(selected == null){
                JOptionPane.showMessageDialog(frame,"Select an item first");
                return;
            }

            String input = JOptionPane.showInputDialog("Rating (1-5)");

            try{

                int rating = Integer.parseInt(input);

                if(rating < 1 || rating > 5){
                    JOptionPane.showMessageDialog(frame,"Rating must be between 1 and 5");
                    return;
                }

                currentUser.ratings.add(new Rating(selected,rating));
                JOptionPane.showMessageDialog(frame,"Rating saved!");

            }catch(Exception ex){

                JOptionPane.showMessageDialog(frame,"Invalid rating");

            }

        });

        profileBtn.addActionListener(e -> {
            frame.setVisible(false);
            showProfile(frame);
        });

        frame.setVisible(true);
    }

    // -----------------------------
    // PROFILE WINDOW
    // -----------------------------
    static void showProfile(JFrame mainFrame){

        JFrame frame = new JFrame(currentUser.username + " Profile");
        frame.setSize(400,400);
        frame.setLayout(new BorderLayout());

        DefaultListModel<String> model = new DefaultListModel<>();

        for(Rating r : currentUser.ratings){
            String itemName = "";
            if(r.item instanceof Album){
                Album album = (Album) r.item;
                itemName = album.title + " (Album) - " + album.artist;
            } else if(r.item instanceof Song){
                Song song = (Song) r.item;
                itemName = song.title + " (Song) - " + song.artist;
            }
            model.addElement(itemName + " - " + r.rating + "/5");
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

        frame.setVisible(true);
    }

    // -----------------------------
    // DATA CLASSES
    // -----------------------------

    static class User{

        String username;
        String password;
        ArrayList<Rating> ratings = new ArrayList<>();

        User(String u,String p){
            username = u;
            password = p;
        }

    }

    static class Album{

        String title;
        String artist;

        Album(String t,String a){
            title = t;
            artist = a;
        }

        public String toString(){
            return title + " - " + artist;
        }

    }

    static class Song{

        String title;
        String artist;
        String album;

        Song(String t,String a,String alb){
            title = t;
            artist = a;
            album = alb;
        }

        public String toString(){
            return title + " - " + artist + " (" + album + ")";
        }

    }

    static class Rating{

        Object item; // Can be Album or Song
        int rating;

        Rating(Object i,int r){
            item = i;
            rating = r;
        }

    }

}