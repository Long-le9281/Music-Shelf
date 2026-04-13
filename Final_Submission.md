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

#  Project Overview

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

## Contribution Report

Contribution snapshot (excluding working branches, which will skew numbers heavily), as shown in the team report image:

| Name | # of commits to main (excluding working branches) | # of pull requests | Roles and Responsibilities |
|---|---:|---:|---|
| Brandon Dias | 11 | 3 | Created the database, user search and bio features as well as tracked team progress with a burndown chart. |
| Darius Kallistas | 33 | 4 | Worked on frontend UI and controller logic. Handled branch management (hence the amount of commits to main). |
| Daniyal Danish | 5 | 0 | Did Phase 2. Made test cases throughout the project. Bug fixing. Made Trello, kept communication running, organized meetings. |
| Danyal Fahim | 8 | 1 | Created the save and loading system for data, created the initial burndown chart for iteration 1, created the sorting by rating system for albums on a profile, and bug identifying/fixing and quality assurance. |
| Thanh Long Le | 7 | 3 | Developed playlist and comments feature, merge conflict resolution, and software testing, and bug fixing. |

---

# 🔴 4. Test-Driven Development (TDD) (Further documentation can be found in the docs folder)

## The Process We Followed

```
Write failing test first  →  Confirm RED  →  Implement feature  →  Confirm GREEN
```

---


## 🔴 EXAMPLE: Red Test Results (Intentional — Features Not Built Yet)

```
✘ Iteration3ToDoColumnRedDemo                              20 ms
  ✘ EL-15 Finding other/friends profiles is not implemented yet    17 ms
  ✘ EL-20 Comments are not implemented yet                          1 ms
  ✘ EL-16 Sorting profiles by genre is not implemented yet          1 ms
  ✘ EL-5  Number/total time of song listens is not implemented yet  1 ms
```


> They pass once the features are built — that's the green phase.

---

---

#  5. Unit Test Final Report

**Primary Artifact:** `docs/UNIT_TESTS.md`

Release-scope unit-test execution was verified from Maven Surefire reports in `backend/target/surefire-reports/`.

## Execution Summary

| Test File | Tests Run | Failures | Errors | Skipped | Final Status |
|------|---:|---:|---:|---:|---|
| `JwtHelperTest.java` | 2 | 0 | 0 | 0 |  Pass |
| `Iteration1DoneColumnTest.java` | 4 | 0 | 0 | 0 |  Pass |
| `Iteration2InProgressColumnTest.java` | 4 | 0 | 0 | 0 |  Pass |

**Release-scope result:** `10 / 10 passed`

### Stories Verified by the Executed Unit Suite
- `EL-22` signup validation
- `EL-14` rating boundary validation
- `EL-11` empty-search handling
- `EL-24` profile payload correctness
- `EL-13` playlist creation defaults
- `EL-2` seeded song sourcing
- `EL-17` album detail song payload
- `EL-3` admin role mutation

---

# 6. Integration Test Final Report

Integration testing validated interactions between controllers, persistence, auth/security context, and API contracts.

**Primary Artifact:** `docs/INTEGRATION_TESTS.md`

## Execution Summary

| Planned Tests | Executed | Passed | Failed | Open Blocking Defects |
|---:|---:|---:|---:|---:|
| 6 | 6 | 6 | 0 | 0 |

### Final Coverage
- Auth + profile/account chain
- Invalid promotion without session loss
- Rating persistence and read-model propagation
- Playlist add-song and add-album consistency
- Admin role transition and admin-user-management access

### Final Outcome
All planned integration scenarios were run. The resulting report documents the executed outcome for each `IT-*` case and records the bugs/regression risks that were re-verified and closed before sign-off. Further testing documentation can be found under" Music-Shelf/docs"

---

# 7. System Test Final Report

System tests covered full end-to-end workflows from React UI to Spring Boot API to SQLite persistence.

**Primary Artifact:** `docs/SYSTEM_TESTS.md`

## Execution Summary

| Planned Tests | Executed | Passed | Failed | Open Blocking Defects |
|---:|---:|---:|---:|---:|
| 12 | 12 | 12 | 0 | 0 |

### Final Coverage
- Signup/login
- Shelf browse and album detail
- Search workflows
- Album rating and profile/account readback
- Playlist CRUD and playlist population flows
- User lookup and public profile view
- Admin promotion and end-to-end user management

### Final Outcome
All planned shipped-scope system scenarios were run. The final report documents the actual outcome for each `ST-*` case and includes the bugs that were resolved or operationally mitigated before final sign-off.

---

---

# 8. Coverage Strategy (Opaque / Clear / Translucent)

This project deliberately mixes all three testing views to maximize confidence while staying practical.

## Clear Box (CB)
- Used heavily in unit tests (`backend/src/test/java/.../`)
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


<img width="1055" height="883" alt="image" src="https://github.com/user-attachments/assets/5e704f4d-cc9d-4f40-b7fe-83106db3945d" />



---

---

# 10. Burndown Chart & Velocity


<img width="761" height="460" alt="image" src="https://github.com/user-attachments/assets/7db7e40c-fd88-4200-93f7-0538a37468b3" />



---

## Velocity Calculation

<img width="1077" height="346" alt="image" src="https://github.com/user-attachments/assets/4fbdb99b-e01b-4a0c-82c6-01201180c37a" />



# 11. Testing Observations and Defects Resolved

---

## What We Found Useful

- **Unit tests caught real logic bugs early** — edge-case coverage around malformed JWTs, rating bounds, and empty search kept controller behavior locked down before release.
- **Mocking the Database class** made tests fast and isolated; the executed release-scope unit suite completed quickly without touching the runtime SQLite file.
- **Integration/system re-checks were valuable** because they verified that writes were visible across read models, playlist counts stayed consistent, and admin-role changes unlocked the expected UI/API behavior.

---

## What Was Difficult

- **Testing Spring Security context** was complex; `@AuthenticationPrincipal` paths required explicit `UserDetails` setup to avoid `NullPointerException` in controller tests.
- **`Map.of(...)` limitations** mattered in tests because immutable mock rows broke controller paths that append fields or tolerate nulls.
- **Startup timing in the browser** needed documentation because the app can briefly show `connection refused` before both services are fully ready.
- **Team Synergy** Figuring out how to work on a single software  as a cohesive team

---


## 12. Summary, Remaining Gaps, and Submission Sync

| Rubric Item | Status |
|------------|--------|
| Build files & scripts |  `pom.xml`, `package.json` |
| New dev setup docs |  `README.md`, `QUICK_START.md` |
| All members checked in |  5 contributors across 6 branches |
| Trunk + branches shown |  `main`, `Frontend`, `Updating-Database` |
| TDD red test shown |  `Iteration3ToDoColumnRedDemo` |
| Full test suite |  Final report covers 10 executed release-scope unit tests, 6 integration scenarios, and 12 system scenarios |
| Integration testing report |  `docs/INTEGRATION_TESTS.md` updated with executed results and resolved defects |
| System testing report |  `docs/SYSTEM_TESTS.md` updated with executed results and resolved defects |
| Coverage demonstration (CB/TB/OB) |  Explicit rationale and mapping provided |
| Task board updated |  Done 8 / In Progress 2 / To Do 4 |
| Burndown chart |  Iteration 2 complete |
| Velocity calculated |  10.5 story points / sprint |
| Bugs identified |  5 bugs documented |
| Bug fixes completed/mitigated |  5 documented bugs addressed in this branch (4 fixed, 1 mitigated) |
| Testing observations |  Useful + difficult aspects covered |

In this iteration our team further developed Music-Shelf, a full-stack music catalog website built using a Java Spring Boot backend and a React frontend. Within this iteration we successfully implemented an improved looking UI, along with core user-facing features such as account authentication, album browsing, playlist creation, search functionality, and user profile management.

We followed a collaborative workflow using Git with multiple branches and consistent contributions from all team members, ensuring organized development and integration. A key focus of the iteration was Test-Driven Development, where tests were written prior to or alongside implementation. The final test report now records executed results across unit, integration, and system layers and shows that all planned shipped-scope tests were run, with defects documented and resolved before sign-off.

Additionally, we maintained an updated Trello task board and tracked progress using a burndown chart, achieving a sprint velocity of approximately 0.87. Through testing, we identified and addressed issues related to validation paths, authentication flow, data consistency, and startup/user-flow behavior. Overall this iteration strengthened both the technical implementation of our software system and our development practices.


### Remaining Gaps
- Iteration 3 features remain intentionally red/TODO (comments, friends discovery, genre sort, listen metrics).

### Submission Sync Checklist
- **Single source of truth:** Edit `PRESENTATION.md` for final deck content.
- **Submission branch decision:** Use `main` unless team explicitly agrees otherwise before merge/submission.
- **Final pre-submit pass:** Ensure integration/system/coverage/summary sections are present and unmodified by conflicts.



---

*Elgooners Record Shelf · CSCI 2040U · April 2026*
