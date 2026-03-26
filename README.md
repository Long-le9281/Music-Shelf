# Elgooners Record Shelf
### CSCI 2040U - Iteration 2

## Project Overview
Elgooners Record Shelf is a full-stack app for browsing albums, searching music, rating albums, managing profiles, and creating playlists.

- Backend: Spring Boot + SQLite (`backend/`)
- Frontend: React (`frontend/`)
- Catalog/data files: CSV + runtime DB (`database/`)

## Documentation Index
- Runbook and rubric mapping: `LAB9_DEMO_PRESENTATION.md`
- Quick startup and testing guide: `QUICK_START.md`

## Repository Structure

```text
elgooners-iteration2/
|-- backend/
|   |-- pom.xml
|   `-- src/main/java/com/elgooners/app/App.java
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

## Build and Run (Manual)
From the repo root (`elgooners-iteration2`):

```powershell
Set-Location .\backend
mvn clean install
```

Then start the backend in IntelliJ:
- Open the `backend` folder as a Maven project (it uses `backend/pom.xml`).
- Wait for dependency sync.
- Open `backend/src/main/java/com/elgooners/app/App.java` and run it with the green play button.

Start the frontend in a separate terminal:

```powershell
Set-Location .\frontend
npm install
npm start
```

- Backend URL: `http://localhost:8080`
- Frontend URL: `http://localhost:3000`

## Unit Testing
All tests are in `backend/src/test/java/com/elgooners/app/`.

### Green tests (expected to pass)

```powershell
Set-Location .\backend
mvn -Dtest=Iteration1DoneColumnTest test
mvn -Dtest=Iteration2InProgressColumnTest test
mvn -Dtest=JwtHelperTest test
```

### Red tests (expected to fail intentionally)

```powershell
Set-Location .\backend
mvn -Dtest=Iteration3ToDoColumnRedDemo test
```

`Iteration3ToDoColumnRedDemo` represents unimplemented To-Do board items and is intentionally red for TDD/demo use.

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
- Missing data in app -> confirm CSV files exist in `database/` and restart backend.
