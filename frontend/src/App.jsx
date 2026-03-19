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
        background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%);
        color: #2c2420;
        min-height: 100vh;
    }

    a { color: inherit; text-decoration: none; }

    /* --- Navbar --- */
    .navbar {
        position: fixed; top: 0; left: 0; right: 0; z-index: 100;
        display: flex; align-items: center; justify-content: space-between;
        padding: 0 2rem; height: 58px;
        background: linear-gradient(180deg, rgba(44,36,32,0.98) 0%, rgba(44,36,32,0.95) 100%);
        border-bottom: 2px solid #8b7355;
        box-shadow: 0 4px 15px rgba(0,0,0,0.1);
    }
    .navbar-logo { font-family: 'Bebas Neue', sans-serif; font-size: 1.4rem; letter-spacing: 3px; color: #f5f1ed; }
    .navbar-logo span { color: #d4744f; }
    .navbar-links { display: flex; gap: 1.5rem; align-items: center; }
    .navbar-links a, .navbar-links button {
        font-size: 0.78rem; letter-spacing: 1.5px; text-transform: uppercase;
        color: rgba(245,241,237,0.7); background: none; border: none;
        cursor: pointer; font-family: inherit; transition: color 0.2s;
    }
    .navbar-links a:hover, .navbar-links button:hover { color: #f5f1ed; }
    .nav-user { color: #d4744f !important; }

    /* --- Page wrapper --- */
    .page { padding-top: 58px; min-height: 100vh; }

    /* --- Shelf Page --- */
    .shelf-page { display: flex; height: calc(100vh - 58px); overflow: hidden; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }

    .stack-panel {
        flex: 0 0 auto; min-width: 380px;
        display: flex; flex-direction: column; align-items: center;
        justify-content: center; gap: 0; position: relative;
        padding: 2rem;
    }
    .scroll-hint {
        position: absolute; bottom: 2rem;
        font-size: 0.68rem; letter-spacing: 1px; text-transform: uppercase;
        color: rgba(44,36,32,0.4);
    }
    .stack-wrapper { position: relative; width: 280px; height: 380px; perspective: 1200px; }
    .stack-card {
        position: absolute; width: 260px; height: 360px;
        border-radius: 12px; overflow: hidden; cursor: pointer;
        transition: all 0.6s cubic-bezier(0.34, 1.4, 0.64, 1);
        box-shadow:
            0 20px 60px rgba(0,0,0,0.25),
            inset 0 1px 0 rgba(255,255,255,0.3);
        background-color: #3a3430;
        border: 3px solid #8b7355;
        transform-origin: center center;
        filter: drop-shadow(0 2px 8px rgba(0,0,0,0.2));
    }
    .stack-card::before {
        content: '';
        position: absolute;
        top: 0; left: 0; right: 0; bottom: 0;
        background: repeating-linear-gradient(
            90deg,
            transparent,
            transparent 2px,
            rgba(139,115,85,0.1) 2px,
            rgba(139,115,85,0.1) 4px
        );
        pointer-events: none;
        opacity: 0.5;
    }
    .stack-card-label {
        position: absolute; bottom: 0; left: 0; right: 0;
        padding: 1rem; background: linear-gradient(transparent, rgba(44,36,32,0.85));
        border-top: 1px solid rgba(255,255,255,0.1);
    }
    .stack-card-title { font-family: 'Bebas Neue', sans-serif; font-size: 1.1rem; letter-spacing: 1px; line-height: 1.1; color: #f5f1ed; }
    .stack-card-artist { font-size: 0.65rem; letter-spacing: 1px; text-transform: uppercase; opacity: 0.7; color: #d4a574; margin-top: 4px; }

    .detail-panel { flex: 1; overflow-y: auto; padding: 2.5rem 3rem; display: flex; flex-direction: column; align-items: center; justify-content: center; }

    .detail-top { display: flex; gap: 3rem; align-items: flex-start; margin-bottom: 2.5rem; max-width: 900px; width: 100%; }

    .album-cover {
        width: 260px; height: 260px; border-radius: 12px; flex-shrink: 0;
        position: relative; overflow: hidden;
        box-shadow:
            0 20px 60px rgba(0,0,0,0.3),
            inset 0 1px 0 rgba(255,255,255,0.2);
        border: 3px solid #8b7355;
    }
    .vinyl-disc {
        position: absolute; right: -50px; top: 5px;
        width: 240px; height: 240px; border-radius: 50%;
        background: radial-gradient(circle,
            #1a1a1a 18%, #3a3a3a 19%, #1a1a1a 28%,
            #2a2a2a 29%, #1a1a1a 40%, #3a3a3a 41%,
            #1a1a1a 55%, #4a4a4a 100%);
        transition: transform 0.5s ease;
        box-shadow: 0 10px 30px rgba(0,0,0,0.4);
    }
    .album-cover:hover .vinyl-disc { transform: translateX(-12px) rotate(20deg); }

    .detail-info { flex: 1; max-width: 500px; }
    .detail-title { font-family: 'Bebas Neue', sans-serif; font-size: 3rem; line-height: 1; letter-spacing: 2px; margin-bottom: 4px; color: #2c2420; }
    .detail-artist { font-size: 0.9rem; letter-spacing: 2px; text-transform: uppercase; color: rgba(44,36,32,0.55); margin-bottom: 1.5rem; }

    .tags { display: flex; gap: 0.6rem; flex-wrap: wrap; margin-bottom: 1.5rem; }
    .tag { font-size: 0.68rem; letter-spacing: 1.5px; text-transform: uppercase; padding: 0.35rem 0.9rem; border-radius: 100px; background: rgba(139,115,85,0.2); color: rgba(44,36,32,0.7); border: 1px solid rgba(139,115,85,0.4); }

    .detail-description { font-size: 0.9rem; line-height: 1.7; color: rgba(44,36,32,0.65); max-width: 480px; margin-bottom: 1.75rem; }

    .community-rating { font-size: 0.8rem; color: rgba(44,36,32,0.55); letter-spacing: 1px; margin-bottom: 1.5rem; }
    .community-rating strong { color: #d4744f; }

    /* --- Star Rating --- */
    .stars { display: flex; gap: 8px; align-items: center; margin-bottom: 1.5rem; }
    .star { font-size: 1.8rem; cursor: pointer; transition: transform 0.12s; color: #c9b5a0; }
    .star.on  { color: #d4744f; }
    .star:hover { transform: scale(1.2); }
    .rate-label { font-size: 0.7rem; letter-spacing: 1px; text-transform: uppercase; color: rgba(44,36,32,0.4); margin-left: 6px; }
    .saved-msg { font-size: 0.72rem; color: #4a7c59; letter-spacing: 1px; }

    /* --- Track list --- */
    .section-title { font-family: 'Bebas Neue', sans-serif; font-size: 1rem; letter-spacing: 3px; color: rgba(44,36,32,0.4); margin-bottom: 0.75rem; border-bottom: 2px solid rgba(139,115,85,0.3); padding-bottom: 0.75rem; }
    .track { display: flex; align-items: center; gap: 1rem; padding: 0.75rem 1rem; border-radius: 8px; transition: all 0.2s; cursor: pointer; border: 1px solid transparent; }
    .track:hover { background: rgba(139,115,85,0.15); border-color: rgba(139,115,85,0.3); }
    .track-num { width: 24px; text-align: right; font-size: 0.75rem; color: rgba(44,36,32,0.4); font-weight: 600; }
    .track-title { flex: 1; font-size: 0.9rem; color: #2c2420; }
    .track-dur { font-size: 0.75rem; color: rgba(44,36,32,0.45); }
    .track-actions { margin-left: auto; display: flex; align-items: center; gap: 0.5rem; }
    .ghost-btn {
        border: 1px solid rgba(139,115,85,0.5);
        background: rgba(255,255,255,0.6);
        color: #2c2420;
        border-radius: 999px;
        font-size: 0.65rem;
        letter-spacing: 1px;
        text-transform: uppercase;
        padding: 0.3rem 0.7rem;
        cursor: pointer;
    }
    .ghost-btn:hover { background: rgba(255,255,255,0.9); }

    .segmented { display: inline-flex; background: rgba(139,115,85,0.16); border: 1px solid rgba(139,115,85,0.35); border-radius: 999px; padding: 0.25rem; gap: 0.2rem; }
    .segmented button {
        border: none;
        background: transparent;
        color: rgba(44,36,32,0.7);
        border-radius: 999px;
        padding: 0.4rem 0.8rem;
        font-size: 0.7rem;
        letter-spacing: 1px;
        text-transform: uppercase;
        cursor: pointer;
    }
    .segmented button.active { background: #2c2420; color: #f5f1ed; }

    .subsection { margin-top: 1.75rem; }

    /* --- Song Detail Modal --- */
    .modal-overlay {
        position: fixed; top: 0; left: 0; right: 0; bottom: 0;
        background: rgba(0,0,0,0.6); display: flex; align-items: center;
        justify-content: center; z-index: 1000;
        animation: fadeIn 0.3s ease;
    }
    @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
    .song-modal {
        background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%);
        border: 3px solid #8b7355;
        border-radius: 16px;
        padding: 2.5rem;
        max-width: 600px;
        width: 90%;
        max-height: 85vh;
        overflow-y: auto;
        box-shadow: 0 25px 80px rgba(0,0,0,0.4);
        animation: slideUp 0.4s cubic-bezier(0.34, 1.4, 0.64, 1);
        position: relative;
    }
    @keyframes slideUp { from { transform: translateY(40px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
    .song-modal-close {
        position: absolute; top: 1.5rem; right: 1.5rem;
        background: rgba(139,115,85,0.2);
        border: none;
        color: #2c2420;
        font-size: 1.5rem;
        cursor: pointer;
        width: 40px; height: 40px;
        border-radius: 50%;
        display: flex; align-items: center; justify-content: center;
        transition: all 0.2s;
    }
    .song-modal-close:hover { background: rgba(139,115,85,0.4); }
    .song-modal-header { margin-bottom: 2rem; }
    .song-modal-number { font-size: 0.75rem; letter-spacing: 2px; text-transform: uppercase; color: rgba(44,36,32,0.5); }
    .song-modal-title { font-family: 'Bebas Neue', sans-serif; font-size: 2rem; letter-spacing: 1px; color: #2c2420; margin-top: 0.5rem; }
    .song-modal-duration { font-size: 0.85rem; color: rgba(44,36,32,0.6); margin-top: 0.75rem; }
    .song-modal-section { margin-bottom: 1.75rem; }
    .song-modal-section-title { font-family: 'Bebas Neue', sans-serif; font-size: 0.95rem; letter-spacing: 2px; text-transform: uppercase; color: rgba(44,36,32,0.5); margin-bottom: 0.75rem; }
    .song-modal-section-content { font-size: 0.9rem; line-height: 1.7; color: #2c2420; }

    /* --- Search Page --- */
    .search-page { padding: 78px 3rem 3rem; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }
    .search-controls { display: flex; align-items: center; justify-content: center; gap: 0.8rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .search-input {
        width: 100%; max-width: 580px; display: block; margin: 0 auto 2.5rem;
        padding: 0.95rem 1.25rem; font-size: 1rem;
        background: linear-gradient(to bottom, rgba(255,255,255,0.8), rgba(255,255,255,0.6));
        border: 2px solid #8b7355;
        border-radius: 10px; color: #2c2420; font-family: inherit; outline: none;
        transition: border-color 0.2s;
    }
    .search-input:focus { border-color: #d4744f; background: rgba(255,255,255,0.95); }
    .search-input::placeholder { color: rgba(44,36,32,0.35); }
    .album-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(155px, 1fr)); gap: 1.5rem; }
    .album-card { cursor: pointer; border-radius: 10px; overflow: hidden; transition: all 0.3s; border: 2px solid transparent; }
    .album-card:hover { transform: translateY(-6px); box-shadow: 0 15px 40px rgba(0,0,0,0.2); border-color: #8b7355; }
    .album-card-art { height: 155px; }
    .album-card-info { padding: 0.75rem 0; }
    .album-card-title { font-size: 0.85rem; font-weight: 600; line-height: 1.3; color: #2c2420; }
    .album-card-artist { font-size: 0.72rem; color: rgba(44,36,32,0.55); margin-top: 3px; }
    .album-card-rating { font-size: 0.7rem; color: #d4744f; margin-top: 4px; font-weight: 600; }

    /* --- Profile Page --- */
    .profile-page { padding: 78px 3rem 3rem; max-width: 860px; margin: 0 auto; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }
    .profile-top { display: flex; gap: 2rem; align-items: center; margin-bottom: 2.5rem; }
    .avatar { width: 80px; height: 80px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-family: 'Bebas Neue', sans-serif; font-size: 2rem; flex-shrink: 0; border: 3px solid #8b7355; }
    .profile-name { font-family: 'Bebas Neue', sans-serif; font-size: 2.2rem; letter-spacing: 2px; color: #2c2420; }
    .profile-rating-count { font-size: 0.8rem; color: rgba(44,36,32,0.55); letter-spacing: 1px; margin-top: 4px; }
    .profile-rating-count strong { color: #d4744f; font-size: 1.1rem; }
    .rating-row { display: flex; align-items: center; gap: 1rem; padding: 1rem 1rem; border-radius: 10px; transition: all 0.2s; border: 2px solid transparent; }
    .rating-row:hover { background: rgba(139,115,85,0.15); border-color: rgba(139,115,85,0.3); }
    .rating-art { width: 48px; height: 48px; border-radius: 6px; flex-shrink: 0; border: 2px solid #8b7355; }
    .rating-album-title { font-size: 0.9rem; font-weight: 600; color: #2c2420; }
    .rating-album-artist { font-size: 0.72rem; color: rgba(44,36,32,0.55); margin-top: 2px; }
    .rating-stars { font-size: 0.95rem; color: #d4744f; margin-left: auto; flex-shrink: 0; font-weight: 600; }

    /* --- Auth Pages --- */
    .auth-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }
    .auth-card { width: 100%; max-width: 400px; padding: 3rem 2.5rem; background: linear-gradient(180deg, rgba(255,255,255,0.9) 0%, rgba(255,255,255,0.85) 100%); border: 3px solid #8b7355; border-radius: 16px; box-shadow: 0 15px 50px rgba(0,0,0,0.15); }
    .auth-logo { font-family: 'Bebas Neue', sans-serif; font-size: 1.8rem; letter-spacing: 4px; text-align: center; margin-bottom: 2rem; color: #2c2420; }
    .auth-logo span { color: #d4744f; }
    .auth-heading { font-size: 1.35rem; font-weight: 400; margin-bottom: 1.75rem; color: #2c2420; }
    .field { margin-bottom: 1.3rem; }
    .field label { display: block; font-size: 0.72rem; letter-spacing: 1.5px; text-transform: uppercase; color: rgba(44,36,32,0.6); margin-bottom: 0.5rem; font-weight: 600; }
    .field input { width: 100%; padding: 0.9rem 1.1rem; border-radius: 10px; background: rgba(255,255,255,0.7); border: 2px solid rgba(139,115,85,0.3); color: #2c2420; font-size: 0.95rem; font-family: inherit; outline: none; transition: all 0.2s; }
    .field input:focus { border-color: #d4744f; background: rgba(255,255,255,0.95); }
    .submit-btn { width: 100%; padding: 1rem; margin-top: 0.75rem; border: none; border-radius: 10px; background: linear-gradient(135deg, #d4744f 0%, #c26241 100%); color: #f5f1ed; font-size: 0.85rem; letter-spacing: 2px; text-transform: uppercase, cursor: pointer; font-family: inherit; font-weight: 600; transition: all 0.3s; box-shadow: 0 4px 15px rgba(212,116,79,0.3); }
    .submit-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(212,116,79,0.4); }
    .submit-btn:disabled { opacity: 0.4; cursor: default; transform: none; }
    .form-error { font-size: 0.8rem; color: #d4744f; text-align: center; margin-top: 0.75rem; font-weight: 500; }
    .form-footer { text-align: center; margin-top: 1.5rem; font-size: 0.8rem; color: rgba(44,36,32,0.55); }
    .form-footer a { color: #d4744f; font-weight: 600; }

    /* --- Misc --- */
    .loading { text-align: center; padding: 4rem; color: rgba(44,36,32,0.4); letter-spacing: 2px; font-size: 0.85rem; }
    .sign-in-prompt { font-size: 0.8rem; color: rgba(44,36,32,0.55); }
    .sign-in-prompt a { color: #d4744f; font-weight: 600; }
    .empty { text-align: center; color: rgba(44,36,32,0.4); padding: 3rem; font-size: 0.9rem; }

    .add-missing-card {
        max-width: 620px;
        margin: 1.5rem auto 2rem;
        background: rgba(255,255,255,0.65);
        border: 2px solid rgba(139,115,85,0.35);
        border-radius: 12px;
        padding: 1rem;
    }
    .add-missing-card h3 { font-family: 'Bebas Neue', sans-serif; letter-spacing: 1px; margin-bottom: 0.6rem; }
    .add-missing-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0.7rem; margin-top: 0.7rem; }
    .add-missing-grid input, .add-missing-grid select {
        width: 100%;
        padding: 0.6rem 0.7rem;
        border: 1px solid rgba(139,115,85,0.5);
        border-radius: 8px;
        background: #fff;
    }
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

// --- Song Detail Modal ---
function SongDetailModal({ song, album, onClose }) {
    if (!song) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="song-modal" onClick={e => e.stopPropagation()}>
                <button className="song-modal-close" onClick={onClose}>x</button>

                <div className="song-modal-header">
                    <div className="song-modal-number">Track {song.trackNumber}</div>
                    <div className="song-modal-title">{song.title}</div>
                    <div className="song-modal-duration">Duration: {formatTime(song.durationSeconds)}</div>
                </div>

                <div className="song-modal-section">
                    <div className="song-modal-section-title">Album</div>
                    <div className="song-modal-section-content">{album.title} · {album.artist}</div>
                </div>

                {song.description && (
                    <div className="song-modal-section">
                        <div className="song-modal-section-title">Notes</div>
                        <div className="song-modal-section-content">{song.description}</div>
                    </div>
                )}

                <div className="song-modal-section">
                    <div className="song-modal-section-title">Details</div>
                    <div className="song-modal-section-content">
                        <div style={{ marginBottom: "0.75rem" }}>
                            <strong>Length:</strong> {formatTime(song.durationSeconds)}
                        </div>
                        {song.releaseYear && (
                            <div style={{ marginBottom: "0.75rem" }}>
                                <strong>Release Year:</strong> {song.releaseYear}
                            </div>
                        )}
                        {song.genre && (
                            <div>
                                <strong>Genre:</strong> {song.genre}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
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
    const [selectedSong, setSelectedSong] = useState(null);
    const [mainFilter, setMainFilter] = useState("albums");
    const [infoSong, setInfoSong] = useState(null);
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
        setSelectedSong(null);

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

    const activeAlbum = albums[activeIndex] || null;
    const allSongs = detail && detail.songs
        ? detail.songs.map(s => ({ ...s, albumTitle: detail.title, artist: detail.artist }))
        : [];

    return (
        <div className="page shelf-page" ref={pageRef}>

            {/* LEFT PANEL - stacked album cards */}
            <div className="stack-panel">
                <div style={{ marginBottom: "0.9rem" }} className="segmented">
                    <button className={mainFilter === "albums" ? "active" : ""} onClick={() => setMainFilter("albums")}>Albums</button>
                    <button className={mainFilter === "songs" ? "active" : ""} onClick={() => setMainFilter("songs")}>Songs</button>
                    <button className={mainFilter === "both" ? "active" : ""} onClick={() => setMainFilter("both")}>Both</button>
                </div>

                {(mainFilter === "albums" || mainFilter === "both") && (
                    <>
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
                    </>
                )}

                {(mainFilter === "songs" || mainFilter === "both") && (
                    <div className="subsection" style={{ width: "100%", maxHeight: 260, overflowY: "auto", paddingRight: "0.4rem" }}>
                        <div className="section-title" style={{ marginBottom: "0.4rem" }}>Songs</div>
                        {allSongs.slice(0, 10).map((song, idx) => (
                            <div key={song.id || (song.title + idx)} className="track" onClick={() => setSelectedSong(song)}>
                                <span className="track-num">{song.trackNumber || idx + 1}</span>
                                <span className="track-title">{song.title}</span>
                                <span className="track-dur">{formatTime(song.durationSeconds)}</span>
                            </div>
                        ))}
                    </div>
                )}
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
                                <AlbumArt color1={detail.color1} color2={detail.color2} size={260} style={{ borderRadius: 12 }} />
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
                                        <div style={{ fontSize: "0.7rem", letterSpacing: "1.5px", textTransform: "uppercase", color: "rgba(44,36,32,0.4)", marginBottom: "0.4rem" }}>
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
                                        <div
                                            key={song.id}
                                            className="track"
                                            onClick={() => setSelectedSong(song)}
                                        >
                                            <span className="track-num">{song.trackNumber}</span>
                                            <span className="track-title">{song.title}</span>
                                            <span className="track-dur">{formatTime(song.durationSeconds)}</span>
                                            <span className="track-actions">
                                                <button
                                                    className="ghost-btn"
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        setInfoSong(song);
                                                    }}
                                                >
                                                    More Info
                                                </button>
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </>
                        )}
                    </>
                )}
            </div>

            {/* Song Detail Modal */}
            {selectedSong && (
                <SongDetailModal
                    song={selectedSong}
                    album={detail || activeAlbum || { title: selectedSong.albumTitle || "Unknown Album", artist: selectedSong.artist || "Unknown Artist" }}
                    onClose={() => setSelectedSong(null)}
                />
            )}

            {/* More Info Placeholder Modal */}
            {infoSong && (
                <div className="modal-overlay" onClick={() => setInfoSong(null)}>
                    <div className="song-modal" onClick={(e) => e.stopPropagation()}>
                        <button className="song-modal-close" onClick={() => setInfoSong(null)}>x</button>
                        <div className="song-modal-header">
                            <div className="song-modal-number">More Info</div>
                            <div className="song-modal-title">{infoSong.title}</div>
                            <div className="song-modal-duration">UI placeholder for upcoming social/music details</div>
                        </div>
                        <div className="song-modal-section">
                            <div className="song-modal-section-title">Lyrics (Placeholder)</div>
                            <div className="song-modal-section-content">
                                We only have a preview for now. Full lyrics integration will be connected in a future release.
                            </div>
                        </div>
                        <div className="song-modal-section">
                            <div className="song-modal-section-title">Comments (Placeholder)</div>
                            <div className="song-modal-section-content">
                                - "This chorus hits every time." - dina88<br />
                                - "Best late-night track on the record." - retrohead
                            </div>
                        </div>
                        <div className="song-modal-section">
                            <div className="song-modal-section-title">Friends Who Reviewed (Placeholder)</div>
                            <div className="song-modal-section-content">Mina T., Ahmed R., Sam K.</div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

// --- Search Page ---
function SearchPage() {
    const [query,   setQuery]   = useState("");
    const [albums, setAlbums] = useState([]);
    const [songs, setSongs] = useState([]);
    const [mode, setMode] = useState("both");
    const [loading, setLoading] = useState(false);
    const [adding, setAdding] = useState(false);
    const [addForm, setAddForm] = useState({
        type: "album",
        title: "",
        artist: "",
        albumTitle: "",
        releaseYear: "2026",
        genre: "Unknown",
    });
    const navigate = useNavigate();

    useEffect(() => {
        setAddForm(prev => ({
            ...prev,
            title: query,
            albumTitle: prev.albumTitle || "Singles",
        }));
    }, [query]);

    // Search 350ms after the user stops typing
    useEffect(() => {
        if (!query.trim()) { setAlbums([]); setSongs([]); return; }
        const timer = setTimeout(() => {
            setLoading(true);
            apiGet("/search?q=" + encodeURIComponent(query))
                .then(data => {
                    setAlbums(data.albums || []);
                    setSongs(data.songs || []);
                })
                .catch(err => console.error(err))
                .finally(() => setLoading(false));
        }, 350);
        return () => clearTimeout(timer); // cancel if user keeps typing
    }, [query]);

    const showAlbums = mode === "albums" || mode === "both";
    const showSongs = mode === "songs" || mode === "both";
    const hasResults = (showAlbums && albums.length > 0) || (showSongs && songs.length > 0);

    async function addMissing() {
        if (!addForm.title.trim()) return;
        setAdding(true);
        try {
            await apiPost("/search/add", {
                ...addForm,
                releaseYear: Number(addForm.releaseYear) || 2026,
            });
            const data = await apiGet("/search?q=" + encodeURIComponent(query));
            setAlbums(data.albums || []);
            setSongs(data.songs || []);
        } catch (err) {
            console.error(err);
            alert(err.message || "Could not add item.");
        } finally {
            setAdding(false);
        }
    }

    return (
        <div className="page search-page">
            <input
                className="search-input"
                placeholder="Search albums, songs, artists, genres…"
                value={query}
                onChange={e => setQuery(e.target.value)}
                autoFocus
            />

            <div className="search-controls">
                <div className="segmented">
                    <button className={mode === "albums" ? "active" : ""} onClick={() => setMode("albums")}>Albums</button>
                    <button className={mode === "songs" ? "active" : ""} onClick={() => setMode("songs")}>Songs</button>
                    <button className={mode === "both" ? "active" : ""} onClick={() => setMode("both")}>Both</button>
                </div>
            </div>

            {loading && <div className="loading">Searching…</div>}

            {!loading && query && !hasResults && (
                <>
                    <div className="empty">No results for "{query}" in {mode}.</div>
                    <div className="add-missing-card">
                        <h3>Add this to the database</h3>
                        <div className="add-missing-grid">
                            <select value={addForm.type} onChange={(e) => setAddForm({ ...addForm, type: e.target.value })}>
                                <option value="album">Album</option>
                                <option value="song">Song</option>
                            </select>
                            <input value={addForm.title} onChange={(e) => setAddForm({ ...addForm, title: e.target.value })} placeholder="Title" />
                            <input value={addForm.artist} onChange={(e) => setAddForm({ ...addForm, artist: e.target.value })} placeholder="Artist" />
                            <input value={addForm.genre} onChange={(e) => setAddForm({ ...addForm, genre: e.target.value })} placeholder="Genre" />
                            <input value={addForm.releaseYear} onChange={(e) => setAddForm({ ...addForm, releaseYear: e.target.value })} placeholder="Release Year" />
                            {addForm.type === "song" && (
                                <input value={addForm.albumTitle} onChange={(e) => setAddForm({ ...addForm, albumTitle: e.target.value })} placeholder="Album title for song" />
                            )}
                        </div>
                        <div style={{ marginTop: "0.8rem", display: "flex", justifyContent: "flex-end" }}>
                            <button className="ghost-btn" disabled={adding} onClick={addMissing}>{adding ? "Adding..." : "Add to DB"}</button>
                        </div>
                    </div>
                </>
            )}

            {!loading && !query && (
                <div className="empty">Type something to search</div>
            )}

            {showAlbums && albums.length > 0 && (
                <>
                    <div className="section-title" style={{ marginTop: "0.6rem" }}>Albums</div>
                    <div className="album-grid">
                        {albums.map(album => (
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
                </>
            )}

            {showSongs && songs.length > 0 && (
                <>
                    <div className="section-title" style={{ marginTop: "1.6rem" }}>Songs</div>
                    <div>
                        {songs.map(song => (
                            <div key={song.id} className="track">
                                <span className="track-num">{song.trackNumber || "-"}</span>
                                <span className="track-title">{song.title} <span style={{ opacity: 0.6 }}>- {song.albumTitle} / {song.artist}</span></span>
                                <span className="track-dur">{formatTime(song.durationSeconds)}</span>
                            </div>
                        ))}
                    </div>
                </>
            )}
        </div>
    );
}

// --- Profile Page ---
function ProfilePage() {
    const { username } = useParams();
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        apiGet("/profile/" + username)
            .then(data => setProfile(data))
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    }, [username]);

    if (loading) return <div className="page loading">Loading profile...</div>;
    if (error || !profile) return <div className="page empty">Profile not found.</div>;

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
                    <div className="rating-art" style={{ background: `linear-gradient(135deg, ${r.color1}, ${r.color2})` }} />
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
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

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
                        {loading ? "Creating account..." : "Join the shelf"}
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
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

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
                        {loading ? "Signing in..." : "Sign in"}
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
