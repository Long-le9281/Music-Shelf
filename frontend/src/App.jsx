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
import { BrowserRouter, Routes, Route, Link, useNavigate, useParams, useLocation } from "react-router-dom";

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
    const raw = await response.text();
    let data = {};
    try {
        data = raw ? JSON.parse(raw) : {};
    } catch (e) {
        data = {};
    }
        if (!response.ok) {
            const message = data.error || data.message || raw || `Request failed (${response.status})`;
            if (response.status === 401) {
            localStorage.removeItem("token");
            localStorage.removeItem("user");
            if (window.location.pathname !== "/login") {
                window.location.assign("/login");
            }
        }
        throw new Error(message);
    }
    return data;
}

// Shortcuts for GET and POST requests
function apiGet(path)        { return callApi(path); }
function apiPost(path, body) { return callApi(path, { method: "POST", body: JSON.stringify(body) }); }
function apiPut(path, body)  { return callApi(path, { method: "PUT", body: JSON.stringify(body) }); }
function apiDelete(path)     { return callApi(path, { method: "DELETE" }); }

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
        const userInfo = {
            userId: data.userId,
            username: data.username,
            displayName: data.displayName || data.username,
            avatarColor: data.avatarColor,
            isAdmin: !!data.isAdmin,
            createdAt: data.createdAt || null,
            bio: data.bio || "",
        };
        localStorage.setItem("token", data.token);
        localStorage.setItem("user",  JSON.stringify(userInfo));
        setUser(userInfo);
    }

    function patchUser(fields) {
        setUser(prev => {
            if (!prev) return prev;
            const next = { ...prev, ...fields };
            localStorage.setItem("user", JSON.stringify(next));
            return next;
        });
    }

    useEffect(() => {
        const token = localStorage.getItem("token");
        if (!token) return;
        apiGet("/auth/me")
            .then((me) => patchUser({
                userId: me.userId,
                username: me.username,
                displayName: me.displayName || me.username,
                avatarColor: me.avatarColor,
                isAdmin: !!me.isAdmin,
                createdAt: me.createdAt || null,
                bio: me.bio || "",
            }))
            .catch(() => {});
    }, []);

    function logout() {
        localStorage.removeItem("token");
        localStorage.removeItem("user");
        setUser(null);
    }

    return (
        <AuthContext.Provider value={{ user, login, logout, patchUser }}>
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
        padding: 2rem 1.5rem;
        overflow: visible;
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
    .stack-card.no-art::before {
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

    .detail-panel { flex: 1; overflow: hidden; padding: 2.5rem 3rem; display: flex; flex-direction: column; align-items: center; justify-content: center; }
    .detail-shell { width: 100%; max-width: 900px; height: 100%; display: flex; flex-direction: column; min-height: 0; }

    .detail-top { display: flex; gap: 3rem; align-items: flex-start; margin-bottom: 2.5rem; max-width: 900px; width: 100%; }
    .detail-scroll-region { flex: 1; min-height: 0; overflow-y: auto; padding-right: 0.4rem; }

    .album-cover {
        width: 260px; height: 260px; border-radius: 12px; flex-shrink: 0;
        position: relative; overflow: hidden;
        box-shadow:
            0 20px 60px rgba(0,0,0,0.3),
            inset 0 1px 0 rgba(255,255,255,0.2);
        border: 3px solid #8b7355;
    }

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
    .track.active { background: rgba(139,115,85,0.18); border-color: rgba(139,115,85,0.45); }
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

    .track-lyrics-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(240px, 320px); gap: 1rem; align-items: start; }
    .lyrics-panel {
        border: 1px solid rgba(139,115,85,0.35);
        border-radius: 10px;
        background: rgba(255,255,255,0.55);
        padding: 0.9rem;
        max-height: 420px;
        overflow-y: auto;
    }
    .lyrics-panel h4 {
        font-family: 'Bebas Neue', sans-serif;
        letter-spacing: 1px;
        margin-bottom: 0.6rem;
        color: #2c2420;
        font-size: 1.05rem;
    }
    .lyrics-body {
        white-space: pre-wrap;
        font-size: 0.84rem;
        line-height: 1.65;
        color: rgba(44,36,32,0.88);
    }

    .segmented { display: inline-flex; background: rgba(245,241,237,0.92); border: 1px solid rgba(139,115,85,0.35); border-radius: 999px; padding: 0.25rem; gap: 0.2rem; backdrop-filter: blur(8px); }
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

    /* Segmented control always floats above cards */
    .stack-panel .segmented-anchor { position: relative; z-index: 30; flex-shrink: 0; }


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

    /* --- Playlists Page --- */
    .playlists-page { padding: 78px 3rem 3rem; max-width: 920px; margin: 0 auto; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }
    .playlists-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; gap: 1rem; }
    .playlists-title { font-family: 'Bebas Neue', sans-serif; font-size: 2rem; letter-spacing: 2px; color: #2c2420; }
    .playlist-card { background: rgba(255,255,255,0.72); border: 2px solid rgba(139,115,85,0.3); border-radius: 12px; padding: 1.1rem; margin-bottom: 0.9rem; transition: all 0.2s; }
    .playlist-card:hover { background: rgba(255,255,255,0.9); border-color: rgba(139,115,85,0.6); transform: translateY(-1px); }
    .playlist-card-title { font-family: 'Bebas Neue', sans-serif; font-size: 1.2rem; letter-spacing: 1px; color: #2c2420; margin-bottom: 0.3rem; }
    .playlist-card-info { font-size: 0.8rem; color: rgba(44,36,32,0.6); margin-bottom: 0.7rem; }
    .playlist-card-category { display: inline-block; font-size: 0.68rem; letter-spacing: 1px; text-transform: uppercase; padding: 0.28rem 0.65rem; border-radius: 999px; background: rgba(212,116,79,0.15); color: #d4744f; font-weight: 600; }
    .playlist-card-actions { display: flex; gap: 0.5rem; }
    .playlist-detail-page { padding: 78px 3rem 3rem; max-width: 920px; margin: 0 auto; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }
    .playlist-back { font-size: 0.78rem; letter-spacing: 1px; text-transform: uppercase; color: #d4744f; margin-bottom: 1.5rem; cursor: pointer; }
    .playlist-detail-title { font-family: 'Bebas Neue', sans-serif; font-size: 2rem; letter-spacing: 2px; color: #2c2420; }
    .playlist-detail-info { font-size: 0.84rem; color: rgba(44,36,32,0.6); }

    /* --- Generic Form Modal --- */
    .modal-content { background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); border: 3px solid #8b7355; border-radius: 16px; padding: 2rem; max-width: 520px; width: 90%; box-shadow: 0 25px 80px rgba(0,0,0,0.4); }
    .modal-header { font-family: 'Bebas Neue', sans-serif; font-size: 1.6rem; letter-spacing: 1px; color: #2c2420; margin-bottom: 1.2rem; }
    .modal-field { margin-bottom: 1rem; }
    .modal-field label { display: block; font-size: 0.72rem; letter-spacing: 1.5px; text-transform: uppercase; color: rgba(44,36,32,0.6); margin-bottom: 0.45rem; font-weight: 600; }
    .modal-field input, .modal-field select, .modal-field textarea { width: 100%; padding: 0.75rem 0.9rem; border-radius: 10px; background: rgba(255,255,255,0.7); border: 2px solid rgba(139,115,85,0.3); color: #2c2420; font-size: 0.92rem; font-family: inherit; outline: none; }
    .modal-field textarea { min-height: 80px; resize: vertical; }
    .modal-field input:focus, .modal-field select:focus, .modal-field textarea:focus { border-color: #d4744f; background: rgba(255,255,255,0.95); }
    .modal-actions { display: flex; gap: 0.6rem; margin-top: 1.2rem; }
    .modal-actions button { flex: 1; padding: 0.8rem; border: none; border-radius: 10px; font-size: 0.8rem; letter-spacing: 1px; text-transform: uppercase; font-family: inherit; font-weight: 600; cursor: pointer; }
    .modal-save { background: linear-gradient(135deg, #d4744f 0%, #c26241 100%); color: #f5f1ed; }
    .modal-cancel { background: rgba(139,115,85,0.2); color: #2c2420; }

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
    .song-card-badge {
        position: absolute; top: 8px; left: 8px;
        font-size: 0.62rem; font-weight: 700; letter-spacing: 1px;
        background: rgba(44,36,32,0.72); color: #f5f1ed;
        border-radius: 999px; padding: 0.18rem 0.55rem;
        backdrop-filter: blur(4px);
    }

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

    .account-page { padding: 78px 3rem 3rem; max-width: 980px; margin: 0 auto; background: linear-gradient(135deg, #f5f1ed 0%, #e8ddd3 100%); }
    .account-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .account-card {
        background: rgba(255,255,255,0.66);
        border: 2px solid rgba(139,115,85,0.35);
        border-radius: 12px;
        padding: 1rem;
    }
    .account-card h3 { font-family: 'Bebas Neue', sans-serif; letter-spacing: 1px; margin-bottom: 0.7rem; }
    .inline-form { display: flex; gap: 0.6rem; margin-top: 0.75rem; }
    .inline-form input {
        flex: 1;
        border: 1px solid rgba(139,115,85,0.5);
        border-radius: 8px;
        padding: 0.6rem 0.7rem;
        background: #fff;
    }
    .lookup-row { display: flex; align-items: center; gap: 0.75rem; border-top: 1px solid rgba(139,115,85,0.2); padding: 0.65rem 0; }
    .lookup-row:first-child { border-top: none; }
    .lookup-avatar { width: 34px; height: 34px; border-radius: 50%; border: 2px solid #8b7355; }
    .admin-pill {
        margin-left: auto;
        font-size: 0.62rem;
        letter-spacing: 1px;
        text-transform: uppercase;
        border: 1px solid rgba(212,116,79,0.45);
        color: #d4744f;
        border-radius: 999px;
        padding: 0.18rem 0.5rem;
    }

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
    .submit-btn { width: 100%; padding: 1rem; margin-top: 0.75rem; border: none; border-radius: 10px; background: linear-gradient(135deg, #d4744f 0%, #c26241 100%); color: #f5f1ed; font-size: 0.85rem; letter-spacing: 2px; text-transform: uppercase; cursor: pointer; font-family: inherit; font-weight: 600; transition: all 0.3s; box-shadow: 0 4px 15px rgba(212,116,79,0.3); }
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

    @media (max-width: 980px) {
        .detail-top { flex-direction: column; }
        .track-lyrics-layout { grid-template-columns: 1fr; }
        .account-grid { grid-template-columns: 1fr; }
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
                {user && <Link to="/playlists">Playlists</Link>}
                {user ? (
                    <>
                        <Link to="/account">Account</Link>
                        <Link to={"/profile/" + user.username} className="nav-user">{user.displayName || user.username}</Link>
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

// --- Album art (uses provided image when available, otherwise falls back to a gradient) ---
function AlbumArt({ color1, color2, size, artUrl, gradientAngle = 135, style = {} }) {
    const hasArt = !!(artUrl && artUrl.trim());
    return (
        <div style={{
            width: size, height: size,
            background: hasArt
                ? `center / cover no-repeat url(${artUrl})`
                : `linear-gradient(${gradientAngle}deg, ${color1 || "#333"}, ${color2 || "#555"})`,
            ...style,
        }} />
    );
}

function hasArtwork(artUrl) {
    return !!(artUrl && artUrl.trim());
}

// --- Song Detail Modal ---
function SongDetailModal({ song, album, onClose, user, onShowAddToPlaylist }) {
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

                <div className="song-modal-section">
                    <div className="song-modal-section-title">Lyrics</div>
                    <div className="song-modal-section-content" style={{ whiteSpace: "pre-wrap" }}>
                        {song.lyrics || "No lyrics stored for this track yet."}
                    </div>
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

                {user && onShowAddToPlaylist && (
                    <button
                        className="ghost-btn"
                        onClick={() => onShowAddToPlaylist(song)}
                        style={{ marginTop: "0.4rem" }}
                    >
                        + Add Song to Playlist
                    </button>
                )}
            </div>
        </div>
    );
}

function AddToPlaylistModal({ isOpen, item, onClose, onAdded }) {
    const [playlists, setPlaylists] = useState([]);
    const [selectedPlaylist, setSelectedPlaylist] = useState("");
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const { user } = useAuth();

    useEffect(() => {
        if (!isOpen || !user) return;
        setLoading(true);
        apiGet("/playlists")
            .then(data => {
                setPlaylists(data || []);
                if ((data || []).length > 0) setSelectedPlaylist(String(data[0].id));
            })
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, [isOpen, user]);

    async function handleSave() {
        if (!selectedPlaylist || !item) return;
        setSaving(true);
        try {
            if (item.type === "album") {
                await apiPost(`/playlists/${selectedPlaylist}/albums/${item.id}`, {});
            } else {
                await apiPost(`/playlists/${selectedPlaylist}/songs/${item.id}`, {});
            }
            if (onAdded) onAdded();
            onClose();
        } catch (err) {
            alert(err.message || "Could not save to playlist.");
        } finally {
            setSaving(false);
        }
    }

    if (!isOpen || !item) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={e => e.stopPropagation()}>
                <div className="modal-header">Add to Playlist</div>
                <div style={{ marginBottom: "0.8rem", color: "rgba(44,36,32,0.7)", fontSize: "0.9rem" }}>
                    <strong>{item.title}</strong>
                    {item.subtitle ? ` - ${item.subtitle}` : ""}
                </div>

                {loading && <div className="loading" style={{ padding: "1rem" }}>Loading playlists...</div>}

                {!loading && playlists.length === 0 && (
                    <div className="empty" style={{ padding: "1rem" }}>
                        No playlists yet. Create one in the Playlists page.
                    </div>
                )}

                {!loading && playlists.length > 0 && (
                    <>
                        <div className="modal-field">
                            <label>Select Playlist</label>
                            <select value={selectedPlaylist} onChange={e => setSelectedPlaylist(e.target.value)}>
                                {playlists.map(p => (
                                    <option key={p.id} value={String(p.id)}>
                                        {p.name} ({p.songCount})
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="modal-actions">
                            <button className="modal-save" disabled={saving} onClick={handleSave}>
                                {saving ? "Saving..." : "Add"}
                            </button>
                            <button className="modal-cancel" onClick={onClose}>Cancel</button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}

function CreatePlaylistModal({ isOpen, playlist = null, onClose, onSave }) {
    const [name, setName] = useState(playlist?.name || "");
    const [description, setDescription] = useState(playlist?.description || "");
    const [category, setCategory] = useState(playlist?.category || "Custom");
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        setName(playlist?.name || "");
        setDescription(playlist?.description || "");
        setCategory(playlist?.category || "Custom");
    }, [playlist, isOpen]);

    async function handleSave() {
        if (!name.trim()) return;
        setSaving(true);
        try {
            await onSave({ name: name.trim(), description: description.trim(), category: category.trim() || "Custom" });
            onClose();
        } catch (err) {
            alert(err.message || "Could not save playlist.");
        } finally {
            setSaving(false);
        }
    }

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={e => e.stopPropagation()}>
                <div className="modal-header">{playlist ? "Edit Playlist" : "Create Playlist"}</div>
                <div className="modal-field">
                    <label>Playlist Name</label>
                    <input value={name} onChange={e => setName(e.target.value)} autoFocus placeholder="e.g. 90s Mix" />
                </div>
                <div className="modal-field">
                    <label>Description (optional)</label>
                    <textarea value={description} onChange={e => setDescription(e.target.value)} placeholder="Describe this playlist..." />
                </div>
                <div className="modal-field">
                    <label>Category</label>
                    <select value={category} onChange={e => setCategory(e.target.value)}>
                        <option value="Custom">Custom</option>
                        <option value="Decade - 1970s">Decade - 1970s</option>
                        <option value="Decade - 1980s">Decade - 1980s</option>
                        <option value="Decade - 1990s">Decade - 1990s</option>
                        <option value="Decade - 2000s">Decade - 2000s</option>
                        <option value="Genre - Rock">Genre - Rock</option>
                        <option value="Genre - Pop">Genre - Pop</option>
                        <option value="Genre - Jazz">Genre - Jazz</option>
                        <option value="Mood - Relaxing">Mood - Relaxing</option>
                        <option value="Mood - Energetic">Mood - Energetic</option>
                        <option value="Activity - Workout">Activity - Workout</option>
                        <option value="Activity - Study">Activity - Study</option>
                    </select>
                </div>
                <div className="modal-actions">
                    <button className="modal-save" disabled={saving} onClick={handleSave}>{saving ? "Saving..." : "Save"}</button>
                    <button className="modal-cancel" onClick={onClose}>Cancel</button>
                </div>
            </div>
        </div>
    );
}

// --- 5-star rating widget ---
function StarRating({ value, onChange, label = "Rate this album" }) {
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
            <span className="rate-label">{labels[hovered || value] || label}</span>
        </div>
    );
}

// Converts seconds to "3:45" format
function formatTime(seconds) {
    if (!seconds) return "";
    return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
}

function formatReleaseYear(year) {
    return year && Number(year) > 0 ? year : "Unknown";
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
    const [lyricsSong, setLyricsSong] = useState(null);
    const [mainFilter, setMainFilter] = useState("albums");
    const [songActiveIndex, setSongActiveIndex] = useState(0);
    const [allSongsGlobal, setAllSongsGlobal] = useState([]);
    const [addToPlaylistOpen, setAddToPlaylistOpen] = useState(false);
    const [playlistItem, setPlaylistItem] = useState(null);
    const [loading, setLoading]     = useState(true);
    const { user } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const pageRef  = useRef(null);
    const scrollBuf = useRef(0); // accumulates scroll distance
    const focusAppliedRef = useRef(false);

    const routeFocus = location.state && location.state.focus ? location.state.focus : null;

    // Load all albums and all songs when the page opens
    useEffect(() => {
        Promise.all([
            apiGet("/albums"),
            apiGet("/songs"),
        ])
            .then(([albumData, songData]) => {
                setAlbums(albumData);
                setAllSongsGlobal(songData);
            })
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!routeFocus || focusAppliedRef.current) return;

        if (routeFocus.type === "song") {
            const songIndex = allSongsGlobal.findIndex(song => song.id === routeFocus.songId);
            if (songIndex >= 0) {
                setMainFilter("songs");
                setSongActiveIndex(songIndex);
                focusAppliedRef.current = true;
                navigate("/", { replace: true, state: null });
                return;
            }

            if (!routeFocus.albumId) return;
        }

        const albumIndex = albums.findIndex(album => album.id === routeFocus.albumId);
        if (albumIndex >= 0) {
            setMainFilter("albums");
            setActiveIndex(albumIndex);
            focusAppliedRef.current = true;
            navigate("/", { replace: true, state: null });
            return;
        }

        if (loading) return;
        focusAppliedRef.current = true;
        navigate("/", { replace: true, state: null });
    }, [routeFocus, albums, allSongsGlobal, loading, navigate]);

    const activeAlbum = albums[activeIndex] || null;
    const activeSong = allSongsGlobal[songActiveIndex] || null;
    const songsMode = mainFilter === "songs";
    const focusedAlbumId = songsMode ? activeSong?.albumId : activeAlbum?.id;
    const detailMatchesFocus = detail && detail.id === focusedAlbumId;
    const focusedAlbumDetail = detailMatchesFocus ? detail : null;
    const focusedSong = songsMode
        ? (focusedAlbumDetail?.songs?.find(song => song.id === activeSong?.id) || activeSong || null)
        : null;

    // Load detail + my rating whenever the currently focused album changes
    useEffect(() => {
        if (!focusedAlbumId) return;
        let ignore = false;

        setMyRating(0);
        setSavedMsg(false);

        apiGet("/albums/" + focusedAlbumId)
            .then(data => {
                if (!ignore) setDetail(data);
            })
            .catch(err => console.error(err));

        if (user) {
            const ratingPath = songsMode && focusedSong?.id
                ? "/ratings/songs/" + focusedSong.id
                : "/ratings/" + focusedAlbumId;

            apiGet(ratingPath)
                .then(data => { if (!ignore) setMyRating(data.stars || 0); })
                .catch(() => {});
        }

        return () => { ignore = true; };
    }, [focusedAlbumId, focusedSong?.id, songsMode, user]);

    useEffect(() => {
        if (songsMode) {
            setLyricsSong(focusedSong || null);
            return;
        }

        if (focusedAlbumDetail?.songs?.length) {
            setLyricsSong(prev => {
                if (prev && focusedAlbumDetail.songs.some(song => song.id === prev.id)) return prev;
                return focusedAlbumDetail.songs[0];
            });
            return;
        }

        setLyricsSong(null);
    }, [songsMode, focusedSong, focusedAlbumDetail]);

    // Scroll wheel: accumulate scroll and flip album/song every ~80px
    useEffect(() => {
        const el = pageRef.current;
        if (!el) return;

        const songsLen = allSongsGlobal.length;

        function onWheel(e) {
            const wheelTarget = e.target;
            if (wheelTarget instanceof Element && wheelTarget.closest(".track-scroll-region")) {
                return;
            }
            e.preventDefault();
            scrollBuf.current += e.deltaY;
            if (scrollBuf.current > 80) {
                scrollBuf.current = 0;
                if (mainFilter === "songs") {
                    setSongActiveIndex(i => Math.min(i + 1, songsLen - 1));
                } else {
                    setActiveIndex(i => Math.min(i + 1, albums.length - 1));
                }
            } else if (scrollBuf.current < -80) {
                scrollBuf.current = 0;
                if (mainFilter === "songs") {
                    setSongActiveIndex(i => Math.max(i - 1, 0));
                } else {
                    setActiveIndex(i => Math.max(i - 1, 0));
                }
            }
        }

        el.addEventListener("wheel", onWheel, { passive: false });
        return () => el.removeEventListener("wheel", onWheel);
    }, [albums.length, allSongsGlobal.length, mainFilter]);

    async function handleRate(stars) {
        if (!user) { window.location.href = "/login"; return; }
        if (!focusedAlbumId) return;

        setMyRating(stars);
        try {
            if (songsMode && focusedSong?.id) {
                await apiPost("/ratings/songs/" + focusedSong.id, { stars });
            } else {
                await apiPost("/ratings/" + focusedAlbumId, { stars });
            }
            setSavedMsg(true);
            // Refresh to show updated community average
            if (!songsMode) {
                apiGet("/albums/" + focusedAlbumId).then(setDetail);
            }
            setTimeout(() => setSavedMsg(false), 2500);
        } catch (err) {
            console.error(err);
        }
    }

    function syncSongSelection(song, { openModal = false } = {}) {
        if (!song) return;

        setLyricsSong(song);

        if (songsMode) {
            const nextIndex = allSongsGlobal.findIndex(item => item.id === song.id);
            if (nextIndex >= 0) setSongActiveIndex(nextIndex);
        }

        if (openModal) setSelectedSong(song);
    }

    function openSongPlaylistModal(song) {
        if (!song) return;
        setPlaylistItem({
            type: "song",
            id: song.id,
            title: song.title,
            subtitle: song.albumTitle || focusedAlbumDetail?.title || activeAlbum?.title || "",
        });
        setAddToPlaylistOpen(true);
    }

    function openAlbumPlaylistModal() {
        const albumForPlaylist = focusedAlbumDetail || activeAlbum;
        if (!albumForPlaylist) return;
        setPlaylistItem({
            type: "album",
            id: albumForPlaylist.id,
            title: albumForPlaylist.title,
            subtitle: albumForPlaylist.artist,
        });
        setAddToPlaylistOpen(true);
    }

    if (loading) return <div className="page loading">LOADING RECORDS…</div>;
    if (albums.length === 0) return <div className="page loading">No albums found. Run database/setup.py first.</div>;

    const detailTitle = songsMode
        ? (focusedSong?.title || "Select a song")
        : (focusedAlbumDetail?.title || activeAlbum?.title || "");
    const detailArtistLine = songsMode
        ? [focusedAlbumDetail?.artist || activeSong?.artist, focusedAlbumDetail?.title || activeSong?.albumTitle].filter(Boolean).join(" · ")
        : (focusedAlbumDetail?.artist || activeAlbum?.artist || "");
    const detailDescription = songsMode
        ? (focusedSong
            ? `Track ${focusedSong.trackNumber || "—"} · ${formatTime(focusedSong.durationSeconds) || "Unknown length"} · from ${focusedAlbumDetail?.title || activeSong?.albumTitle || "Unknown Album"}.`
            : "")
        : (focusedAlbumDetail?.description || "");
    const modalAlbum = selectedSong
        ? {
            title: selectedSong.albumTitle || focusedAlbumDetail?.title || activeAlbum?.title || "Unknown Album",
            artist: selectedSong.artist || focusedAlbumDetail?.artist || activeAlbum?.artist || "Unknown Artist",
        }
        : null;
    const detailArtUrl = focusedAlbumDetail?.albumArtUrl || activeSong?.albumArtUrl || "";

    return (
        <div className="page shelf-page" ref={pageRef}>

            {/* LEFT PANEL - stacked album/song cards */}
            <div className="stack-panel">
                {/* Segmented control — always above cards via z-index */}
                <div className="segmented-anchor" style={{ marginBottom: "1.1rem" }}>
                    <div className="segmented">
                        <button className={mainFilter === "albums" ? "active" : ""} onClick={() => setMainFilter("albums")}>Albums</button>
                        <button className={mainFilter === "songs" ? "active" : ""} onClick={() => setMainFilter("songs")}>Songs</button>
                    </div>
                </div>

                {/* ── ALBUMS ONLY ── */}
                {mainFilter === "albums" && (
                    <>
                        <div className="stack-wrapper">
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
                                const cardHasArt = hasArtwork(album.albumArtUrl);
                                return (
                                    <div key={album.id} className={"stack-card " + (cardHasArt ? "has-art" : "no-art")}
                                        onClick={() => setActiveIndex(index)}
                                        style={{
                                            transform: `translateY(${yMove}%) scale(${scale}) rotate(${rotate}deg)`,
                                            zIndex, opacity, top: "25px",
                                        }}>
                                        <AlbumArt
                                            color1={album.color1}
                                            color2={album.color2}
                                            artUrl={album.albumArtUrl}
                                            size="100%"
                                            style={{ height: "100%", display: "block" }}
                                        />
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

                {/* ── SONGS ONLY ── */}
                {mainFilter === "songs" && (
                    <>
                        <div className="stack-wrapper">
                            {[-2, -1, 0, 1, 2].map(offset => {
                                const index = songActiveIndex + offset;
                                if (index < 0 || index >= allSongsGlobal.length) return null;
                                const song     = allSongsGlobal[index];
                                const isActive = offset === 0;
                                const yMove    = offset * 40;
                                const scale    = isActive ? 1 : 1 - Math.abs(offset) * 0.07;
                                const opacity  = isActive ? 1 : 1 - Math.abs(offset) * 0.22;
                                const zIndex   = 10 - Math.abs(offset);
                                const rotate   = offset * -1.5;
                                // Vary gradient angle per song so peeking cards look distinct
                                const angle    = 115 + (index % 8) * 25;
                                const cardHasArt = hasArtwork(song.albumArtUrl);
                                return (
                                    <div key={song.id || (song.title + index)} className={"stack-card " + (cardHasArt ? "has-art" : "no-art")}
                                            onClick={() => {
                                                setSongActiveIndex(index);
                                                syncSongSelection(song, { openModal: true });
                                            }}
                                        style={{
                                            transform: `translateY(${yMove}%) scale(${scale}) rotate(${rotate}deg)`,
                                            zIndex, opacity, top: "25px",
                                        }}>
                                        <AlbumArt
                                            color1={song.color1}
                                            color2={song.color2}
                                            artUrl={song.albumArtUrl}
                                            gradientAngle={angle}
                                            size="100%"
                                            style={{ height: "100%", display: "block" }}
                                        />
                                        {isActive && (
                                            <div className="stack-card-label">
                                                <div className="stack-card-title">{song.title}</div>
                                                <div className="stack-card-artist">{song.artist} · {song.albumTitle}</div>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                        <div className="scroll-hint">↑ ↓ scroll to browse</div>
                    </>
                )}

            </div>

            {/* RIGHT PANEL - album detail */}
            <div className="detail-panel">
                {(!focusedAlbumId && !songsMode) || (!focusedAlbumDetail && !songsMode) ? (
                    <div className="loading">LOADING…</div>
                ) : (
                    <div className="detail-shell">
                        <div className="detail-top">
                            {/* Album cover */}
                            <div className="album-cover">
                                <AlbumArt
                                    color1={focusedAlbumDetail?.color1 || activeSong?.color1 || activeAlbum?.color1}
                                    color2={focusedAlbumDetail?.color2 || activeSong?.color2 || activeAlbum?.color2}
                                    artUrl={detailArtUrl}
                                    size={260}
                                    style={{ borderRadius: 12 }}
                                />
                            </div>

                            <div className="detail-info">
                                {songsMode && focusedSong && (
                                    <div style={{ fontSize: "0.72rem", letterSpacing: "2px", textTransform: "uppercase", color: "rgba(44,36,32,0.45)", marginBottom: "0.5rem" }}>
                                        Song Focus
                                    </div>
                                )}
                                <div className="detail-title">{detailTitle}</div>
                                <div className="detail-artist">{detailArtistLine}</div>

                                <div className="tags">
                                    {songsMode && focusedSong?.trackNumber && <span className="tag">Track {focusedSong.trackNumber}</span>}
                                    {songsMode && focusedSong?.durationSeconds && <span className="tag">{formatTime(focusedSong.durationSeconds)}</span>}
                                    {(focusedAlbumDetail?.releaseYear || activeSong?.releaseYear) && <span className="tag">{focusedAlbumDetail?.releaseYear || activeSong?.releaseYear}</span>}
                                    {(focusedAlbumDetail?.genre || activeSong?.genre) && <span className="tag">{focusedAlbumDetail?.genre || activeSong?.genre}</span>}
                                    {!songsMode && focusedAlbumDetail?.runtimeMinutes && <span className="tag">{focusedAlbumDetail.runtimeMinutes} min</span>}
                                </div>

                                {detailDescription && (
                                    <p className="detail-description">{detailDescription}</p>
                                )}

                                {/* Community average */}
                                <div className="community-rating">
                                    {!focusedAlbumDetail
                                        ? "Loading album details…"
                                        : focusedAlbumDetail.avgRating
                                            ? <><strong>★ {Number(focusedAlbumDetail.avgRating).toFixed(1)}</strong> avg · {focusedAlbumDetail.ratingCount} ratings</>
                                            : "No ratings yet — be the first!"}
                                </div>

                                {/* Your rating */}
                                {user ? (
                                    <>
                                        <div style={{ fontSize: "0.7rem", letterSpacing: "1.5px", textTransform: "uppercase", color: "rgba(44,36,32,0.4)", marginBottom: "0.4rem" }}>
                                            Your Rating
                                        </div>
                                        <StarRating
                                            value={myRating}
                                            onChange={handleRate}
                                            label={songsMode ? "Rate this song" : "Rate this album"}
                                        />
                                        {savedMsg && <div className="saved-msg">✓ Saved!</div>}
                                        <div style={{ marginTop: "0.7rem" }}>
                                            <button className="ghost-btn" onClick={openAlbumPlaylistModal}>
                                                + Add Album Songs to Playlist
                                            </button>
                                        </div>
                                    </>
                                ) : (
                                    <div className="sign-in-prompt">
                                        <Link to="/login">Sign in</Link> to rate this {songsMode ? "song" : "album"}
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Track list */}
                        {focusedAlbumDetail?.songs && focusedAlbumDetail.songs.length > 0 && (
                            <div className="detail-scroll-region track-scroll-region">
                                <div className="section-title">{songsMode ? "Album Track List" : "Track List"}</div>
                                <div className="track-lyrics-layout">
                                    <div>
                                        {focusedAlbumDetail.songs.map(song => (
                                            <div
                                                key={song.id}
                                                className={"track" + (focusedSong && song.id === focusedSong.id ? " active" : "")}
                                                onClick={() => {
                                                    syncSongSelection(song, { openModal: true });
                                                }}
                                            >
                                                <span className="track-num">{song.trackNumber}</span>
                                                <span className="track-title">{song.title}</span>
                                                <span className="track-dur">{formatTime(song.durationSeconds)}</span>
                                                <span className="track-actions">
                                                    <button
                                                        className="ghost-btn"
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            syncSongSelection(song);
                                                        }}
                                                    >
                                                        Lyrics
                                                    </button>
                                                    {user && (
                                                        <button
                                                            className="ghost-btn"
                                                            onClick={(e) => {
                                                                e.stopPropagation();
                                                                openSongPlaylistModal(song);
                                                            }}
                                                        >
                                                            Add
                                                        </button>
                                                    )}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                    <aside className="lyrics-panel">
                                        <h4>{lyricsSong ? lyricsSong.title : "Select a track"}</h4>
                                        <div className="lyrics-body">
                                            {lyricsSong?.lyrics || "No lyrics available for this track yet."}
                                        </div>
                                    </aside>
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Song Detail Modal */}
            {selectedSong && (
                <SongDetailModal
                    song={selectedSong}
                    album={modalAlbum || { title: selectedSong.albumTitle || "Unknown Album", artist: selectedSong.artist || "Unknown Artist" }}
                    user={user}
                    onShowAddToPlaylist={openSongPlaylistModal}
                    onClose={() => setSelectedSong(null)}
                />
            )}

            <AddToPlaylistModal
                isOpen={addToPlaylistOpen}
                item={playlistItem}
                onClose={() => {
                    setAddToPlaylistOpen(false);
                    setPlaylistItem(null);
                }}
            />

        </div>
    );
}

// --- Search Page ---
function SearchPage() {
    const [query,   setQuery]   = useState("");
    const [albums, setAlbums] = useState([]);
    const [songs, setSongs] = useState([]);
    const [mode, setMode] = useState("albums");
    const [loading, setLoading] = useState(false);
    const [adding, setAdding] = useState(false);
    const [addToPlaylistOpen, setAddToPlaylistOpen] = useState(false);
    const [playlistItem, setPlaylistItem] = useState(null);
    const [addForm, setAddForm] = useState({
        type: "album",
        title: "",
        artist: "",
        albumTitle: "",
        releaseYear: "2026",
        genre: "Unknown",
    });
    const navigate = useNavigate();
    const { user } = useAuth();

    function openSongPlaylistModal(song) {
        setPlaylistItem({
            type: "song",
            id: song.id,
            title: song.title,
            subtitle: song.albumTitle || song.artist || "",
        });
        setAddToPlaylistOpen(true);
    }

    function openAlbumPlaylistModal(album) {
        setPlaylistItem({
            type: "album",
            id: album.id,
            title: album.title,
            subtitle: album.artist || "",
        });
        setAddToPlaylistOpen(true);
    }

    function openAlbumInShelf(album) {
        navigate("/", {
            state: {
                focus: {
                    type: "album",
                    albumId: album.id,
                },
            },
        });
    }

    function openSongInShelf(song) {
        navigate("/", {
            state: {
                focus: {
                    type: "song",
                    songId: song.id,
                    albumId: song.albumId || null,
                },
            },
        });
    }

    useEffect(() => {
        setAddForm(prev => ({
            ...prev,
            title: query,
            albumTitle: prev.albumTitle,
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

    const showAlbums = mode === "albums";
    const showSongs = mode === "songs";
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
                </div>
            </div>

            {loading && <div className="loading">Searching…</div>}

            {!loading && query && !hasResults && <div className="empty">No results for "{query}" in {mode}.</div>}

            {!loading && query && !hasResults && user?.isAdmin && (
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
                            <input value={addForm.albumTitle} onChange={(e) => setAddForm({ ...addForm, albumTitle: e.target.value })} placeholder="Album title (optional)" />
                        )}
                    </div>
                    <div style={{ marginTop: "0.8rem", display: "flex", justifyContent: "flex-end" }}>
                        <button className="ghost-btn" disabled={adding} onClick={addMissing}>{adding ? "Adding..." : "Add to DB"}</button>
                    </div>
                </div>
            )}

            {!loading && !query && (
                <div className="empty">Type something to search</div>
            )}

            {showAlbums && albums.length > 0 && (
                <>
                    <div className="section-title" style={{ marginTop: "0.6rem" }}>Albums</div>
                    <div className="album-grid">
                        {albums.map(album => (
                            <div key={album.id} className="album-card" onClick={() => openAlbumInShelf(album)}>
                                <AlbumArt color1={album.color1} color2={album.color2} artUrl={album.albumArtUrl} size="100%" style={{ height: 155 }} />
                                <div className="album-card-info">
                                    <div className="album-card-title">{album.title}</div>
                                    <div className="album-card-artist">{album.artist} · {formatReleaseYear(album.releaseYear)}</div>
                                    {album.avgRating && (
                                        <div className="album-card-rating">★ {Number(album.avgRating).toFixed(1)}</div>
                                    )}
                                    {user && (
                                        <div style={{ marginTop: "0.45rem" }}>
                                            <button
                                                className="ghost-btn"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    openAlbumPlaylistModal(album);
                                                }}
                                            >
                                                Add Album to Playlist
                                            </button>
                                        </div>
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
                    <div className="album-grid">
                        {songs.map(song => (
                            <div key={song.id} className="album-card" onClick={() => openSongInShelf(song)}>
                                <div style={{ position: "relative" }}>
                                    <AlbumArt
                                        color1={song.color1}
                                        color2={song.color2}
                                        artUrl={song.albumArtUrl}
                                        size="100%"
                                        style={{ height: 155 }}
                                    />
                                    {song.trackNumber && <span className="song-card-badge">#{song.trackNumber}</span>}
                                </div>
                                <div className="album-card-info">
                                    <div className="album-card-title">{song.title}</div>
                                    <div className="album-card-artist">{song.artist} · {song.albumTitle}</div>
                                    <div className="album-card-rating">{formatTime(song.durationSeconds)}</div>
                                    {user && (
                                        <div style={{ marginTop: "0.45rem" }}>
                                            <button
                                                className="ghost-btn"
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    openSongPlaylistModal(song);
                                                }}
                                            >
                                                Add Song to Playlist
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            )}
            <AddToPlaylistModal
                isOpen={addToPlaylistOpen}
                item={playlistItem}
                onClose={() => {
                    setAddToPlaylistOpen(false);
                    setPlaylistItem(null);
                }}
            />
        </div>
    );
}

function PlaylistsPage() {
    const [playlists, setPlaylists] = useState([]);
    const [loading, setLoading] = useState(true);
    const [createOpen, setCreateOpen] = useState(false);
    const [editingPlaylist, setEditingPlaylist] = useState(null);
    const { user } = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        if (!user) {
            navigate("/login");
            return;
        }
        apiGet("/playlists")
            .then(data => setPlaylists(data || []))
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, [user, navigate]);

    async function refreshPlaylists() {
        const data = await apiGet("/playlists");
        setPlaylists(data || []);
    }

    async function createPlaylist(data) {
        await apiPost("/playlists", data);
        await refreshPlaylists();
    }

    async function updatePlaylist(data) {
        if (!editingPlaylist) return;
        await apiPut(`/playlists/${editingPlaylist.id}`, data);
        setEditingPlaylist(null);
        await refreshPlaylists();
    }

    async function removePlaylist(id) {
        if (!window.confirm("Delete this playlist?")) return;
        await apiDelete(`/playlists/${id}`);
        setPlaylists(prev => prev.filter(p => p.id !== id));
    }

    if (loading) return <div className="page loading">Loading playlists...</div>;

    return (
        <div className="page playlists-page">
            <div className="playlists-header">
                <div className="playlists-title">Your Playlists</div>
                <button className="submit-btn" style={{ margin: 0, width: "auto" }} onClick={() => setCreateOpen(true)}>
                    + Create Playlist
                </button>
            </div>

            {playlists.length === 0 ? (
                <div className="empty">No playlists yet. Create one to get started.</div>
            ) : (
                playlists.map(playlist => (
                    <div key={playlist.id} className="playlist-card">
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "1rem" }}>
                            <div style={{ flex: 1, cursor: "pointer" }} onClick={() => navigate(`/playlists/${playlist.id}`)}>
                                <div className="playlist-card-title">{playlist.name}</div>
                                {playlist.description && <div style={{ fontSize: "0.84rem", color: "rgba(44,36,32,0.62)", marginBottom: "0.4rem" }}>{playlist.description}</div>}
                                <div className="playlist-card-info">
                                    <span className="playlist-card-category" style={{ marginRight: "0.6rem" }}>{playlist.category}</span>
                                    <span>{playlist.songCount} {playlist.songCount === 1 ? "song" : "songs"}</span>
                                </div>
                            </div>
                            <div className="playlist-card-actions">
                                <button className="ghost-btn" onClick={() => setEditingPlaylist(playlist)}>Edit</button>
                                <button className="ghost-btn" onClick={() => removePlaylist(playlist.id)}>Delete</button>
                            </div>
                        </div>
                    </div>
                ))
            )}

            <CreatePlaylistModal
                isOpen={createOpen}
                onClose={() => setCreateOpen(false)}
                onSave={createPlaylist}
            />
            <CreatePlaylistModal
                isOpen={!!editingPlaylist}
                playlist={editingPlaylist}
                onClose={() => setEditingPlaylist(null)}
                onSave={updatePlaylist}
            />
        </div>
    );
}

function PlaylistDetailPage() {
    const { id } = useParams();
    const { user } = useAuth();
    const navigate = useNavigate();
    const [playlist, setPlaylist] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!user) {
            navigate("/login");
            return;
        }
        apiGet(`/playlists/${id}`)
            .then(data => setPlaylist(data))
            .catch(err => console.error(err))
            .finally(() => setLoading(false));
    }, [id, user, navigate]);

    async function removeSong(songId) {
        if (!window.confirm("Remove this song from playlist?")) return;
        await apiDelete(`/playlists/${id}/songs/${songId}`);
        setPlaylist(prev => prev ? { ...prev, songs: prev.songs.filter(s => s.id !== songId) } : prev);
    }

    if (loading) return <div className="page loading">Loading playlist...</div>;
    if (!playlist) return <div className="page empty">Playlist not found.</div>;

    return (
        <div className="page playlist-detail-page">
            <div className="playlist-back" onClick={() => navigate("/playlists")}>Back to Playlists</div>
            <div className="playlist-detail-title">{playlist.name}</div>
            {playlist.description && <p style={{ marginTop: "0.6rem", color: "rgba(44,36,32,0.65)" }}>{playlist.description}</p>}
            <div className="playlist-detail-info" style={{ marginTop: "0.7rem" }}>
                <span className="playlist-card-category" style={{ marginRight: "0.6rem" }}>{playlist.category}</span>
                <span>{playlist.songs.length} {playlist.songs.length === 1 ? "song" : "songs"}</span>
            </div>

            <div style={{ marginTop: "1.5rem" }}>
                {playlist.songs.length === 0 ? (
                    <div className="empty">No songs in this playlist yet.</div>
                ) : (
                    <>
                        <div className="section-title">Songs</div>
                        {playlist.songs.map((song, idx) => (
                            <div key={`${song.id}-${idx}`} className="track">
                                <span className="track-num">{idx + 1}</span>
                                <div style={{ flex: 1 }}>
                                    <div className="track-title">{song.title}</div>
                                    <div style={{ fontSize: "0.74rem", color: "rgba(44,36,32,0.55)", marginTop: "2px" }}>
                                        {song.albumTitle} - {song.artist}
                                    </div>
                                </div>
                                <span className="track-dur">{formatTime(song.durationSeconds)}</span>
                                <span className="track-actions">
                                    <button className="ghost-btn" onClick={() => removeSong(song.id)}>Remove</button>
                                </span>
                            </div>
                        ))}
                    </>
                )}
            </div>
        </div>
    );
}

// --- Profile Page ---
function ProfilePage() {
    const { username } = useParams();
    const [profile, setProfile] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const [selectedGenre, setSelectedGenre] = useState("All");

    useEffect(() => {
        apiGet("/profile/" + username)
            .then(data => setProfile(data))
            .catch(err => setError(err.message))
            .finally(() => setLoading(false));
    }, [username]);

    if (loading) return <div className="page loading">Loading profile...</div>;
    if (error || !profile) return <div className="page empty">Profile not found.</div>;

    const genres = ["All", ...new Set(profile.ratings.map(r => r.genre).filter(Boolean))];

    function cycleGenre() {
        const currentIndex = genres.indexOf(selectedGenre);
        const nextIndex = (currentIndex + 1) % genres.length;
        setSelectedGenre(genres[nextIndex]);
    }

    const filteredRatings =
        selectedGenre === "All"
            ? profile.ratings
            : profile.ratings.filter(r => r.genre === selectedGenre);

    return (
        <div className="page profile-page">
            <div className="profile-top">
                <div className="avatar" style={{ background: profile.avatarColor }}>
                    {profile.username[0].toUpperCase()}
                </div>
                <div>
                    <div className="profile-name">{profile.displayName || profile.username}</div>
                    <div className="profile-rating-count">
                        @{profile.username} · <strong>{profile.ratingCount}</strong> album ratings {profile.isAdmin ? "· Admin" : ""}
                    </div>
                    {profile.bio && <div style={{ marginTop: "0.5rem", color: "rgba(44,36,32,0.7)", maxWidth: 520 }}>{profile.bio}</div>}
                </div>
            </div>

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
                <div className="section-title">
                    Rated Albums {selectedGenre !== "All" ? `: ${selectedGenre}` : ""}
                </div>
                <button onClick={cycleGenre} style={{ cursor: "pointer" }}>
                    Filter Genre
                </button>
            </div>

            {filteredRatings.length === 0 && (
                <div className="empty">No ratings yet.</div>
            )}

            {filteredRatings.map((r, i) => (
                <div key={i} className="rating-row">
                    <div className="rating-art" style={{ background: `linear-gradient(135deg, ${r.color1}, ${r.color2})` }} />
                    <div>
                        <div className="rating-album-title">{r.title}</div>
                        <div className="rating-album-artist">
                            {r.artist}
                            {r.updatedAt ? ` · rated ${new Date(r.updatedAt).toLocaleDateString()}` : ""}
                        </div>
                    </div>
                    <div className="rating-stars">{"★".repeat(r.stars)}</div>
                </div>
            ))}
        </div>
    );
}

function AccountPage() {
    const { user, patchUser } = useAuth();
    const navigate = useNavigate();
    const [account, setAccount] = useState(null);
    const [lookupQuery, setLookupQuery] = useState("");
    const [lookupUsers, setLookupUsers] = useState([]);
        const [lookupAttempted, setLookupAttempted] = useState(false);
    const [adminUsers, setAdminUsers] = useState([]);
    const [promotionCode, setPromotionCode] = useState("");
    const [message, setMessage] = useState("");
    const [createForm, setCreateForm] = useState({
        username: "",
        password: "",
        displayName: "",
        bio: "",
        avatarColor: "#45B7D1",
        isAdmin: false,
    });

    useEffect(() => {
        if (!user) {
            navigate("/login");
            return;
        }
        apiGet("/profile/me?historyLimit=12")
            .then((data) => {
                setAccount(data);
                patchUser({
                    displayName: data.displayName || data.username,
                    avatarColor: data.avatarColor,
                    isAdmin: !!data.isAdmin,
                    bio: data.bio || "",
                    createdAt: data.createdAt || null,
                });
            })
            .catch((err) => setMessage(err.message));
    }, [user]);

    async function refreshAdminUsers() {
        if (!account?.isAdmin) return;
        try {
            const data = await apiGet("/admin/users");
            setAdminUsers(data.users || []);
        } catch {
            // keep last known state
        }
    }

    useEffect(() => {
        refreshAdminUsers();
    }, [account?.isAdmin]);

    async function runLookup() {
        const q = lookupQuery.trim();
            setLookupAttempted(true);
        if (!q) {
            setLookupUsers([]);
            return;
        }
        try {
            const data = await apiGet("/users/lookup?q=" + encodeURIComponent(q));
            setLookupUsers(data.users || []);
        } catch (err) {
            setMessage(err.message);
        }
    }

    async function promoteSelf() {
        if (!promotionCode.trim()) return;
        try {
            const data = await apiPost("/auth/promote", { code: promotionCode.trim() });
            patchUser({ isAdmin: !!data.isAdmin });
            setAccount((prev) => prev ? { ...prev, isAdmin: !!data.isAdmin } : prev);
            setMessage(data.message || "Admin updated");
            setPromotionCode("");
            await refreshAdminUsers();
        } catch (err) {
            setMessage(err.message);
        }
    }

    async function toggleRole(target) {
        try {
            const nextRole = !target.isAdmin;
            await apiPut("/admin/users/" + encodeURIComponent(target.username) + "/role", { isAdmin: nextRole });
            await refreshAdminUsers();
            setMessage(`Role updated for @${target.username}`);
        } catch (err) {
            setMessage(err.message);
        }
    }

    async function toggleStatus(target) {
        try {
            const nextActive = !target.isActive;
            await apiPut("/admin/users/" + encodeURIComponent(target.username) + "/status", { isActive: nextActive });
            await refreshAdminUsers();
            setMessage(nextActive ? `Enabled @${target.username}` : `Disabled @${target.username}`);
        } catch (err) {
            setMessage(err.message);
        }
    }

    async function resetPassword(target) {
        try {
            const data = await apiPost("/admin/users/" + encodeURIComponent(target.username) + "/reset-password", {});
            setMessage(`Password reset for @${target.username}: ${data.temporaryPassword}`);
            await refreshAdminUsers();
        } catch (err) {
            setMessage(err.message);
        }
    }

    async function softDeleteUser(target) {
        if (!window.confirm(`Soft delete @${target.username}?`)) return;
        try {
            await apiDelete("/admin/users/" + encodeURIComponent(target.username));
            await refreshAdminUsers();
            setMessage(`Soft deleted @${target.username}`);
        } catch (err) {
            setMessage(err.message);
        }
    }

    async function createUserAsAdmin() {
        if (!createForm.username.trim() || !createForm.password.trim()) {
            setMessage("Username and password are required.");
            return;
        }
        try {
            await apiPost("/admin/users", {
                ...createForm,
                username: createForm.username.trim(),
                password: createForm.password,
                displayName: createForm.displayName.trim() || createForm.username.trim(),
                bio: createForm.bio.trim(),
            });
            setCreateForm({ username: "", password: "", displayName: "", bio: "", avatarColor: "#45B7D1", isAdmin: false });
            await refreshAdminUsers();
            setMessage("User created.");
        } catch (err) {
            setMessage(err.message);
        }
    }

    if (!user) return <div className="page loading">Redirecting…</div>;

    return (
        <div className="page account-page">
            <div className="section-title">My Account</div>

            {account && (
                <div className="account-card" style={{ marginBottom: "1rem" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
                        <div className="avatar" style={{ width: 56, height: 56, fontSize: "1.5rem", background: account.avatarColor }}>
                            {(account.displayName || account.username || "u")[0].toUpperCase()}
                        </div>
                        <div>
                            <div style={{ fontSize: "1.05rem", fontWeight: 700 }}>{account.displayName || account.username}</div>
                            <div style={{ fontSize: "0.8rem", color: "rgba(44,36,32,0.6)" }}>@{account.username} {account.isAdmin ? "· admin" : ""}</div>
                        </div>
                    </div>
                    {account.bio && <div style={{ marginTop: "0.75rem", color: "rgba(44,36,32,0.75)" }}>{account.bio}</div>}
                </div>
            )}

            <div className="account-grid">
                <div className="account-card">
                    <h3>Lookup Users</h3>
                    <div className="inline-form">
                        <input
                            value={lookupQuery}
                            onChange={(e) => {
                                setLookupQuery(e.target.value);
                                setLookupAttempted(false);
                            }}
                            placeholder="Search by username or display name"
                        />
                        <button className="ghost-btn" onClick={runLookup}>Find</button>
                    </div>
                    <div style={{ marginTop: "0.9rem" }}>
                        {lookupUsers.map((u) => (
                            <div className="lookup-row" key={u.username}>
                                <div className="lookup-avatar" style={{ background: u.avatarColor }} />
                                <div>
                                    <div style={{ fontSize: "0.85rem", fontWeight: 600 }}>{u.displayName || u.username}</div>
                                    <div style={{ fontSize: "0.73rem", color: "rgba(44,36,32,0.55)" }}>@{u.username} · {u.ratingCount} ratings</div>
                                </div>
                                {u.isAdmin && <span className="admin-pill">Admin</span>}
                            </div>
                        ))}
                        {lookupAttempted && lookupQuery.trim() && lookupUsers.length === 0 && (
                            <div className="empty" style={{ padding: "1rem 0" }}>No matching users.</div>
                        )}
                    </div>
                </div>

                <div className="account-card">
                    <h3>Promotion + History</h3>
                    {!account?.isAdmin && (
                        <>
                            <div style={{ fontSize: "0.8rem", color: "rgba(44,36,32,0.65)" }}>Enter admin code to unlock management tools.</div>
                            <div className="inline-form">
                                <input value={promotionCode} onChange={(e) => setPromotionCode(e.target.value)} placeholder="Admin code" />
                                <button className="ghost-btn" onClick={promoteSelf}>Promote</button>
                            </div>
                        </>
                    )}
                    <div style={{ marginTop: "0.8rem" }}>
                        {(account?.recentHistory || []).map((item, idx) => (
                            <div className="lookup-row" key={item.albumId + "-" + idx}>
                                <div className="rating-art" style={{ width: 34, height: 34, background: `linear-gradient(135deg, ${item.color1}, ${item.color2})` }} />
                                <div>
                                    <div style={{ fontSize: "0.82rem", fontWeight: 600 }}>{item.title}</div>
                                    <div style={{ fontSize: "0.7rem", color: "rgba(44,36,32,0.55)" }}>{item.artist}</div>
                                </div>
                                <div className="rating-stars" style={{ marginLeft: "auto", fontSize: "0.8rem" }}>{"★".repeat(item.stars)}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {account?.isAdmin && (
                <div className="account-card" style={{ marginTop: "1rem" }}>
                    <h3>Admin User Management</h3>

                    <div style={{ marginBottom: "1rem", borderBottom: "1px solid rgba(139,115,85,0.25)", paddingBottom: "0.9rem" }}>
                        <div style={{ fontSize: "0.76rem", letterSpacing: "1px", textTransform: "uppercase", color: "rgba(44,36,32,0.5)" }}>Create User</div>
                        <div className="inline-form">
                            <input placeholder="username" value={createForm.username} onChange={(e) => setCreateForm({ ...createForm, username: e.target.value })} />
                            <input placeholder="password" type="password" value={createForm.password} onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })} />
                        </div>
                        <div className="inline-form">
                            <input placeholder="display name" value={createForm.displayName} onChange={(e) => setCreateForm({ ...createForm, displayName: e.target.value })} />
                            <input placeholder="avatar color (#45B7D1)" value={createForm.avatarColor} onChange={(e) => setCreateForm({ ...createForm, avatarColor: e.target.value })} />
                        </div>
                        <div className="inline-form">
                            <input placeholder="bio" value={createForm.bio} onChange={(e) => setCreateForm({ ...createForm, bio: e.target.value })} />
                            <label style={{ display: "inline-flex", alignItems: "center", gap: "0.4rem", fontSize: "0.78rem" }}>
                                <input type="checkbox" checked={createForm.isAdmin} onChange={(e) => setCreateForm({ ...createForm, isAdmin: e.target.checked })} />
                                Admin
                            </label>
                            <button className="ghost-btn" onClick={createUserAsAdmin}>Create</button>
                        </div>
                    </div>

                    {adminUsers.map((u) => (
                        <div className="lookup-row" key={u.username}>
                            <div className="lookup-avatar" style={{ background: u.avatarColor }} />
                            <div>
                                <div style={{ fontSize: "0.84rem", fontWeight: 600 }}>{u.displayName || u.username}</div>
                                <div style={{ fontSize: "0.7rem", color: "rgba(44,36,32,0.55)" }}>
                                    @{u.username} · {u.ratingCount} ratings · {u.isActive ? "active" : "disabled"}
                                    {u.deletedAt ? " · deleted" : ""}
                                </div>
                            </div>
                            <div style={{ marginLeft: "auto", display: "flex", gap: "0.4rem", flexWrap: "wrap", justifyContent: "flex-end" }}>
                                <button className="ghost-btn" onClick={() => toggleRole(u)}>
                                    {u.isAdmin ? "Demote" : "Promote"}
                                </button>
                                {!u.deletedAt && (
                                    <button className="ghost-btn" onClick={() => toggleStatus(u)}>
                                        {u.isActive ? "Disable" : "Enable"}
                                    </button>
                                )}
                                {!u.deletedAt && (
                                    <button className="ghost-btn" onClick={() => resetPassword(u)}>Reset PW</button>
                                )}
                                {!u.deletedAt && (
                                    <button className="ghost-btn" onClick={() => softDeleteUser(u)}>Delete</button>
                                )}
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {message && <div className="form-error" style={{ marginTop: "1rem" }}>{message}</div>}
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
                    <Route path="/playlists"          element={<PlaylistsPage />} />
                    <Route path="/playlists/:id"      element={<PlaylistDetailPage />} />
                    <Route path="/account"            element={<AccountPage />} />
                    <Route path="/profile/:username"  element={<ProfilePage />} />
                    <Route path="/signup"             element={<SignupPage />} />
                    <Route path="/login"              element={<LoginPage />} />
                </Routes>
            </AuthProvider>
        </BrowserRouter>
    );
}
