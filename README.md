# Music-Shelf

A Java-based music management application with a graphical user interface (GUI) that allows users to browse, organize, and manage their music collections. Users can create accounts, log in, and explore a catalog of albums and songs.

## Features

- **User Authentication**: Create accounts and log in with persistent user data storage
- **Album & Song Management**: Browse a collection of albums and songs with artist information
- **Search Functionality**: Search albums and songs by title, artist, or album name
- **Rating System**: Rate albums and songs on a 1-5 scale
- **User Profile**: View your personal ratings and history
- **Persistent Data**: User credentials are saved to `users.csv` for returning users
- **Graphical Interface**: Built with Java Swing for an intuitive user experience
- **Demo Data**: Pre-loaded sample albums and songs to explore on startup

## Project Structure

```
Music-Shelf/
├── README.md                          # This file
├── users.csv                          # User credentials database
└── RecordShelfDemo/
    ├── RecordShelfDemo.iml            # IntelliJ project file
    ├── RecordShelfDemo.java           # Main application entry point
    └── src/
        └── RecordShelfDemo.java       # Source code
```

## How to Run

1. **Prerequisites**: Java Development Kit (JDK) installed on your system
2. **Navigate** to the project directory:
   ```
   cd RecordShelfDemo
   ```
3. **Compile** the Java files:
   ```
   javac src/RecordShelfDemo.java
   ```
4. **Run** the application from the RecordShelfDemo directory (so users.csv is found):
   ```
   java -cp src RecordShelfDemo
   ```

## File Descriptions

- **users.csv**: Stores user account information in CSV format with columns for username and password
- **RecordShelfDemo.java**: Main application class containing:
  - User login and registration system
  - Loading/saving user data from `users.csv`
  - Search functionality for albums and songs
  - Rating system for albums and songs
  - User profile view showing personal ratings
  - Java Swing GUI components for all windows (login, main app, profile)
  - Inner data classes: `User`, `Album`, `Song`, and `Rating`

## Sample Data

The application comes with pre-loaded albums and songs including:
- Abbey Road (The Beatles)
- Thriller (Michael Jackson)
- Back in Black (AC/DC)
- Random Access Memories (Daft Punk)

## Application Screenshots

<img width="900" height="558" alt="image" src="https://github.com/user-attachments/assets/1458274f-cf4a-4770-9b0d-5979ad15fc8e" />
<img width="812" height="619" alt="image" src="https://github.com/user-attachments/assets/0ec3cd8b-501d-4a6a-b6ee-8ae1200a3a23" />