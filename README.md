# Elgooners Record Shelf
### CSCI 2040U - Final Product

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
From the repo root (`Music-Shelf`):

To Start from scratch:
Run.bat

To Start either the server or client seperately 
run-backend.bat
run-frontend.bat

## Unit Testing
All tests are in `backend/src/test/java/com/elgooners/app/`.

### Green tests

```powershell
Set-Location .\backend
mvn -Dtest=Iteration1 test
mvn -Dtest=Iteration2 test
mvn -Dtest=JwtHelperTest test
mvn -Dtest=Iteration3 test
```


## Key Features Present
- Authentication: signup/login with JWT
- Album browsing and details
- Search albums/songs
- Album ratings
- User profiles
- Playlist CRUD + song/album playlist actions
- Admin user management of endpoints
- User comments on songs/albums
- 

## Troubleshooting
- `mvn: command not found` -> install Maven and ensure it is in PATH.
- Port 8080 already in use -> stop conflicting process or change `server.port` in `backend/src/main/resources/application.properties`.
- Frontend cannot reach backend -> ensure backend is running before `npm start`.
- Missing data in app -> confirm CSV files exist in `database/` and restart backend.
