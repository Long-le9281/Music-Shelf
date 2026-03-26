# 🎵 Elgooners Record Shelf
## Lab 9 — Demo Presentation
### CSCI 2040U · March 26, 2026

---

---

# 📋 Agenda

1. Project Overview
2. Build Files & New Developer Setup
3. Version Control System
4. Test-Driven Development (TDD)
5. Unit Test Deliverables
6. Task Board (Updated)
7. Burndown Chart & Velocity
8. Interesting Bugs Found
9. Testing Observations

---

---

# 🏗️ 1. Project Overview

**Elgooners Record Shelf** — A full-stack music cataloguing app

| Layer | Technology |
|-------|-----------|
| Backend | Java 17 · Spring Boot 3 · SQLite |
| Frontend | React 18 · React Router |
| Auth | JWT tokens · BCrypt passwords |
| Build | Maven (`pom.xml`) · npm (`package.json`) |

**Key Features Shipped:**
- Account sign-up & login
- Album browsing (vinyl bin carousel)
- Star ratings per album
- Search by title / artist / genre
- User profile pages
- Custom playlists
- Admin user management

---

---

# 🔧 2. Build Files & New Developer Setup

## Build Configuration Files

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Maven — all Java dependencies + Spring Boot plugin |
| `frontend/package.json` | npm — React + Router + scripts |
| `backend/src/main/resources/application.properties` | Server port, DB path |

---

## Step 1 — Initialize Maven Project

Open IntelliJ IDEA:

```
File → Open → select the backend/ folder
```

IntelliJ detects `backend/pom.xml` and downloads all dependencies automatically (~1 min first time).

---

## Step 2 — Run the Backend

Open `backend/src/main/java/com/elgooners/app/App.java`

→ Click the **green play button** next to `main`

→ Console shows: `Started App in X seconds`

→ Backend live at: **http://localhost:8080**

---

## Step 3 — Run the Frontend

Open a new terminal:

```bash
cd frontend
npm install       # first time only
npm start
```

→ App opens automatically at: **http://localhost:3000**

---

---

# 🌿 3. Version Control System

## All Members Have Check-ins

```
Commit history
```

```
10  Darius Kallistas
 8  Brandon Dias
 6  Epicfunguyddan
 5  Daniyal
 6  Thanh Long Le
```

---

## Branch Structure

```
git branch -a
```

```
  remotes/origin/Frontend
  remotes/origin/Backend
  remotes/origin/Database&Controller
  remotes/origin/Database-&-data-sourcing
  remotes/origin/main
```

---

## Commit Graph (Main Trunk + Branches)

```
git log --all --graph --oneline --decorate -n 20
```

```
* 682b8803 (HEAD -> Frontend) Add Lab9 demo scripts/tests and untrack conflicting IntelliJ metadata
* ed333fcb merging new database logic - fixed ui bugs
* fe79b61c merging new database logic
*   6936b172 Merge Updating-Database into Frontend
 \
  * 0a37ead1 (Updating-Database) Finished Database
  * 88af5c7d Made some changes
* c1206968 (main) Add MVP walkthrough documentation
```

**Trunk:** `main`  
**Feature branches merged:** `Updating-Database`, `new-stack`, `new-ui`, `Frontend`

---

---

# 🔴 4. Test-Driven Development (TDD)

## The Process We Followed

```
Write failing test first  →  Confirm RED  →  Implement feature  →  Confirm GREEN
```

---

## TDD Demo: Iteration 3 (To-Do Features — RED First)

These tests were written **before implementation** to document what still needs to be built.

**Run the red tests:**

```bash
# In backend/ directory
mvn -Dtest=Iteration3ToDoColumnRedDemo test
```

---

## 🔴 Red Test Results (Intentional — Features Not Built Yet)

```
✘ Iteration3ToDoColumnRedDemo                              20 ms
  ✘ EL-15 Finding other/friends profiles is not implemented yet    17 ms
  ✘ EL-20 Comments are not implemented yet                          1 ms
  ✘ EL-16 Sorting profiles by genre is not implemented yet          1 ms
  ✘ EL-5  Number/total time of song listens is not implemented yet  1 ms
```

> These tests define the **contract for Iteration 3**.  
> They pass once the features are built — that's the green phase.

---

---

# ✅ 5. Unit Test Deliverables

## Test Files

| File | Column | Expected |
|------|--------|----------|
| `Iteration1DoneColumnTest.java` | Done (Iteration 1) | 🟢 All pass |
| `Iteration2InProgressColumnTest.java` | Done (Iteration 2) | 🟢 All pass |
| `JwtHelperTest.java` | Auth utility | 🟢 All pass |
| `Iteration3ToDoColumnRedDemo.java` | To-Do (Iteration 3) | 🔴 All fail (TDD red phase) |

---

## 🟢 Iteration 1 — Done Column Tests

```bash
mvn -Dtest=Iteration1DoneColumnTest test
```

```
✔ EL-22  Create an account / signup-login:
         signup rejects passwords shorter than 6
✔ EL-14  Star ratings:
         rating values outside 1-5 are rejected
✔ EL-11  Search bar:
         empty query returns empty result set with zero meta counts
✔ EL-24  View User Profile Page:
         profile endpoint returns user info and ratings
```

**4 / 4 PASSED** ✅

---

## 🟢 Iteration 2 — In Progress Column Tests

```bash
mvn -Dtest=Iteration2InProgressColumnTest test
```

```
✔ EL-13  Create custom albums or playlists:
         create playlist succeeds with default category        906 ms
✔ EL-2   Database and data sourcing:
         songs endpoint returns sourced song list                3 ms
✔ EL-17  Record shelf UI design:
         album detail includes songs for the selected record     2 ms
✔ EL-3   User Authentication and Roles:
         admin can update a target user's role                   3 ms
```

**4 / 4 PASSED** ✅

---

## 🔴 Iteration 3 — To-Do Column (Red Phase)

```bash
mvn -Dtest=Iteration3ToDoColumnRedDemo test
```

```
✘ EL-15  Finding other/friends profiles is not implemented yet
✘ EL-20  Comments are not implemented yet
✘ EL-16  Sorting profiles by genre is not implemented yet
✘ EL-5   Number/total time of song listens is not implemented yet
```

**0 / 4 PASSED** 🔴 — Intentional TDD red phase

---

---

# 📌 6. Task Board (Updated)



---

---

# 📉 7. Burndown Chart & Velocity

## Iteration 2 Burndown



---

## Velocity Calculation

Velocity = (12 + 9) / 2  =  10.5 story points per sprint
```
## Velocity Trend

| Sprint | Story Points | Trend |
|--------|-------------|-------|
| Iteration 1 | 12 pts | Baseline |
| Iteration 2 | 9 pts | ↓ Slight decrease — UI complexity higher than estimated |
| Average | **10.5 pts/sprint** | Stable |

> The Day 3 spike in the burndown was caused by scope re-assessment after merging `Updating-Database` into `Frontend` revealed additional integration work.

---

### Projection for Iteration 3

```
Remaining To-Do: EL-15 (3) + EL-5 (3) + EL-16 (1) + EL-20 (2) = 9 story points
Estimated completion at current velocity: < 1 sprint
```

---

---

# 🐛 8. Interesting Bugs Found

---

## Bug 1 — Rating a Song Rates the Whole Album

**Section:** Shelf  
**Symptom:** Rating a single song applies the same star rating to every other song in the album and to the album itself.  
**Root Cause:** Rating logic targets `albumId` rather than an individual `songId`; there is no per-song rating pathway.  
**Status:** Identified — fix requires a separate `song_ratings` table and UI toggle.

---

## Bug 2 — Incorrect Admin Code Boots User to Login Screen

**Section:** Account  
**Symptom:** Entering the wrong admin promotion code silently redirects to the login screen instead of showing an inline error message.  
**Root Cause:** Auth error handling bubbles to a global 403 interceptor which triggers a logout redirect.  
**Status:** Identified — needs a targeted error response in the promote endpoint UI handler.

---

## Bug 3 — User Lookup Shows "Doesn't Exist" Before Search

**Section:** Account  
**Symptom:** The user lookup panel shows "user does not exist" immediately on page load before any search has been attempted.  
**Root Cause:** Component renders the empty-state error message on initial mount before the first query fires.  
**Status:** Identified — should only show "No matching users" after the Find button is explicitly pressed.

---

## Bug 4 — Admin "Add to Database" Panel Visible to Non-Admins

**Section:** Search  
**Symptom:** When searching for an album or song, the "Add this to database" panel briefly flashes for non-admin users.  
**Root Cause:** Visibility of the panel is controlled by a CSS transition that renders before the role check completes.  
**Status:** Identified — panel should be conditionally rendered based on `isAdmin` flag, not just hidden with CSS.

---

## Bug 5 — Inaccurate Release Years for Some Albums / Songs

**Section:** Shelf  
**Symptom:** Some albums and songs display incorrect release years.  
**Root Cause:** Data sourcing from iTunes API returned inconsistent metadata; years were not validated on import.  
**Status:** Known data quality issue — requires a catalog audit pass on `albums.csv` and `songs.csv`.

---

---

# 💡 9. Testing Observations

---

## What We Found Useful

- **Unit tests caught real logic bugs early** — the star rating 1–5 boundary check test immediately revealed the out-of-range path was silently returning 400 without a clear message.
- **Mocking the Database class** made tests fast and isolated; each test runs in under 10 ms without touching the real SQLite file.
- **TDD red phase for Iteration 3** gave us a concrete spec for unbuilt features before a single line of implementation was written — the failing tests *are* the documentation.

---

## What Was Difficult

- **Testing Spring Security context** was complex; injecting `@AuthenticationPrincipal` required careful mock setup of `UserDetails` to avoid `NullPointerException` at the controller layer.
- **Map.of() limitations** — `Map.of(...)` does not allow `null` values, which caused silent runtime failures in mock data until we switched to `new HashMap<>()` for rows that could have nullable columns.
- **AlbumController.getOneAlbum** mutates the map returned by the DB layer (calling `.put("songs", ...)`) — using an immutable `Map.of()` return from the mock caused `UnsupportedOperationException` at runtime.

---


## Summary

| Rubric Item | Status |
|------------|--------|
| Build files & scripts | ✅ `pom.xml`, `package.json` |
| New dev setup docs | ✅ `README.md`, `QUICK_START.md` |
| All members checked in | ✅ 5 contributors across 6 branches |
| Trunk + branches shown | ✅ `main`, `Frontend`, `Updating-Database` |
| TDD red test shown | ✅ `Iteration3ToDoColumnRedDemo` |
| Full test suite | ✅ 12 tests across 4 files |
| Task board updated | ✅ Done 8 / In Progress 2 / To Do 4 |
| Burndown chart | ✅ Iteration 2 complete |
| Velocity calculated | ✅ 10.5 story points / sprint |
| Bugs identified | ✅ 5 bugs documented |
| Testing observations | ✅ Useful + difficult aspects covered |

---

*Elgooners Record Shelf · CSCI 2040U · Iteration 2 · March 2026*

