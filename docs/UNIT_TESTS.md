# Unit Test Final Report

This document records the final executed unit-test results for the backend code in `backend/src/test/java/.../`.

All unit tests in this report are **Clear Box (CB)** tests. They instantiate controllers or helpers directly, inject mocked dependencies where needed, and assert against exact response/state behavior.

## Evidence Source

Release-scope unit test evidence was taken from Maven Surefire outputs in `backend/target/surefire-reports/`.

Verified report files:
- `com.elgooners.app.JwtHelperTest.txt`
- `com.elgooners.app.Iteration1DoneColumnTest.txt`
- `com.elgooners.app.Iteration2InProgressColumnTest.txt`
- `TEST-com.elgooners.app.JwtHelperTest.xml`
- `TEST-com.elgooners.app.Iteration1DoneColumnTest.xml`
- `TEST-com.elgooners.app.Iteration2InProgressColumnTest.xml`

The Surefire XML metadata shows the recorded execution cycle ran on `2026-04-09`.

---

## Execution Summary

| Test File | Scope | Tests Run | Failures | Errors | Skipped | Final Status |
|---|---|---:|---:|---:|---:|---|
| `JwtHelperTest.java` | UT-01-CB, UT-02-CB | 2 | 0 | 0 | 0 | Pass |
| `Iteration1DoneColumnTest.java` | UT-03-CB to UT-06-CB | 4 | 0 | 0 | 0 | Pass |
| `Iteration2InProgressColumnTest.java` | UT-07-CB to UT-10-CB | 4 | 0 | 0 | 0 | Pass |

**Release-scope total:** `10 / 10 passed`

### Backlog / TDD Note
`Iteration3ToDoColumnRedDemo.java` remains an intentional red-phase specification file for unimplemented Iteration 3 backlog items. It is tracked in this report for completeness, but it is **excluded from the shipped-scope pass rate** because those stories were not part of the released feature set.

---

## Final Unit Test Matrix

| Test ID | Method / Class | Actual Result | Evidence / Notes | Final Status |
|---|---|---|---|---|
| UT-01-CB | `JwtHelper.createToken()` + `getUsernameFromToken()` | Executed successfully; generated token decoded back to original username | Covered by `JwtHelperTest`; Surefire reports 2/2 passing in file | Pass |
| UT-02-CB | `JwtHelper.getUsernameFromToken()` with malformed token | Executed successfully; invalid token returned `null` without uncaught exception | Covered by `JwtHelperTest`; testcase `invalidTokenReturnsNull` passed | Pass |
| UT-03-CB | `AuthController.signup()` short password validation | Executed successfully; short password rejected with `400 Bad Request` | Covered by `Iteration1DoneColumnTest`; testcase passed | Pass |
| UT-04-CB | `RatingController.rateAlbum()` out-of-range stars | Executed successfully; invalid rating rejected before persistence | Covered by `Iteration1DoneColumnTest`; testcase passed | Pass |
| UT-05-CB | `AlbumController.search()` whitespace-only query | Executed successfully; zero-count empty result returned | Covered by `Iteration1DoneColumnTest`; testcase passed | Pass |
| UT-06-CB | `ProfileController.getProfile()` public profile payload | Executed successfully; public fields and rating count returned as expected | Covered by `Iteration1DoneColumnTest`; testcase passed | Pass |
| UT-07-CB | `AlbumController.getOneAlbum()` songs payload inclusion | Executed successfully; album detail response contained `songs` collection | Covered by `Iteration2InProgressColumnTest`; testcase passed | Pass |
| UT-08-CB | `PlaylistController.createPlaylist()` default category behavior | Executed successfully; playlist returned `201 Created` with default `Custom` category | Covered by `Iteration2InProgressColumnTest`; testcase passed | Pass |
| UT-09-CB | `AlbumController.getAllSongs()` DB payload passthrough | Executed successfully; seeded song list returned unchanged | Covered by `Iteration2InProgressColumnTest`; testcase passed | Pass |
| UT-10-CB | `AdminController.setRole()` admin role update | Executed successfully; target user role updated and returned correctly | Covered by `Iteration2InProgressColumnTest`; testcase passed | Pass |
| UT-11-CB | Friends profile discovery backlog stub | Tracked as intentional red-phase backlog check; not part of shipped release | `Iteration3ToDoColumnRedDemo.java` placeholder for EL-15 | Deferred / Out of Release Scope |
| UT-12-CB | Listen count / total duration backlog stub | Tracked as intentional red-phase backlog check; not part of shipped release | `Iteration3ToDoColumnRedDemo.java` placeholder for EL-5 | Deferred / Out of Release Scope |
| UT-13-CB | Profile sorting by genre backlog stub | Tracked as intentional red-phase backlog check; not part of shipped release | `Iteration3ToDoColumnRedDemo.java` placeholder for EL-16 | Deferred / Out of Release Scope |
| UT-14-CB | Comments backlog stub | Tracked as intentional red-phase backlog check; not part of shipped release | `Iteration3ToDoColumnRedDemo.java` placeholder for EL-20 | Deferred / Out of Release Scope |

---

## Bugs Documented and Resolved During Unit Testing

| Defect ID | Issue Observed | Resolution | Verification |
|---|---|---|---|
| DEF-UT-01 | Mock data built with `Map.of(...)` caused `UnsupportedOperationException` when `AlbumController.getOneAlbum()` mutated the returned album map | Test setup was changed to use mutable `HashMap`-backed rows for controller paths that append fields like `songs` | Re-run of UT-07-CB passed |
| DEF-UT-02 | Mock security principal setup initially caused `NullPointerException` in controller tests using authenticated endpoints | Tests were updated to build explicit `UserDetails` principals before invoking secured controller methods | Re-run of UT-08-CB and UT-10-CB passed |
| DEF-UT-03 | Validation paths needed explicit regression coverage for malformed JWTs, short passwords, and out-of-range ratings | Dedicated assertions were kept in the final suite to lock down those edge cases | UT-02-CB, UT-03-CB, and UT-04-CB all passed |

---

## Traceability Summary

- `EL-22` Create an account / signup-login -> UT-03-CB
- `EL-14` Star ratings -> UT-04-CB
- `EL-11` Search bar -> UT-05-CB
- `EL-24` View User Profile Page -> UT-06-CB
- `EL-17` Record shelf UI design -> UT-07-CB
- `EL-13` Playlists -> UT-08-CB
- `EL-2` Database and data sourcing -> UT-09-CB
- `EL-3` User Authentication and Roles -> UT-10-CB
- Backlog-only TDD placeholders -> UT-11-CB to UT-14-CB

---

## Final Assessment

- All **release-scope** unit tests were run.
- All **10 planned release-scope unit tests passed**.
- Known test-discovered issues were documented and resolved before final sign-off.
- The four Iteration 3 red tests remain intentionally deferred backlog specifications and do not block the final shipped release.
