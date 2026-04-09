# Unit Tests

This document describes all unit tests currently in `backend/src/test/java/com/elgooners/app/`.

All tests listed here are **Clear Box (CB)** — each test directly instantiates a controller, mocks its `Database` dependency using Mockito, calls a specific method with known inputs, and asserts on the exact response. This requires full knowledge of the class internals, field injection points, and business logic rules.


---

## Unit Test Matrix

| Test ID | Method / Class | Input(s) | Expected Output(s) | Testing Approach | Assigned Team Member |
|---|---|---|---|---|---|
| UT-01-CB | `JwtHelper.createToken()` + `getUsernameFromToken()` | `"demo-user"` | Decoded username equals `"demo-user"` | Automated (JUnit) | Brandon Dias |
| UT-02-CB | `JwtHelper.getUsernameFromToken()` | `"not-a-real-jwt"` | Returns `null` | Automated (JUnit) | Brandon Dias |
| UT-03-CB | `AuthController.signup()` | `{username: "newuser", password: "123"}` | HTTP `400 Bad Request`, non-null body | Automated (JUnit) | Darius Kallistas |
| UT-04-CB | `RatingController.rateAlbum()` | `albumId=1`, `{stars: 6}`, `principal=null` | HTTP `400 Bad Request` | Automated (JUnit) | Danyal |
| UT-05-CB | `AlbumController.search()` | `"   "` (whitespace only) | `meta.albumCount == 0`, `meta.songCount == 0` | Automated (JUnit) | Daniyal |
| UT-06-CB | `ProfileController.getProfile()` | `username="demo"`, mocked user row + 2 ratings | HTTP `200 OK`, `username="demo"`, `ratingCount=2` | Automated (JUnit) | Danyal |
| UT-07-CB | `AlbumController.getOneAlbum()` | `albumId=4`, mocked album + 2 songs | HTTP `200 OK`, body contains `"songs"` key | Automated (JUnit) | Thanh Long Le |
| UT-08-CB | `PlaylistController.createPlaylist()` | `{name: "Road Trip", description: "Driving mix"}`, `principal="demo"` | HTTP `201 Created`, `id=501`, `category="Custom"` | Automated (JUnit) | Darius Kallistas |
| UT-09-CB | `AlbumController.getAllSongs()` | Mocked DB returning 1 song `{id:1, title:"Seed Song"}` | List size `1`, `title="Seed Song"` | Automated (JUnit) | Thanh Long Le |
| UT-10-CB | `AdminController.setRole()` | `username="targetUser"`, `{isAdmin: true}`, admin `principal` | HTTP `200 OK`, `username="targetUser"`, `isAdmin=true` | Automated (JUnit) | Brandon Dias |
| UT-11-CB | `fail()` — friends profile discovery stub | N/A (TDD red phase) | Test fails with `EL-15 TODO` message | Automated (JUnit) | Daniyal |
| UT-12-CB | `fail()` — song listen count/duration stub | N/A (TDD red phase) | Test fails with `EL-5 TODO` message | Automated (JUnit) | Danyal |
| UT-13-CB | `fail()` — sort profiles by genre stub | N/A (TDD red phase) | Test fails with `EL-16 TODO` message | Automated (JUnit) | Daniyal |
| UT-14-CB | `fail()` — comments feature stub | N/A (TDD red phase) | Test fails with `EL-20 TODO` message | Automated (JUnit) | Thanh Long Le |

---

## Detailed Test Descriptions

### UT-01-CB — JWT token encodes and decodes username correctly
**Class:** `JwtHelperTest`  
**File:** `JwtHelperTest.java`  
**Test Approach:** Clear Box — directly instantiates `JwtHelper` and calls both token creation and parsing methods.  
**Assigned Team Member:** Brandon Dias

**Method Under Test:** `JwtHelper.createToken()` + `JwtHelper.getUsernameFromToken()`

**Inputs**
- Username string: `"demo-user"`

**Expected Outputs**
- `getUsernameFromToken(token)` returns `"demo-user"`

**Notes**
- Verifies the full round-trip: generate → decode → match.

---

### UT-02-CB — Invalid JWT returns null instead of throwing
**Class:** `JwtHelperTest`  
**File:** `JwtHelperTest.java`  
**Test Approach:** Clear Box — directly calls `getUsernameFromToken` with a malformed token string.  
**Assigned Team Member:** Brandon Dias

**Method Under Test:** `JwtHelper.getUsernameFromToken()`

**Inputs**
- Token string: `"not-a-real-jwt"`

**Expected Outputs**
- Return value is `null`

**Notes**
- Ensures malformed tokens fail gracefully without throwing an uncaught exception.

---

### UT-03-CB — Signup rejects passwords shorter than 6 characters
**Class:** `Iteration1DoneColumnTest`  
**File:** `Iteration1DoneColumnTest.java`  
**Story:** EL-22 — Create an account / signup-login  
**Test Approach:** Clear Box — directly instantiates `AuthController`, injects a mocked `Database`, and calls `signup()`.  
**Assigned Team Member:** Darius Kallistas

**Method Under Test:** `AuthController.signup()`

**Inputs**
- Request body: `{username: "newuser", password: "123"}`

**Expected Outputs**
- HTTP `400 Bad Request`
- Non-null response body containing an error description

**Notes**
- Password `"123"` has 3 characters, which is below the 6-character minimum enforced by the controller.

---

### UT-04-CB — Rating rejects star values outside the 1–5 range
**Class:** `Iteration1DoneColumnTest`  
**File:** `Iteration1DoneColumnTest.java`  
**Story:** EL-14 — Star ratings  
**Test Approach:** Clear Box — directly instantiates `RatingController`, injects a mocked `Database`, and calls `rateAlbum()`.  
**Assigned Team Member:** Danyal

**Method Under Test:** `RatingController.rateAlbum()`

**Inputs**
- `albumId`: `1L`
- Request body: `{stars: 6}`
- `principal`: `null`

**Expected Outputs**
- HTTP `400 Bad Request`

**Notes**
- `stars: 6` exceeds the valid upper bound of 5. The controller must reject it before any DB write occurs.

---

### UT-05-CB — Whitespace-only search query returns zero results
**Class:** `Iteration1DoneColumnTest`  
**File:** `Iteration1DoneColumnTest.java`  
**Story:** EL-11 — Search bar  
**Test Approach:** Clear Box — directly instantiates `AlbumController` (no DB mock needed) and calls `search()`.  
**Assigned Team Member:** Daniyal

**Method Under Test:** `AlbumController.search()`

**Inputs**
- Query string: `"   "` (three spaces)

**Expected Outputs**
- `meta.albumCount == 0`
- `meta.songCount == 0`
- `albums` and `songs` lists are empty

**Notes**
- A blank/whitespace query must short-circuit before hitting the DB and return an empty structured response.

---

### UT-06-CB — Profile endpoint returns public fields and correct rating count
**Class:** `Iteration1DoneColumnTest`  
**File:** `Iteration1DoneColumnTest.java`  
**Story:** EL-24 — View User Profile Page  
**Test Approach:** Clear Box — directly instantiates `ProfileController`, injects a mocked `Database` returning a known user row and rating list.  
**Assigned Team Member:** Danyal

**Method Under Test:** `ProfileController.getProfile()`

**Inputs**
- `username`: `"demo"`
- Mocked `db.findUser("demo")` returns user row `{id:7, username:"demo", displayName:"Demo User", ...}`
- Mocked `db.getRatingsByUser(7L)` returns list of 2 ratings

**Expected Outputs**
- HTTP `200 OK`
- `body.username == "demo"`
- `body.ratingCount == 2`

**Notes**
- Password and other sensitive fields must not appear in the response body.

---

### UT-07-CB — Album detail response includes a songs list
**Class:** `Iteration2InProgressColumnTest`  
**File:** `Iteration2InProgressColumnTest.java`  
**Story:** EL-17 — Record shelf UI design  
**Test Approach:** Clear Box — directly instantiates `AlbumController`, injects a mocked `Database` returning a known album and two songs.  
**Assigned Team Member:** Thanh Long Le

**Method Under Test:** `AlbumController.getOneAlbum()`

**Inputs**
- `albumId`: `4L`
- Mocked `db.getAlbumById(4L)` returns `{id:4, title:"Demo Album"}`
- Mocked `db.getSongsForAlbum(4L)` returns `[{id:40, title:"Track One"}, {id:41, title:"Track Two"}]`

**Expected Outputs**
- HTTP `200 OK`
- Response body contains a non-null `"songs"` key

**Notes**
- The frontend relies on the `songs` field being present in the album detail payload to render the track list.

---

### UT-08-CB — Create playlist returns 201 with correct default category
**Class:** `Iteration2InProgressColumnTest`  
**File:** `Iteration2InProgressColumnTest.java`  
**Story:** EL-13 — Create custom albums or playlists  
**Test Approach:** Clear Box — directly instantiates `PlaylistController`, injects a mocked `Database`, builds a `UserDetails` principal, and calls `createPlaylist()`.  
**Assigned Team Member:** Darius Kallistas

**Method Under Test:** `PlaylistController.createPlaylist()`

**Inputs**
- Request body: `{name: "Road Trip", description: "Driving mix"}`
- Principal: `User.withUsername("demo").password("x").roles("USER").build()`
- Mocked `db.findUser("demo")` returns `{id:77, username:"demo"}`
- Mocked `db.createPlaylist(77L, "Road Trip", "Driving mix", "Custom")` returns `501L`

**Expected Outputs**
- HTTP `201 Created`
- `body.id == 501`
- `body.category == "Custom"`

**Notes**
- No explicit `category` was provided in the request body; the controller must default to `"Custom"`.

---

### UT-09-CB — Songs endpoint returns the full list from the database
**Class:** `Iteration2InProgressColumnTest`  
**File:** `Iteration2InProgressColumnTest.java`  
**Story:** EL-2 — Database and data sourcing  
**Test Approach:** Clear Box — directly instantiates `AlbumController`, injects a mocked `Database` returning a single seeded song.  
**Assigned Team Member:** Thanh Long Le

**Method Under Test:** `AlbumController.getAllSongs()`

**Inputs**
- Mocked `db.getAllSongs()` returns `[{id:1, title:"Seed Song"}]`

**Expected Outputs**
- List size `1`
- `songs.get(0).title == "Seed Song"`

**Notes**
- Verifies that the controller passes the DB result directly to the caller without filtering or transforming it.

---

### UT-10-CB — Admin can promote a target user to admin role
**Class:** `Iteration2InProgressColumnTest`  
**File:** `Iteration2InProgressColumnTest.java`  
**Story:** EL-3 — User Authentication and Roles  
**Test Approach:** Clear Box — directly instantiates `AdminController`, injects a mocked `Database`, builds an admin `UserDetails` principal, and calls `setRole()`.  
**Assigned Team Member:** Brandon Dias

**Method Under Test:** `AdminController.setRole()`

**Inputs**
- `username`: `"targetUser"`
- Request body: `{isAdmin: true}`
- Admin principal: `User.withUsername("adminUser").password("x").roles("ADMIN").build()`
- Mocked `db.findUser("targetUser")` returns `{id:9, username:"targetUser", isAdmin:false}`
- Mocked `db.setUserAdminByUsername("targetUser", true)` returns `true`

**Expected Outputs**
- HTTP `200 OK`
- `body.username == "targetUser"`
- `body.isAdmin == true`

---

### UT-11-CB — Friends profile discovery 
**Class:** `Iteration3ToDoColumnRedDemo`  
**File:** `Iteration3ToDoColumnRedDemo.java`  
**Story:** EL-15 — Finding other/friends profiles  
**Test Approach:** Clear Box  
**Assigned Team Member:** Daniyal


**Inputs**
- None



**Notes**
- This is a TDD red-phase placeholder. The test will turn green once the friends discovery endpoint and UI flow are implemented.

---

### UT-12-CB — Song listen count and total duration tracking 
**Class:** `Iteration3ToDoColumnRedDemo`  
**File:** `Iteration3ToDoColumnRedDemo.java`  
**Story:** EL-5 — Number/total time of song listens  
**Test Approach:** Clear Box
**Assigned Team Member:** Danyal

**Method Under Test:** N/A (not yet implemented)

**Inputs**
- None


**Notes**
- TDD red-phase placeholder. Requires a per-user listen tracking table and aggregation endpoint.

---

### UT-13-CB — Profile sorting by genre 
**Class:** `Iteration3ToDoColumnRedDemo`  
**File:** `Iteration3ToDoColumnRedDemo.java`  
**Story:** EL-16 — Sorting profiles by genre  
**Test Approach:** Clear Box  
**Assigned Team Member:** Daniyal

**Method Under Test:** N/A (not yet implemented)

**Inputs**
- None

**Expected Outputs**
- Test **fails** intentionally with message: `"EL-16 TODO: add profile genre indexing and sorting/filter endpoint."`

**Notes**
- TDD red-phase placeholder. Requires genre indexing on profiles and a sort/filter API endpoint.

---

### UT-14-CB — Comments feature (TDD red — not yet implemented)
**Class:** `Iteration3ToDoColumnRedDemo`  
**File:** `Iteration3ToDoColumnRedDemo.java`  
**Story:** EL-20 — Comments  
**Test Approach:** Clear Box — explicit `fail()` stub.  
**Assigned Team Member:** Thanh Long Le

**Method Under Test:** N/A (not yet implemented)

**Inputs**
- None

**Expected Outputs**
- Test **fails** intentionally with message: `"EL-20 TODO: implement comments persistence, moderation, and API contract."`

**Notes**
- TDD red-phase placeholder. Requires a comments table, moderation rules, and full API contract before implementation.

---

## Test File Index

| File | Tests Covered | Run Command | Expected Result |
|------|--------------|-------------|-----------------|
| `JwtHelperTest.java` | UT-01-CB, UT-02-CB | `mvn -Dtest=JwtHelperTest test` | 🟢 2 / 2 pass |
| `Iteration1DoneColumnTest.java` | UT-03-CB, UT-04-CB, UT-05-CB, UT-06-CB | `mvn -Dtest=Iteration1DoneColumnTest test` | 🟢 4 / 4 pass |
| `Iteration2InProgressColumnTest.java` | UT-07-CB, UT-08-CB, UT-09-CB, UT-10-CB | `mvn -Dtest=Iteration2InProgressColumnTest test` | 🟢 4 / 4 pass |
| `Iteration3ToDoColumnRedDemo.java` | UT-11-CB, UT-12-CB, UT-13-CB, UT-14-CB | `mvn -Dtest=Iteration3ToDoColumnRedDemo test` | 🟢 4 / 4 pass |

Run all tests at once:

```powershell
Set-Location .\backend
mvn test
```

