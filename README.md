# Elgooners Record Shelf
### CSCI 2040U - Iteration 3

## Project Overview
Elgooners Record Shelf is a full-stack app for browsing albums, searching music, rating albums, managing profiles, and creating playlists.

- Backend: Spring Boot + SQLite (`backend/`)
- Frontend: React (`frontend/`)
- Catalog/data files: CSV + runtime DB (`database/`)

## Documentation Index
- Runbook and rubric mapping: `PRESENTATION.md`
- Quick startup and testing guide: `QUICK_START.md`
- System test plan and workflow coverage: `docs/SYSTEM_TESTS.md`
- Unit test documentation: `docs/UNIT_TESTS.md`

## Repository Structure

```text
Music-Shelf/
|-- backend/
|   |-- pom.xml
|   `-- src/main/java/.../App.java
|-- frontend/
|   |-- package.json
|   `-- src/App.jsx
|-- database/
|   |-- albums.csv
|   |-- songs.csv
|   |-- users.csv
|   `-- user_library.csv
```

## Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+

## Build and Run

### Recommended startup on Windows
From the repo root (`Music-Shelf`), run:

```powershell
Set-Location "C:\Users\PC\Desktop\Music-Shelf"
.\run.bat
```

This launcher:
- checks that Java, Maven, and npm are available
- offers first-time setup help if `setup.sh` is present and setup has not been completed yet
- starts the backend on port `8080`
- waits briefly, then starts the frontend on port `3000`
- opens the frontend in your browser

After startup:
- Backend URL: `http://localhost:8080`
- Frontend URL: `http://localhost:3000`

### Start backend and frontend separately
If you want to launch each service in its own step, use these scripts from the repo root:

```powershell
Set-Location "C:\Users\PC\Desktop\Music-Shelf"
.\run-backend.bat
```

Open a second terminal for the frontend:

```powershell
Set-Location "C:\Users\PC\Desktop\Music-Shelf"
.\run-frontend.bat
```

### Manual fallback
If you prefer to build and run the application manually:

Build the backend:

```powershell
Set-Location .\backend
mvn clean install
```

Then start the backend in IntelliJ:
- Open the `backend` folder as a Maven project (it uses `backend/pom.xml`).
- Wait for dependency sync.
- Open `backend/src/main/java/.../App.java` and run it with the green play button.

Start the frontend in a separate terminal:

```powershell
Set-Location .\frontend
npm install
npm start
```



## Key Features Present
- Authentication: signup/login with JWT
- Album browsing and details
- Search albums/songs
- Album ratings
- User profiles
- Playlist CRUD + song/album playlist actions
- Admin user management endpoints

## Troubleshooting
- `mvn: command not found` -> install Maven and ensure it is in PATH.
- Port 8080 already in use -> stop conflicting process or change `server.port` in `backend/src/main/resources/application.properties`.
- Frontend cannot reach backend -> ensure backend is running before `npm start`.
- Browser shows `connection refused` on first load -> the backend or frontend may still be starting; wait 10-20 seconds, then refresh the page.
- Missing data in app -> confirm CSV files exist in `database/` and restart backend.
