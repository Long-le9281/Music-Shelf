"""Elgooners Record Shelf - additive DB setup with optional lyrics + album art fetch.

How to run:
    python setup.py

What this script does:
  - Creates required tables if missing.
  - Applies additive migrations for newer columns.
  - Seeds albums + songs idempotently (safe to re-run).
  - Tries fetching album art (iTunes Search API) and lyrics (lyrics.ovh).
"""

from __future__ import annotations

import json
import sqlite3
import urllib.parse
import urllib.request


def fetch_json(url: str, timeout: int = 8):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None


def fetch_album_art(artist: str, album: str) -> str:
    term = urllib.parse.quote_plus(f"{artist} {album}")
    url = f"https://itunes.apple.com/search?term={term}&entity=album&limit=1"
    payload = fetch_json(url)
    if not payload or not payload.get("results"):
        return ""
    art = payload["results"][0].get("artworkUrl100", "")
    return art.replace("100x100bb", "600x600bb") if art else ""


def fetch_lyrics(artist: str, title: str) -> str:
    artist_q = urllib.parse.quote(artist)
    title_q = urllib.parse.quote(title)
    payload = fetch_json(f"https://api.lyrics.ovh/v1/{artist_q}/{title_q}")
    if not payload:
        return ""
    lyrics = payload.get("lyrics", "").strip()
    return lyrics[:4000]


def add_column_if_missing(cursor: sqlite3.Cursor, table: str, column: str, ddl: str):
    cursor.execute(f"PRAGMA table_info({table})")
    cols = {row[1].lower() for row in cursor.fetchall()}
    if column.lower() not in cols:
        cursor.execute(f"ALTER TABLE {table} ADD COLUMN {column} {ddl}")


def ensure_schema(cursor: sqlite3.Cursor):
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS users (
            id                INTEGER PRIMARY KEY AUTOINCREMENT,
            username          TEXT UNIQUE NOT NULL,
            password          TEXT NOT NULL,
            avatar_color      TEXT DEFAULT '#FF6B6B',
            is_admin          INTEGER NOT NULL DEFAULT 0,
            bio               TEXT DEFAULT '',
            created_at        TEXT DEFAULT CURRENT_TIMESTAMP,
            display_name      TEXT,
            is_active         INTEGER NOT NULL DEFAULT 1,
            disabled_at       TEXT,
            deleted_at        TEXT,
            password_reset_at TEXT
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS albums (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            title         TEXT NOT NULL,
            artist        TEXT NOT NULL,
            release_year  INTEGER,
            genre         TEXT,
            description   TEXT,
            color1        TEXT DEFAULT '#333333',
            color2        TEXT DEFAULT '#555555',
            album_art_url TEXT DEFAULT '',
            is_single     INTEGER NOT NULL DEFAULT 0
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS songs (
            id               INTEGER PRIMARY KEY AUTOINCREMENT,
            album_id         INTEGER,
            title            TEXT NOT NULL,
            track_number     INTEGER,
            duration_seconds INTEGER,
            lyrics           TEXT DEFAULT ''
        )
        """
    )
    cursor.execute(
        """
        CREATE TABLE IF NOT EXISTS ratings (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id    INTEGER NOT NULL,
            album_id   INTEGER NOT NULL,
            stars      INTEGER NOT NULL,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, album_id)
        )
        """
    )

    # Additive migration strategy for existing DBs.
    add_column_if_missing(cursor, "users", "is_admin", "INTEGER NOT NULL DEFAULT 0")
    add_column_if_missing(cursor, "users", "bio", "TEXT DEFAULT ''")
    add_column_if_missing(cursor, "users", "created_at", "TEXT")
    add_column_if_missing(cursor, "users", "display_name", "TEXT")
    add_column_if_missing(cursor, "users", "is_active", "INTEGER NOT NULL DEFAULT 1")
    add_column_if_missing(cursor, "users", "disabled_at", "TEXT")
    add_column_if_missing(cursor, "users", "deleted_at", "TEXT")
    add_column_if_missing(cursor, "users", "password_reset_at", "TEXT")
    add_column_if_missing(cursor, "albums", "album_art_url", "TEXT DEFAULT ''")
    add_column_if_missing(cursor, "albums", "is_single", "INTEGER NOT NULL DEFAULT 0")
    add_column_if_missing(cursor, "songs", "lyrics", "TEXT DEFAULT ''")
    add_column_if_missing(cursor, "ratings", "updated_at", "TEXT")

    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_album_identity ON albums(title, artist)")
    cursor.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_song_identity ON songs(album_id, track_number, title)")

    cursor.execute("UPDATE users SET created_at = COALESCE(NULLIF(created_at, ''), CURRENT_TIMESTAMP)")
    cursor.execute("UPDATE users SET display_name = COALESCE(NULLIF(display_name, ''), username)")
    cursor.execute("UPDATE users SET bio = COALESCE(bio, '')")
    cursor.execute("UPDATE users SET is_active = COALESCE(is_active, 1)")
    cursor.execute("UPDATE albums SET is_single = COALESCE(is_single, 0)")
    cursor.execute("UPDATE albums SET is_single = 1 WHERE LOWER(TRIM(title)) IN ('single', 'singles')")
    cursor.execute("UPDATE ratings SET updated_at = COALESCE(NULLIF(updated_at, ''), CURRENT_TIMESTAMP)")


def seed_catalog(cursor: sqlite3.Cursor):
    sample_albums = [
        ("Abbey Road", "The Beatles", 1969, "Rock", "Iconic swan song with the legendary medley.", "#2C3E50", "#3498DB"),
        ("Thriller", "Michael Jackson", 1982, "Pop / R&B", "Best-selling album of all time.", "#1A1A2E", "#E94560"),
        ("The Dark Side of the Moon", "Pink Floyd", 1973, "Prog Rock", "A concept album exploring time and mental illness.", "#0D0D0D", "#8E44AD"),
        ("Rumours", "Fleetwood Mac", 1977, "Soft Rock", "Heartbreak and beauty from personal turmoil.", "#C0392B", "#F39C12"),
        ("Kind of Blue", "Miles Davis", 1959, "Jazz", "The definitive jazz album.", "#1ABC9C", "#2C3E50"),
        ("Nevermind", "Nirvana", 1991, "Grunge", "Grunge explosion that changed rock forever.", "#3498DB", "#2ECC71"),
        ("Achtung Baby", "U2", 1991, "Alt Rock", "U2's dark industrial reinvention.", "#E74C3C", "#8E44AD"),
        ("OK Computer", "Radiohead", 1997, "Alt Rock", "Anxious, dystopian, and utterly beautiful.", "#2C3E50", "#27AE60"),
        ("To Pimp a Butterfly", "Kendrick Lamar", 2015, "Hip-Hop", "A jazz-funk odyssey on race and identity.", "#1A1A2E", "#F39C12"),
        ("Random Access Memories", "Daft Punk", 2013, "Electronic", "A love letter to 70s disco.", "#D4AC0D", "#2C3E50"),
    ]

    for title, artist, year, genre, description, color1, color2 in sample_albums:
        cursor.execute(
            """
            INSERT OR IGNORE INTO albums (title, artist, release_year, genre, description, color1, color2, album_art_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, '')
            """,
            (title, artist, year, genre, description, color1, color2),
        )
        cursor.execute(
            """
            UPDATE albums
            SET release_year = COALESCE(release_year, ?),
                genre = COALESCE(NULLIF(genre, ''), ?),
                description = COALESCE(NULLIF(description, ''), ?),
                color1 = COALESCE(NULLIF(color1, ''), ?),
                color2 = COALESCE(NULLIF(color2, ''), ?),
                is_single = 0
            WHERE title = ? AND artist = ?
            """,
            (year, genre, description, color1, color2, title, artist),
        )

        cursor.execute("SELECT id, album_art_url FROM albums WHERE title = ? AND artist = ?", (title, artist))
        album_id, art_url = cursor.fetchone()
        if not art_url:
            fetched = fetch_album_art(artist, title)
            if fetched:
                cursor.execute("UPDATE albums SET album_art_url = ? WHERE id = ?", (fetched, album_id))

    song_seed = {
        "Abbey Road": [
            ("Come Together", 1, 259),
            ("Something", 2, 182),
            ("Here Comes the Sun", 3, 185),
        ],
        "Thriller": [
            ("Thriller", 1, 358),
            ("Beat It", 2, 258),
            ("Billie Jean", 3, 294),
        ],
        "Nevermind": [
            ("Smells Like Teen Spirit", 1, 301),
            ("In Bloom", 2, 254),
            ("Come as You Are", 3, 219),
        ],
        "Random Access Memories": [
            ("Give Life Back to Music", 1, 274),
            ("Get Lucky", 2, 369),
            ("Instant Crush", 3, 337),
        ],
    }

    for album_title, songs in song_seed.items():
        cursor.execute("SELECT id, artist FROM albums WHERE title = ? LIMIT 1", (album_title,))
        row = cursor.fetchone()
        if not row:
            continue
        album_id, artist = row
        for title, track_number, duration_seconds in songs:
            cursor.execute(
                """
                INSERT OR IGNORE INTO songs (album_id, title, track_number, duration_seconds, lyrics)
                VALUES (?, ?, ?, ?, '')
                """,
                (album_id, title, track_number, duration_seconds),
            )
            cursor.execute("SELECT id, lyrics FROM songs WHERE album_id = ? AND title = ? LIMIT 1", (album_id, title))
            song_id, current_lyrics = cursor.fetchone()
            if not current_lyrics:
                lyrics = fetch_lyrics(artist, title)
                if not lyrics:
                    lyrics = (
                        f"Lyrics are not available yet for '{title}'.\n"
                        "This track is included for catalog browsing and can be updated later."
                    )
                cursor.execute("UPDATE songs SET lyrics = ? WHERE id = ?", (lyrics, song_id))

    # Guarantee every non-single album has at least one track so shelf song sections are populated.
    cursor.execute(
        """
        SELECT a.id, a.title
        FROM albums a
        LEFT JOIN songs s ON s.album_id = a.id
        WHERE COALESCE(a.is_single, 0) = 0
        GROUP BY a.id
        HAVING COUNT(s.id) = 0
        """
    )
    for album_id, album_title in cursor.fetchall():
        fallback_title = f"{album_title} (Title Track)"
        cursor.execute(
            """
            INSERT OR IGNORE INTO songs (album_id, title, track_number, duration_seconds, lyrics)
            VALUES (?, ?, 1, 180, ?)
            """,
            (
                album_id,
                fallback_title,
                f"Lyrics are not available yet for '{fallback_title}'.\nThis fallback song keeps album pages non-empty.",
            ),
        )


def main():
    conn = sqlite3.connect("elgooners.db")
    cursor = conn.cursor()
    ensure_schema(cursor)
    seed_catalog(cursor)
    conn.commit()
    conn.close()
    print("Database setup complete: schema migrated, albums/songs seeded, art/lyrics fetched when available.")


if __name__ == "__main__":
    main()
