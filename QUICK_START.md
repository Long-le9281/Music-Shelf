# Quick Start Guide

This guide is the fastest way to compile, run, and test the current project.

## 1) Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+
- Windows PowerShell

## 2) Initiate the Maven Project (using `pom.xml`)
Open IntelliJ IDEA and initialize backend as a Maven project:

1. `File -> Open` and select the `backend` folder.
2. IntelliJ detects `backend/pom.xml` and imports Maven dependencies.
3. Wait until indexing/dependency download completes.

Optional terminal verification:

```powershell
Set-Location .\backend
mvn clean install
```

## 3) Run `App.java` Manually (green play button)
In IntelliJ:
1. Open `backend/src/main/java/com/elgooners/app/App.java`.
2. Click the green play button next to `main`.
3. Confirm backend is running at `http://localhost:8080`.

## 4) Run the Frontend (npm)
From repo root, open a new terminal and run:

```powershell
Set-Location .\frontend
npm install
npm start
```

Open:
- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080`

## 5) Unit Testing
All test files are under `backend/src/test/java/com/elgooners/app/`.

### A) Iteration 1 Done Column (Green)

```powershell
Set-Location .\backend
mvn -Dtest=Iteration1DoneColumnTest test
```

### B) Iteration 2 In Progress Column (Green)

```powershell
Set-Location .\backend
mvn -Dtest=Iteration2InProgressColumnTest test
```

### C) Baseline Auth Unit Test (Green)

```powershell
Set-Location .\backend
mvn -Dtest=JwtHelperTest test
```

### D) Iteration 3 To-Do Column Demo (Red by design)

```powershell
Set-Location .\backend
mvn -Dtest=Iteration3ToDoColumnRedDemo test
```

`Iteration3ToDoColumnRedDemo` is intentionally failing to demonstrate unimplemented tasks in a TDD red phase.

## 6) Common Issues
- `mvn` not recognized:
  - Install Maven and add it to PATH.
- Port 8080 busy:
  - Stop the process using the port or change backend port in `backend/src/main/resources/application.properties`.
- Frontend cannot load data:
  - Ensure backend is running before frontend.
- Missing catalog data:
  - Confirm `database/albums.csv` and `database/songs.csv` are present.

## 7) Related Docs
- `README.md`
- `LAB9_DEMO_PRESENTATION.md`
- `docs/SYSTEM_TESTS.md`
- `docs/UNIT_TESTS.md`
