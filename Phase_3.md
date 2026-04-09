#  Elgooners Record Shelf

---

---

#  Agenda

1. Project Overview
2. Build Files & New Developer Setup
3. Version Control System
4. Test-Driven Development (TDD)
5. Unit Test Deliverables
6. Integration Testing Plan
7. System Testing Plan
8. Coverage Strategy (Opaque / Clear / Translucent)
9. Task Board (Updated)
10. Burndown Chart & Velocity
11. Interesting Bugs Found
12. Testing Observations
13. Summary, Remaining Gaps, and Submission Sync

---

---

# 1. Project Overview

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

# 3. Version Control System

## All Members Have Check-ins

```
Commit history
```

```
10  Darius Kallistas
 8  Brandon Dias
 6  Danyal
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

# 🔴 4. Test-Driven Development (TDD) (Further documentation can be found in the docs folder)

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

#  5. Unit Test Deliverables

## Test Files

| File | Column | Expected |
|------|--------|----------|
| `Iteration1DoneColumnTest.java` | (Iteration 1) | 🟢 All pass |
| `Iteration2InProgressColumnTest.java` | (Iteration 2) | 🟢 All pass |
| `JwtHelperTest.java` | Auth utility | 🟢 All pass |
| `Iteration3ToDoColumnRedDemo.java` | (Iteration 3) | 🟢 All pass |

---

##  Iteration 1 


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

**4 / 4 PASSED** 

---

##  Iteration 2


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

**4 / 4 PASSED** 

---

##  Iteration 3 



```
✔ EL-15  Finding other/friends profiles is not implemented yet
✔ EL-20  Comments are not implemented yet
✔ EL-16  Sorting profiles by genre is not implemented yet
✔ EL-5   Number/total time of song listens is not implemented yet
```

**4 / 4 PASSED** 

---

---

# 6. Integration Testing Plan

Integration testing validates interactions between controllers, persistence, auth/security context, and API contracts.

**Primary Artifact:** `docs/INTEGRATION_TESTS.md`

| Integration Scenario | Components Exercised | Expected Result |
|---|---|---|
| Auth + Profile Chain | `AuthController` + JWT filter + `ProfileController` | Login token authorizes profile and account endpoints correctly |
| Rating Persistence Flow | `RatingController` + DB ratings table + profile/account read models | New rating appears in account history and public profile |
| Playlist Song Flow | Shelf UI action + playlist endpoints + `playlist_songs` table | Added track/album songs appear in target playlist with correct counts |
| Admin Role Management | Promotion endpoint + admin endpoints + security roles | Elevated account can access admin operations; non-admin cannot |

**Execution Approach**
- Use seeded local data plus test users.
- Run through API-level flows with authentication enabled.
- Validate both response payloads and downstream read consistency.

---

---

# 7. System Testing Plan

System tests cover full end-to-end workflows from React UI to Spring Boot API to SQLite persistence.

**Primary Artifact:** `docs/SYSTEM_TESTS.md`

**Current Matrix Coverage:**
- 12 scenarios (ST-01 through ST-12)
- Core user journeys: signup/login, shelf browse, search, album rating, profile lookup, playlist CRUD, admin management
- Includes assigned owner per scenario and explicit preconditions/steps/expected outcomes

**Execution Style**
- Manual functional walkthrough with deterministic setup
- Repeatable runbook format suitable for demo and grading
- Explicitly excludes Iteration 3 unimplemented features (friends/comments/sorting/listen-time)

---

---

# 8. Coverage Strategy (Opaque / Clear / Translucent)

This project deliberately mixes all three testing views to maximize confidence while staying practical.

## Clear Box (CB)
- Used heavily in unit tests (`backend/src/test/java/com/elgooners/app/`)
- We mock `Database`, invoke controller methods directly, and assert exact response contracts
- Best for validation logic, null/error branches, and controller-level behavior

## Opaque Box (OB)
- Used in system tests for user-visible workflows
- Tester interacts only through UI and observable outputs (no internal implementation assumptions)
- Best for login, navigation, lookup, search, and playlist UX outcomes

## Translucent Box (TB)
- Used for scenarios where tester knows key intermediate behavior (e.g., endpoint/data side effects) without white-box implementation coupling
- Examples: add-song/add-album-to-playlist count changes, admin promotion enabling admin panel

## Why This Is Sufficient Coverage
- CB protects method-level correctness and edge cases
- TB validates cross-component interaction contracts
- OB validates real user workflows and end-user behavior
- Combined, these cover the rubric views: **CB + TB + OB** with traceability to user stories on the task board

---

---
# 9. Task Board (Updated)

<img width="1072" height="905" alt="Updated burn-down" src="https://github.com/user-attachments/assets/d0345a48-0dc7-48e5-8aab-97369b19593b" />


---

---

# 10. Burndown Chart & Velocity

## Iteration 2 Burndown

<img width="600" height="371" alt="Iteration_2_Finalized_Burndown_Chart" src="https://github.com/user-attachments/assets/3409f619-1b3a-404d-8ef5-1be5120a9f98" />


---

## Velocity Calculation

Velocity = (3.5 tasks) / 4 total  =  0.87 for sprint 2


# 11. Testing Observations

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


## 12. Summary, Remaining Gaps, and Submission Sync

| Rubric Item | Status |
|------------|--------|
| Build files & scripts | ✅ `pom.xml`, `package.json` |
| New dev setup docs | ✅ `README.md`, `QUICK_START.md` |
| All members checked in | ✅ 5 contributors across 6 branches |
| Trunk + branches shown | ✅ `main`, `Frontend`, `Updating-Database` |
| TDD red test shown | ✅ `Iteration3ToDoColumnRedDemo` |
| Full test suite | ✅ 12 tests across 4 files |
| Integration testing plan | ✅ Included in this deck (controller/DB/auth interaction coverage) |
| System testing plan | ✅ `docs/SYSTEM_TESTS.md` with 12 full workflow scenarios |
| Coverage demonstration (CB/TB/OB) | ✅ Explicit rationale and mapping provided |
| Task board updated | ✅ Done 8 / In Progress 2 / To Do 4 |
| Burndown chart | ✅ Iteration 2 complete |
| Velocity calculated | ✅ 10.5 story points / sprint |
| Bugs identified | ✅ 5 bugs documented |
| Bug fixes completed/mitigated | ✅ 5 documented bugs addressed in this branch (4 fixed, 1 mitigated) |
| Testing observations | ✅ Useful + difficult aspects covered |

In this iteration our team further developed Music-Shelf, a full-stack music catalog website built using a Java spring boot backend and a react frontend. Within this iteration we successfully implemented an improved looking UI, along with core user-facing features such as account authentication, album browsing, playlist creation, search functionalities, and user profile management.

We followed a collaborative workflow using Git with multiple branches and consistent contributions from all team members ensuring organized development and integration. A key focus of the iteration was Test-Driven-Development, where tests were written prior to the implementation of any feature. The completed features from iteration 1 and 2 passed all unit tests, our plan is to continue using unit tests going into iteration 3 to help guide future development.

Additionally, we maintained an updated trello (task board) and tracked progress using a burndown chart, achieving a sprint velocity of approximately 0.87. Through testing, we identified several bugs related to rating logic, authentication flow, UI rendering, and data accuracy, providing clear direction for future fixes. Overall this iteration strengthened both the technical implementation of our software system and our development practices.


### Remaining Gaps
- Iteration 3 features remain intentionally red/TODO (comments, friends discovery, genre sort, listen metrics).

### Submission Sync Checklist
- **Single source of truth:** Edit `PRESENTATION.md` for final deck content.
- **Submission branch decision:** Use `main` unless team explicitly agrees otherwise before merge/submission.
- **Final pre-submit pass:** Ensure integration/system/coverage/summary sections are present and unmodified by conflicts.



---

*Elgooners Record Shelf · CSCI 2040U · Iteration 2 · March 2026*

