# 🎵 Elgooners Record Shelf
### CSCI 2040U — Iteration 1

---

## Folder Structure

```
elgooners/
│
├── database/
│   └── setup.py          ← Run this FIRST. Creates the database + 25 albums.
│
├── backend/
│   ├── pom.xml           ← Maven config (dependencies)
│   └── src/main/java/com/elgooners/app/
│       └── App.java      ← Entire Spring Boot backend in one file
│
└── frontend/
    ├── package.json      ← npm config
    └── src/
        └── App.jsx       ← Entire React frontend in one file
```

---

## Step-by-Step Setup (Do This Once)

### Step 1 — Set Up the Database
Make sure Python 3 is installed. Then:
```
cd database
python setup.py
```
This creates `database/elgooners.db` with all tables and 25 seed albums.
You only need to do this once. If you mess up the data, just delete
`elgooners.db` and run `setup.py` again.

---

### Step 2 — Start the Backend
Option A — IntelliJ (recommended):
1. Open IntelliJ IDEA
2. File → Open → select the `backend` folder
3. IntelliJ will detect it as a Maven project
4. Wait for Maven to download dependencies (~1 min first time)
5. Run `App.java` (the green play button)
6. You should see "Started App" in the console

Option B — Terminal:
```
cd backend
mvn spring-boot:run
```

Backend runs at: http://localhost:8080

---

### Step 3 — Start the Frontend
Open a NEW terminal window (keep backend running):
```
cd frontend
npm install        ← only needed the first time
npm start
```
App opens automatically at: http://localhost:3000

---

## How to Use the App

| Feature | How |
|---------|-----|
| Browse albums | **Scroll up/down** on the main shelf to flip through records |
| View album detail | The right panel updates automatically as you scroll |
| Rate an album | Click a star (you must be signed in) |
| Search | Click "Search" in the nav, type anything |
| Profile | Click your username in the nav after signing in |

---

## Team File Assignments (Iteration 1)

| File | Owner | User Stories |
|------|-------|-------------|
| `database/setup.py` | Brandon + Thanh | Schema design |
| `backend/App.java` — Auth section | Brandon + Thanh | US1, US2 |
| `backend/App.java` — Search section | Brandon | US3 |
| `backend/App.java` — Rating section | Brandon + Danyal | US4 |
| `frontend/App.jsx` — ShelfPage | Darius + Daniyal | US7 |
| `frontend/App.jsx` — ProfilePage | Darius | US5 |
| `frontend/App.jsx` — StarRating | Danyal | US4 |

---

## How to Add a New Feature

### New API Endpoint (Backend)
In `App.java`, find the Controller section that makes sense (Auth, Album, Rating, Profile)
and add a new method with `@GetMapping` or `@PostMapping`:

```java
@GetMapping("/albums/top-rated")
public List<Map<String, Object>> getTopRated() {
    // add a new method to the Database class, call it here
    return db.getTopRatedAlbums();
}
```

Then add the SQL in the `Database` class above.

### New Page (Frontend)
In `App.jsx`:
1. Write a new function: `function MyNewPage() { ... }`
2. Add a route at the bottom: `<Route path="/my-page" element={<MyNewPage />} />`
3. Add a nav link in `<Navbar>`: `<Link to="/my-page">My Page</Link>`

### New Database Column
1. Add the column to `setup.py` in the `CREATE TABLE` block
2. Delete `elgooners.db` and run `python setup.py` again
3. Update the SQL queries in the `Database` class in `App.java`

---

## Troubleshooting

**"Port 8080 already in use"**
→ Another process is using the port. Find and stop it, or change
  `server.port=8080` in `backend/src/main/resources/application.properties`

**"No albums found" on the shelf**
→ You haven't run `python setup.py` yet, or the database path is wrong.
  Check that `database/elgooners.db` exists.

**CORS error in browser console**
→ Make sure the backend is running on port 8080 before starting the frontend.

**Maven download takes forever**
→ Normal on first run. Maven downloads ~50MB of dependencies once,
  then caches them. Subsequent runs are fast.

**npm install fails**
→ Make sure Node.js v18+ is installed: https://nodejs.org
