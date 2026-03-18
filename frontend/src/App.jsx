/*
 * Elgooners Record Shelf - Frontend (React)
 * ------------------------------------------
 * HOW TO RUN:
 *   1. Open a terminal in the "frontend" folder
 *   2. Run: npm install   (only needed the first time)
 *   3. Run: npm start
 *   4. App opens at http://localhost:3000
 *
 * HOW THIS FILE IS ORGANIZED:
 *   1. API functions    - talk to the backend
 *   2. AuthContext      - keeps track of who is logged in
 *   3. Small components - reusable UI pieces (Navbar, StarRating, etc.)
 *   4. Pages            - full pages (ShelfPage, SearchPage, ProfilePage, etc.)
 *   5. App              - the router that connects all pages
 *
 * SCROLL WHEEL:
 *   On the main Shelf page, scroll up/down to flip through albums.
 */

import React, { useState, useEffect, useContext, createContext, useRef } from "react";
import { BrowserRouter, Routes, Route, Link, useNavigate, useParams } from "react-router-dom";

// Load Google Fonts
const link = document.createElement("link");
link.href = "https://fonts.googleapis.com/css2?family=Bebas+Neue&family=DM+Sans:wght@300;400;500;700&display=swap";
link.rel  = "stylesheet";
document.head.appendChild(link);

// ============================================================
// 1. API FUNCTIONS
// All calls to the backend go through these functions.
// Change BASE_URL if your backend runs on a different port.
// ============================================================

const BASE_URL = "http://localhost:8080/api";

// Makes a request to the backend. Attaches the login token if one exists.
async function callApi(path, options = {}) {
    const token = localStorage.getItem("token");
    const response = await fetch(BASE_URL + path, {
        headers: {
            "Content-Type": "application/json",
            ...(token ? { "Authorization": "Bearer " + token } : {}),
        },
        ...options,
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.error || "Something went wrong");
    return data;
}

// Shortcuts for GET and POST requests
function apiGet(path)        { return callApi(path); }
function apiPost(path, body) { return callApi(path, { method: "POST", body: JSON.stringify(body) }); }

// ============================================================
// 2. AUTH CONTEXT
// Stores who is logged in so every component can access it.
// Usage: const { user, login, logout } = useAuth();
//   user = { userId, username, avatarColor } or null if not logged in
// ============================================================

const AuthContext = createContext(null);

function AuthProvider({ children }) {
    // Try to restore the logged-in user from a previous session
    const [user, setUser] = useState(() => {
        const saved = localStorage.getItem("user");
        return saved ? JSON.parse(saved) : null;
    });

    function login(data) {
        const userInfo = { userId: data.userId, username: data.username, avatarColor: data.avatarColor };
        localStorage.setItem("token", data.token);
        localStorage.setItem("user",  JSON.stringify(userInfo));
        setUser(userInfo);
    }

    function logout() {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setUser(null);
    }

    return (
        <AuthContext.Provider value={{ user, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

// Hook to use auth anywhere: const { user } = useAuth();
function useAuth() { return useContext(AuthContext); }

// ============================================================
// 3. SMALL COMPONENTS
// ============================================================

// --- CSS styles (injected once into the page) ---
const css = `
    * { box-sizing: border-box; margin: 0; padding: 0; }

    body {
        font-family: 'DM Sans', sans-serif;
        background: #111;
        color: #f0ede8;
        min-height: 100vh;
    }

    a { color: inherit; text-decoration: none; }

    /* --- Navbar --- */
    .navbar {
        position: fixed; top: 0; left: 0; right: 0; z-index: 100;
        display: flex; align-items: center; justify-content: space-between;
        padding: 0 2rem; height: 58px;
        background: rgba(17,17,17,0.95);
        border-bottom: 1px solid rgba(255,255,255,0.08);
    }
    .navbar-logo { font-family: 'Bebas Neue', sans-serif; font-size: 1.4rem; letter-spacing: 3px; }
    .navbar-logo span { color: #E85D4A; }
    .navbar-links { display: flex; gap: 1.5rem; align-items: center; }
    .navbar-links a, .navbar-links button {
        font-size: 0.78rem; letter-spacing: 1.5px; text-transform: uppercase;
        color: rgba(240,237,232,0.55); background: none; border: none;
        cursor: pointer; font-family: inherit; transition: color 0.2s;
    }
    .navbar-links a:hover, .navbar-links button:hover { color: #f0ede8; }
    .nav-user { color: #E85D4A !important; }

    /* --- Page wrapper --- */
    .page { padding-top: 58px; min-height: 100vh; }

    /* --- Shelf Page --- */
    .shelf-page { display: flex; height: calc(100vh - 58px); overflow: hidden; }

    .stack-panel {
        width: 250px; flex-shrink: 0;
        display: flex; flex-direction: column; align-items: center;
        justify-content: center; gap: 0; position: relative;
        padding: 1rem;
    }
    .scroll-hint {
        position: absolute; bottom: 1.5rem;
        font-size: 0.68rem; letter-spacing: 1px; text-transform: uppercase;
        color: rgba(240,237,232,0.25);
    }
    .stack-wrapper { position: relative; width: 200px; height: 250px; }
    .stack-card {
        position: absolute; width: 200px; height: 200px;
        border-radius: 8px; overflow: hidden; cursor: pointer;
        transition: all 0.4s cubic-bezier(0.34, 1.4, 0.64, 1);
        box-shadow: 0 8px 30px rgba(0,0,0,0.6);
    }
    .stack-card-label {
        position: absolute; bottom: 0; left: 0; right: 0;
        padding: 0.75rem; background: linear-gradient(transparent, rgba(0,0,0,0.7));
    }
    .stack-card-title { font-family: 'Bebas Neue', sans-serif; font-size: 1rem; letter-spacing: 1px; line-height: 1.1; }
    .stack-card-artist { font-size: 0.62rem; letter-spacing: 1px; text-transform: uppercase; opacity: 0.7; }

    .detail-panel { flex: 1; overflow-y: auto; padding: 2.5rem 3rem; }

    .detail-top { display: flex; gap: 2.5rem; align-items: flex-start; margin-bottom: 2.5rem; }

    .album-cover {
        width: 210px; height: 210px; border-radius: 10px; flex-shrink: 0;
        position: relative; overflow: hidden;
        box-shadow: 0 16px 50px rgba(0,0,0,0.7);
    }
    .vinyl-disc {
        position: absolute; right: -45px; top: 5px;
        width: 200px; height: 200px; border-radius: 50%;
        background: radial-gradient(circle,
            #111 18%, #222 19%, #111 28%,
            #1a1a1a 29%, #111 40%, #222 41%,
            #111 55%, #333 100%);
        transition: transform 0.5s ease;
    }
    .album-cover:hover .vinyl-disc { transform: translateX(-12px) rotate(20deg); }

    .detail-info { flex: 1; }
    .detail-title { font-family: 'Bebas Neue', sans-serif; font-size: 2.8rem; line-height: 1; letter-spacing: 2px; margin-bottom: 4px; }
    .detail-artist { font-size: 0.85rem; letter-spacing: 2px; text-transform: uppercase; color: rgba(240,237,232,0.45); margin-bottom: 1.25rem; }

    .tags { display: flex; gap: 0.6rem; flex-wrap: wrap; margin-bottom: 1.25rem; }
    .tag { font-size: 0.68rem; letter-spacing: 1.5px; text-transform: uppercase; padding: 0.28rem 0.7rem; border-radius: 100px; background: rgba(255,255,255,0.07); color: rgba(240,237,232,0.6); }

    .detail-description { font-size: 0.88rem; line-height: 1.65; color: rgba(240,237,232,0.55); max-width: 480px; margin-bottom: 1.5rem; }

    .community-rating { font-size: 0.75rem; color: rgba(240,237,232,0.35); letter-spacing: 1px; margin-bottom: 1.25rem; }
    .community-rating strong { color: #E85D4A; }

    /* --- Star Rating --- */
    .stars { display: flex; gap: 5px; align-items: center; margin-bottom: 1rem; }
    .star { font-size: 1.5rem; cursor: pointer; transition: transform 0.12s; color: #444; }
    .star.on  { color: #E85D4A; }
    .star:hover { transform: scale(1.2); }
    .rate-label { font-size: 0.7rem; letter-spacing: 1px; text-transform: uppercase; color: rgba(240,237,232,0.35); margin-left: 6px; }
    .saved-msg { font-size: 0.72rem; color: #4ECDC4; letter-spacing: 1px; }

    /* --- Track list --- */
    .section-title { font-family: 'Bebas Neue', sans-serif; font-size: 1rem; letter-spacing: 3px; color: rgba(240,237,232,0.3); margin-bottom: 0.75rem; border-bottom: 1px solid rgba(255,255,255,0.05); padding-bottom: 0.5rem; }
    .track { display: flex; align-items: center; gap: 1rem; padding: 0.55rem 0.6rem; border-radius: 6px; transition: background 0.15s; }
    .track:hover { background: rgba(255,255,255,0.04); }
    .track-num { width: 18px; text-align: right; font-size: 0.72rem; color: rgba(240,237,232,0.28); }
    .track-title { flex: 1; font-size: 0.88rem; }
    .track-dur { font-size: 0.72rem; color: rgba(240,237,232,0.35); }

    /* --- Search Page --- */
    .search-page { padding: 78px 3rem 3rem; }
    .search-input {
        width: 100%; max-width: 580px; display: block; margin: 0 auto 2.5rem;
        padding: 0.9rem 1.25rem; font-size: 1rem;
        background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
        border-radius: 8px; color: #f0ede8; font-family: inherit; outline: none;
    }
    .search-input:focus { border-color: #E85D4A; }
    .search-input::placeholder { color: rgba(240,237,232,0.28); }
    .album-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(155px, 1fr)); gap: 1.2rem; }
    .album-card { cursor: pointer; border-radius: 8px; overflow: hidden; transition: transform 0.2s, box-shadow 0.2s; }
    .album-card:hover { transform: translateY(-4px); box-shadow: 0 10px 28px rgba(0,0,0,0.5); }
    .album-card-art { height: 155px; }
    .album-card-info { padding: 0.5rem 0; }
    .album-card-title { font-size: 0.82rem; font-weight: 500; line-height: 1.3; }
    .album-card-artist { font-size: 0.7rem; color: rgba(240,237,232,0.45); margin-top: 2px; }
    .album-card-rating { font-size: 0.68rem; color: #E85D4A; margin-top: 4px; }

    /* --- Profile Page --- */
    .profile-page { padding: 78px 3rem 3rem; max-width: 860px; margin: 0 auto; }
    .profile-top { display: flex; gap: 1.5rem; align-items: center; margin-bottom: 2.5rem; }
    .avatar { width: 72px; height: 72px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-family: 'Bebas Neue', sans-serif; font-size: 1.8rem; flex-shrink: 0; }
    .profile-name { font-family: 'Bebas Neue', sans-serif; font-size: 2.2rem; letter-spacing: 2px; }
    .profile-rating-count { font-size: 0.75rem; color: rgba(240,237,232,0.35); letter-spacing: 1px; margin-top: 4px; }
    .profile-rating-count strong { color: #E85D4A; font-size: 1rem; }
    .rating-row { display: flex; align-items: center; gap: 1rem; padding: 0.8rem 0.75rem; border-radius: 7px; transition: background 0.15s; }
    .rating-row:hover { background: rgba(255,255,255,0.04); }
    .rating-art { width: 42px; height: 42px; border-radius: 5px; flex-shrink: 0; }
    .rating-album-title { font-size: 0.88rem; font-weight: 500; }
    .rating-album-artist { font-size: 0.7rem; color: rgba(240,237,232,0.4); }
    .rating-stars { font-size: 0.85rem; color: #E85D4A; margin-left: auto; flex-shrink: 0; }

    /* --- Auth Pages --- */
    .auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; }
    .auth-card { width: 100%; max-width: 370px; padding: 2.5rem 2rem; background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.08); border-radius: 14px; }
    .auth-logo { font-family: 'Bebas Neue', sans-serif; font-size: 1.6rem; letter-spacing: 4px; text-align: center; margin-bottom: 1.75rem; }
    .auth-logo span { color: #E85D4A; }
    .auth-heading { font-size: 1.25rem; font-weight: 300; margin-bottom: 1.75rem; }
    .field { margin-bottom: 1.1rem; }
    .field label { display: block; font-size: 0.68rem; letter-spacing: 1.5px; text-transform: uppercase; color: rgba(240,237,232,0.45); margin-bottom: 0.45rem; }
    .field input { width: 100%; padding: 0.8rem 0.9rem; border-radius: 7px; background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1); color: #f0ede8; font-size: 0.92rem; font-family: inherit; outline: none; transition: border-color 0.2s; }
    .field input:focus { border-color: #E85D4A; }
    .submit-btn { width: 100%; padding: 0.85rem; margin-top: 0.5rem; border: none; border-radius: 7px; background: #E85D4A; color: #fff; font-size: 0.82rem; letter-spacing: 2px; text-transform: uppercase; cursor: pointer; font-family: inherit; font-weight: 500; transition: opacity 0.2s; }
    .submit-btn:hover { opacity: 0.88; }
    .submit-btn:disabled { opacity: 0.4; cursor: default; }
    .form-error { font-size: 0.78rem; color: #E85D4A; text-align: center; margin-top: 0.75rem; }
    .form-footer { text-align: center; margin-top: 1.25rem; font-size: 0.78rem; color: rgba(240,237,232,0.38); }
    .form-footer a { color: #E85D4A; }

    /* --- Misc --- */
    .loading { text-align: center; padding: 4rem; color: rgba(240,237,232,0.28); letter-spacing: 2px; font-size: 0.82rem; }
    .sign-in-prompt { font-size: 0.75rem; color: rgba(240,237,232,0.4); }
    .sign-in-prompt a { color: #E85D4A; }
    .empty { text-align: center; color: rgba(240,237,232,0.28); padding: 3rem; font-size: 0.85rem; }
`;

function GlobalStyles() {
    useEffect(() => {
        const el = document.createElement("style");
        el.textContent = css;
        document.head.appendChild(el);
        return () => document.head.removeChild(el);
    }, []);
    return null;
}

// --- Navbar ---
function Navbar() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    return (
        <nav className="navbar">
            <Link to="/" className="navbar-logo">ELGOON<span>ERS</span></Link>
            <div className="navbar-links">
                <Link to="/">Shelf</Link>
                <Link to="/search">Search</Link>
                {user ? (
                    <>
                        <Link to={"/profile/" + user.username} className="nav-user">{user.username}</Link>
                        <button onClick={() => { logout(); navigate("/"); }}>Sign Out</button>
                    </>
                ) : (
                    <>
                        <Link to="/login">Sign In</Link>
                        <Link to="/signup">Join</Link>
                    </>
                )}
            </div>
        </nav>
    );
}

// --- Album art (coloured gradient box since we don't have real images) ---
function AlbumArt({ color1, color2, size, style = {} }) {
    return (
        <div style={{
            width: size, height: size,
            background: `linear-gradient(135deg, ${color1 || "#333"}, ${color2 || "#555"})`,
            ...style,
        }} />
    );
}

// --- 5-star rating widget ---
function StarRating({ value, onChange }) {
    const [hovered, setHovered] = useState(0);
    const labels = ["", "Awful", "Poor", "Good", "Great", "Perfect"];

    return (
        <div className="stars">
            {[1, 2, 3, 4, 5].map(n => (
                <span
                    key={n}
                    className={"star" + (n <= (hovered || value) ? " on" : "")}
                    onMouseEnter={() => setHovered(n)}
                    onMouseLeave={() => setHovered(0)}
                    onClick={() => onChange && onChange(n)}
                >★</span>
            ))}
            <span className="rate-label">{labels[hovered || value] || "Rate this album"}</span>
        </div>
    );
}

// Converts seconds to "3:45" format
function formatTime(seconds) {
    if (!seconds) return "";
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
}

// ============================================================
// 4. PAGES
// ============================================================

// --- Shelf Page ---
// Main page. Shows a stack of album cards on the left.
// Scroll up/down to flip through albums.
// The right side shows the selected album's details.
function ShelfPage() {
    const [albums, setAlbums]       = useState([]);
    const [activeIndex, setActiveIndex] = useState(0);
    const [detail, setDetail]       = useState(null);
    const [myRating, setMyRating]   = useState(0);
    const [savedMsg, setSavedMsg]   = useState(false);
    const [loading, setLoading]     = useState(true);
    const { user } = useAuth();
    const pageRef  = useRef(null);
    const scrollBuf = useRef(0); // accumulates scroll distance

    // Load all albums when the page opens
    useEffect(() => {
        apiGet("/albums")
            .then(data => setAlbums(data))
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, []);

    // Load detail + my rating whenever the active album changes
    useEffect(() => {
        if (albums.length === 0) return;
        const album = albums[activeIndex];
        setDetail(null);
        setMyRating(0);
        setSavedMsg(false);

        apiGet("/albums/" + album.id).then(data => setDetail(data));

        if (user) {
            apiGet("/ratings/" + album.id)
                .then(data => setMyRating(data.stars || 0))
                .catch(() => {});
        }
    }, [activeIndex, albums, user]);

    // Scroll wheel: accumulate scroll and flip album every ~80px
    useEffect(() => {
        const el = pageRef.current;
        if (!el) return;

        function onWheel(e) {
            e.preventDefault();
            scrollBuf.current += e.deltaY;
            if (scrollBuf.current > 80) {
                scrollBuf.current = 0;
                setActiveIndex(i => Math.min(i + 1, albums.length - 1));
            } else if (scrollBuf.current < -80) {
                scrollBuf.current = 0;
                setActiveIndex(i => Math.max(i - 1, 0));
            }
        }

        el.addEventListener("wheel", onWheel, { passive: false });
        return () => el.removeEventListener("wheel", onWheel);
    }, [albums.length]);

    async function handleRate(stars) {
        if (!user) { window.location.href = "/login"; return; }
        setMyRating(stars);
        try {
            await apiPost("/ratings/" + albums[activeIndex].id, { stars });
            setSavedMsg(true);
            // Refresh to show updated community average
            apiGet("/albums/" + albums[activeIndex].id).then(setDetail);
            setTimeout(() => setSavedMsg(false), 2500);
        } catch (err) {
            console.error(err);
        }
    }

    if (loading) return <div className="page loading">LOADING RECORDS…</div>;
    if (albums.length === 0) return <div className="page loading">No albums found. Run database/setup.py first.</div>;

    return (
        <div className="page shelf-page" ref={pageRef}>

            {/* LEFT PANEL - stacked album cards */}
            <div className="stack-panel">
                <div className="stack-wrapper">
                    {/* Show 5 cards: 2 behind, current, 2 in front */}
                    {[-2, -1, 0, 1, 2].map(offset => {
                        const index = activeIndex + offset;
                        if (index < 0 || index >= albums.length) return null;
                        const album    = albums[index];
                        const isActive = offset === 0;
                        const yMove    = offset * 40;
                        const scale    = isActive ? 1 : 1 - Math.abs(offset) * 0.07;
                        const opacity  = isActive ? 1 : 1 - Math.abs(offset) * 0.22;
                        const zIndex   = 10 - Math.abs(offset);
                        const rotate   = offset * -1.5;

                        return (
                            <div
                                key={album.id}
                                className="stack-card"
                                onClick={() => setActiveIndex(index)}
                                style={{
                                    background: `linear-gradient(135deg, ${album.color1}, ${album.color2})`,
                                    transform: `translateY(${yMove}%) scale(${scale}) rotate(${rotate}deg)`,
                                    zIndex, opacity, top: "25px",
                                }}
                            >
                                {isActive && (
                                    <div className="stack-card-label">
                                        <div className="stack-card-title">{album.title}</div>
                                        <div className="stack-card-artist">{album.artist}</div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </div>
                <div className="scroll-hint">↑ ↓ scroll to browse</div>
            </div>

            {/* RIGHT PANEL - album detail */}
            <div className="detail-panel">
                {!detail ? (
                    <div className="loading">LOADING…</div>
                ) : (
                    <>
                        <div className="detail-top">
                            {/* Album cover with sliding vinyl effect */}
                            <div className="album-cover">
                                <AlbumArt color1={detail.color1} color2={detail.color2} size={210} style={{ borderRadius: 10 }} />
                                <div className="vinyl-disc" />
                            </div>

                            <div className="detail-info">
                                <div className="detail-title">{detail.title}</div>
                                <div className="detail-artist">{detail.artist}</div>

                                <div className="tags">
                                    {detail.releaseYear    && <span className="tag">{detail.releaseYear}</span>}
                                    {detail.genre          && <span className="tag">{detail.genre}</span>}
                                    {detail.runtimeMinutes && <span className="tag">{detail.runtimeMinutes} min</span>}
                                </div>

                                {detail.description && (
                                    <p className="detail-description">{detail.description}</p>
                                )}

                                {/* Community average */}
                                <div className="community-rating">
                                    {detail.avgRating
                                        ? <><strong>★ {Number(detail.avgRating).toFixed(1)}</strong> avg · {detail.ratingCount} ratings</>
                                        : "No ratings yet — be the first!"}
                                </div>

                                {/* Your rating */}
                                {user ? (
                                    <>
                                        <div style={{ fontSize: "0.7rem", letterSpacing: "1.5px", textTransform: "uppercase", color: "rgba(240,237,232,0.35)", marginBottom: "0.4rem" }}>
                                            Your Rating
                                        </div>
                                        <StarRating value={myRating} onChange={handleRate} />
                                        {savedMsg && <div className="saved-msg">✓ Saved!</div>}
                                    </>
                                ) : (
                                    <div className="sign-in-prompt">
                                        <Link to="/login">Sign in</Link> to rate this album
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Track list */}
                        {detail.songs && detail.songs.length > 0 && (
                            <>
                                <div className="section-title">Track List</div>
                                <div>
                                    {detail.songs.map(song => (
                                        <div key={song.id} className="track">
                                            <span className="track-num">{song.trackNumber}</span>
                                            <span className="track-title">{song.title}</span>
                                            <span className="track-dur">{formatTime(song.durationSeconds)}</span>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}

// --- Search Page ---
function SearchPage() {
    const [query,   setQuery]   = useState("");
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    // Search 350ms after the user stops typing
    useEffect(() => {
        if (!query.trim()) { setResults([]); return; }
        const timer = setTimeout(() => {
            setLoading(true);
            apiGet("/search?q=" + encodeURIComponent(query))
                .then(data => setResults(data.albums || []))
                .catch(err => console.error(err))
                .finally(() => setLoading(false));
        }, 350);
        return () => clearTimeout(timer); // cancel if user keeps typing
    }, [query]);

    return (
        <div className="page search-page">
            <input
                className="search-input"
                placeholder="Search albums, artists, genres…"
                value={query}
                onChange={e => setQuery(e.target.value)}
                autoFocus
            />

            {loading && <div className="loading">Searching…</div>}

            {!loading && query && results.length === 0 && (
                <div className="empty">No results for "{query}"</div>
            )}

            {!loading && !query && (
                <div className="empty">Type something to search</div>
            )}

            <div className="album-grid">
                {results.map(album => (
                    <div key={album.id} className="album-card" onClick={() => navigate("/")}>
                        <AlbumArt color1={album.color1} color2={album.color2} size="100%" style={{ height: 155 }} />
                        <div className="album-card-info">
                            <div className="album-card-title">{album.title}</div>
                            <div className="album-card-artist">{album.artist} · {album.releaseYear}</div>
                            {album.avgRating && (
                                <div className="album-card-rating">★ {Number(album.avgRating).toFixed(1)}</div>
                            )}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

// --- Profile Page ---
function ProfilePage() {
    const { username } = useParams(); // gets the username from the URL
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error,   setError]   = useState(null);

    useEffect(() => {
        apiGet("/profile/" + username)
            .then(data => setProfile(data))
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    }, [username]);

    if (loading) return <div className="page loading">Loading profile…</div>;
    if (error)   return <div className="page empty">Profile not found.</div>;

    return (
        <div className="page profile-page">
            <div className="profile-top">
                <div className="avatar" style={{ background: profile.avatarColor }}>
                    {profile.username[0].toUpperCase()}
                </div>
                <div>
                    <div className="profile-name">{profile.username}</div>
                    <div className="profile-rating-count">
                        <strong>{profile.ratingCount}</strong> album ratings
                    </div>
                </div>
            </div>

            <div className="section-title" style={{ marginBottom: "1rem" }}>Rated Albums</div>

            {profile.ratings.length === 0 && (
                <div className="empty">No ratings yet.</div>
            )}

            {profile.ratings.map((r, i) => (
                <div key={i} className="rating-row">
                    <div
                        className="rating-art"
                        style={{ background: `linear-gradient(135deg, ${r.color1}, ${r.color2})` }}
                    />
                    <div>
                        <div className="rating-album-title">{r.title}</div>
                        <div className="rating-album-artist">{r.artist}</div>
                    </div>
                    <div className="rating-stars">{"★".repeat(r.stars)}</div>
                </div>
            ))}
        </div>
    );
}

// --- Signup Page ---
function SignupPage() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error,    setError]    = useState("");
    const [loading,  setLoading]  = useState(false);
    const { login } = useAuth();
    const navigate  = useNavigate();

    async function handleSubmit(e) {
        e.preventDefault();
        setError("");
        setLoading(true);
        try {
            const data = await apiPost("/auth/signup", { username, password });
            login(data);
            navigate("/");
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="auth-page">
            <div className="auth-card">
                <div className="auth-logo">ELGOON<span>ERS</span></div>
                <div className="auth-heading">Create your account</div>
                <form onSubmit={handleSubmit}>
                    <div className="field">
                        <label>Username</label>
                        <input value={username} onChange={e => setUsername(e.target.value)} placeholder="e.g. vinyl_lover" autoFocus />
                    </div>
                    <div className="field">
                        <label>Password</label>
                        <input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 6 characters" />
                    </div>
                    {error && <div className="form-error">{error}</div>}
                    <button className="submit-btn" type="submit" disabled={loading}>
                        {loading ? "Creating account…" : "Join the shelf"}
                    </button>
                </form>
                <div className="form-footer">
                    Already have an account? <Link to="/login">Sign in</Link>
                </div>
            </div>
        </div>
    );
}

// --- Login Page ---
function LoginPage() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error,    setError]    = useState("");
    const [loading,  setLoading]  = useState(false);
    const { login } = useAuth();
    const navigate  = useNavigate();

    async function handleSubmit(e) {
        e.preventDefault();
        setError("");
        setLoading(true);
        try {
            const data = await apiPost("/auth/login", { username, password });
            login(data);
            navigate("/");
        } catch (err) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    }

    return (
        <div className="auth-page">
            <div className="auth-card">
                <div className="auth-logo">ELGOON<span>ERS</span></div>
                <div className="auth-heading">Welcome back</div>
                <form onSubmit={handleSubmit}>
                    <div className="field">
                        <label>Username</label>
                        <input value={username} onChange={e => setUsername(e.target.value)} autoFocus />
                    </div>
                    <div className="field">
                        <label>Password</label>
                        <input type="password" value={password} onChange={e => setPassword(e.target.value)} />
                    </div>
                    {error && <div className="form-error">{error}</div>}
                    <button className="submit-btn" type="submit" disabled={loading}>
                        {loading ? "Signing in…" : "Sign in"}
                    </button>
                </form>
                <div className="form-footer">
                    New here? <Link to="/signup">Create an account</Link>
                </div>
            </div>
        </div>
    );
}

// ============================================================
// 5. APP / ROUTER
// Connects all the pages together.
// To add a new page: add a new <Route> below.
// ============================================================

export default function App() {
    return (
        <BrowserRouter>
            <AuthProvider>
                <GlobalStyles />
                <Navbar />
                <Routes>
                    <Route path="/"                   element={<ShelfPage />} />
                    <Route path="/search"             element={<SearchPage />} />
                    <Route path="/profile/:username"  element={<ProfilePage />} />
                    <Route path="/signup"             element={<SignupPage />} />
                    <Route path="/login"              element={<LoginPage />} />
                </Routes>
            </AuthProvider>
        </BrowserRouter>
    );
}
