# Lab 9 Demo Presentation Pack

This guide maps your current project to the **Lab 9 Demo Presentation** rubric and gives a concrete live-demo flow.

## 1) Build Files and Scripts (Rubric Evidence)

### Added scripts
- `scripts/build-and-test.ps1` - backend test run + frontend production build
- `scripts/run-backend.ps1` - starts Spring Boot API on `http://localhost:8080`
- `scripts/run-frontend.ps1` - starts React UI on `http://localhost:3000`

### Existing build config files
- `backend/pom.xml` (Maven)
- `frontend/package.json` (npm)

---

## 2) New Developer: Compile, Test, Run

### Prereqs
- Java 17+
- Maven 3.9+
- Node.js 18+
- npm 9+

### One-command verify build
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-and-test.ps1
```

### Start backend and frontend (two terminals)
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-frontend.ps1
```

---

## 3) Version Control Demonstration

### Show every group member has check-ins
```powershell
git --no-pager shortlog -sn --all
```

### Show trunk and branches
```powershell
git --no-pager branch -a
git --no-pager log --all --graph --oneline --decorate -n 30
```

Suggested talking points:
- Mainline branch: `main`
- Active feature branches: `Frontend`, `Updating-Database`, plus remote feature branches
- Merge commits showing integration across tracks

---

## 4) TDD Demo (Board Columns)

Use tests that map directly to the task board columns in your screenshot.

### Done column (Iteration 1) - all green
Test file: `backend/src/test/java/com/elgooners/app/Iteration1DoneColumnTest.java`

```powershell
Set-Location .\backend
mvn -Dtest=Iteration1DoneColumnTest test
```

### In Progress column (Iteration 2) - all green
Test file: `backend/src/test/java/com/elgooners/app/Iteration2InProgressColumnTest.java`

```powershell
Set-Location .\backend
mvn -Dtest=Iteration2InProgressColumnTest test
```

### To Do column (Iteration 3) - intentionally red
Test file: `backend/src/test/java/com/elgooners/app/Iteration3ToDoColumnRedDemo.java`

```powershell
Set-Location .\backend
mvn -Dtest=Iteration3ToDoColumnRedDemo test
```

Expected demo result for To Do: **FAIL (Red)** because those tasks are not implemented yet.

---

## 5) Task Board, Burn Down, and Velocity

Prepare screenshots/exports before presentation:
- Updated task board (Sprint backlog + Done column)
- Burn down chart for current iteration
- Velocity table (previous sprint points vs current completed points)

Suggested velocity slide table:
- Sprint N-1 Planned / Completed
- Sprint N Planned / Completed
- Velocity trend (up/down/stable)

---

## 6) Unit Test Deliverables to Show

- Passing tests for Iteration 1 done tasks: `Iteration1DoneColumnTest`
- Passing tests for Iteration 2 in-progress tasks: `Iteration2InProgressColumnTest`
- Red tests for Iteration 3 to-do tasks: `Iteration3ToDoColumnRedDemo`
- Existing auth utility unit test: `JwtHelperTest`

Optional aggregate run (only default tests):
```powershell
Set-Location .\backend
mvn test
```

---

## 7) Interesting Bugs + Observations Slide

Use this structure:
- Bug 1: root cause, symptom, fix, prevention
- Bug 2: root cause, symptom, fix, prevention
- Observation: where testing was difficult and why
- Observation: where testing saved time or caught regressions

Good examples for this codebase:
- Auth edge cases (disabled/deleted users)
- Data import deduplication (`albums` / `songs` identity indexes)
- Endpoint authorization behavior (`/api/admin/**`)

---

## 8) 10-15 Minute Demo Run Order

1. Project intro + architecture (1 min)
2. Build/test script run (2 min)
3. App walkthrough (2-3 min)
4. Git evidence: members + branches + graph (2 min)
5. TDD red test + suite (2-3 min)
6. Task board + burndown + velocity (2 min)
7. Bugs found + observations (1-2 min)

---

## 9) Presentation Backup Plan

If live app fails:
- Show `MVP_Demo/screenshots/`
- Show latest successful `mvn test` output screenshot
- Continue with Git, TDD, and metrics sections (these do not require live UI)
