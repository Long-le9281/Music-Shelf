"""
Elgooners Record Shelf - Database Setup
Run this first before starting the backend!

How to run:
    python setup.py

This creates a file called elgooners.db with sample albums.
If something breaks, just delete elgooners.db and run this again.
"""

import sqlite3

# Connect to the database (creates the file if it doesn't exist)
conn = sqlite3.connect("elgooners.db")
cursor = conn.cursor()

# -------------------------------------------------------
# CREATE TABLES
# -------------------------------------------------------

# Users table - stores account info
cursor.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id           INTEGER PRIMARY KEY AUTOINCREMENT,
        username     TEXT UNIQUE NOT NULL,
        password     TEXT NOT NULL,
        avatar_color TEXT DEFAULT '#FF6B6B'
    )
""")

# Albums table - the music catalog
cursor.execute("""
    CREATE TABLE IF NOT EXISTS albums (
        id           INTEGER PRIMARY KEY AUTOINCREMENT,
        title        TEXT NOT NULL,
        artist       TEXT NOT NULL,
        release_year INTEGER,
        genre        TEXT,
        description  TEXT,
        color1       TEXT DEFAULT '#333333',
        color2       TEXT DEFAULT '#555555'
    )
""")

# Songs table - tracks within albums
cursor.execute("""
    CREATE TABLE IF NOT EXISTS songs (
        id               INTEGER PRIMARY KEY AUTOINCREMENT,
        album_id         INTEGER,
        title            TEXT NOT NULL,
        track_number     INTEGER,
        duration_seconds INTEGER
    )
""")

# Ratings table - user ratings for albums (1 to 5 stars)
cursor.execute("""
    CREATE TABLE IF NOT EXISTS ratings (
        id         INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id    INTEGER NOT NULL,
        album_id   INTEGER NOT NULL,
        stars      INTEGER NOT NULL,
        UNIQUE(user_id, album_id)
    )
""")

conn.commit()
print("Tables created!")

# -------------------------------------------------------
# ADD SAMPLE ALBUMS
# -------------------------------------------------------

# Check if we already added albums so we don't add duplicates
cursor.execute("SELECT COUNT(*) FROM albums")
album_count = cursor.fetchone()[0]

if album_count > 0:
    print("Albums already added, skipping.")
else:
    # List of albums: (title, artist, year, genre, description, color1, color2)
    sample_albums = [
        ("Abbey Road",                     "The Beatles",        1969, "Rock",       "Iconic swan song with the legendary medley.",        "#2C3E50", "#3498DB"),
        ("Thriller",                       "Michael Jackson",    1982, "Pop / R&B",  "Best-selling album of all time.",                    "#1A1A2E", "#E94560"),
        ("The Dark Side of the Moon",      "Pink Floyd",         1973, "Prog Rock",  "A concept album exploring time and mental illness.", "#0D0D0D", "#8E44AD"),
        ("Rumours",                        "Fleetwood Mac",      1977, "Soft Rock",  "Heartbreak and beauty from personal turmoil.",       "#C0392B", "#F39C12"),
        ("Kind of Blue",                   "Miles Davis",        1959, "Jazz",       "The definitive jazz album.",                         "#1ABC9C", "#2C3E50"),
        ("Purple Rain",                    "Prince",             1984, "Pop / Funk", "Prince's magnum opus blending rock and R&B.",        "#6C3483", "#C0392B"),
        ("Born to Run",                    "Bruce Springsteen",  1975, "Rock",       "The sound of escape and big dreams.",                "#E67E22", "#2C3E50"),
        ("Nevermind",                      "Nirvana",            1991, "Grunge",     "Grunge explosion that changed rock forever.",        "#3498DB", "#2ECC71"),
        ("Achtung Baby",                   "U2",                 1991, "Alt Rock",   "U2's dark industrial reinvention.",                  "#E74C3C", "#8E44AD"),
        ("Songs in the Key of Life",       "Stevie Wonder",      1976, "Soul / R&B", "A joyful sprawling masterpiece of soul.",            "#F1C40F", "#E74C3C"),
        ("The Miseducation of Lauryn Hill","Lauryn Hill",        1998, "Hip-Hop",    "A deeply personal blend of hip-hop and soul.",       "#27AE60", "#F39C12"),
        ("OK Computer",                    "Radiohead",          1997, "Alt Rock",   "Anxious, dystopian, and utterly beautiful.",         "#2C3E50", "#27AE60"),
        ("What's Going On",                "Marvin Gaye",        1971, "Soul",       "Socially conscious soul unlike anything before.",    "#16A085", "#2980B9"),
        ("Illmatic",                       "Nas",                1994, "Hip-Hop",    "The gold standard of East Coast hip-hop.",           "#1A1A2E", "#C0392B"),
        ("Graceland",                      "Paul Simon",         1986, "World / Pop","African rhythms meet American folk.",                "#F9E79F", "#E67E22"),
        ("Appetite for Destruction",       "Guns N' Roses",      1987, "Hard Rock",  "Explosive debut that redefined hard rock.",          "#E74C3C", "#E67E22"),
        ("Lemonade",                       "Beyonce",            2016, "R&B / Pop",  "A journey through love, loss, and identity.",        "#F4D03F", "#E74C3C"),
        ("To Pimp a Butterfly",            "Kendrick Lamar",     2015, "Hip-Hop",    "A jazz-funk odyssey on race and identity.",          "#1A1A2E", "#F39C12"),
        ("Random Access Memories",         "Daft Punk",          2013, "Electronic", "A love letter to 70s disco.",                        "#D4AC0D", "#2C3E50"),
        ("21",                             "Adele",              2011, "Soul / Pop", "Heartbreak distilled into powerful vocals.",         "#BDC3C7", "#E74C3C"),
        ("In Rainbows",                    "Radiohead",          2007, "Art Rock",   "Warm, intimate, and quietly devastating.",           "#1ABC9C", "#8E44AD"),
        ("Blonde on Blonde",               "Bob Dylan",          1966, "Folk Rock",  "Dylan's double LP masterpiece.",                     "#D35400", "#F7DC6F"),
        ("Clapton",                        "Eric Clapton",       2010, "Blues",      "Clapton reimagines classic blues standards.",        "#5D6D7E", "#A93226"),
        ("Careless Love",                  "Madeleine Peyroux",  2004, "Jazz",       "Silky voice channeling Billie Holiday.",             "#A9CCE3", "#1A5276"),
        ("Born This Way",                  "Lady Gaga",          2011, "Pop",        "Anthemic pop about identity and freedom.",           "#8E44AD", "#3498DB"),
    ]

    cursor.executemany("""
        INSERT INTO albums (title, artist, release_year, genre, description, color1, color2)
        VALUES (?, ?, ?, ?, ?, ?, ?)
    """, sample_albums)

    conn.commit()
    print(f"Added {len(sample_albums)} albums!")

# Add sample songs for Abbey Road
cursor.execute("SELECT COUNT(*) FROM songs")
if cursor.fetchone()[0] == 0:
    cursor.execute("SELECT id FROM albums WHERE title = 'Abbey Road'")
    row = cursor.fetchone()
    if row:
        abbey_id = row[0]
        songs = [
            (abbey_id, "Come Together",     1, 259),
            (abbey_id, "Something",          2, 182),
            (abbey_id, "Here Comes the Sun", 3, 185),
            (abbey_id, "Octopus's Garden",   4, 170),
            (abbey_id, "Let It Be",          5, 243),
        ]
        cursor.executemany("""
            INSERT INTO songs (album_id, title, track_number, duration_seconds)
            VALUES (?, ?, ?, ?)
        """, songs)
        conn.commit()
        print("Added sample songs!")

conn.close()
print("\nDone! Database is ready. Now start the backend in IntelliJ.")
