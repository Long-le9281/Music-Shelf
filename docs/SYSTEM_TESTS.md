# System Tests

This document defines end-to-end system tests for the current shipped workflows in **Elgooners Record Shelf**.

The scenarios are based on the features and workflows described in `README.md`, `QUICK_START.md`, and `PRESENTATION.md`:
- authentication (signup/login)
- album browsing and record shelf interaction
- search across albums and songs
- album ratings
- public profile viewing
- playlist CRUD and playlist item management
- admin promotion and admin user management

## Scope

These tests validate full user-facing workflows across the React frontend, Spring Boot backend, and seeded catalog/user data.

### In Scope
- Sign up and log in
- Browse shelf and open album details
- Search albums and songs
- Rate albums
- View account and public profile pages
- Create, update, populate, and delete playlists
- Admin promotion and user management

### Out of Scope for Current Release
The following items are intentionally excluded because `PRESENTATION.md` identifies them as Iteration 3 / not-yet-implemented work:
- friend/profile discovery beyond current lookup flow
- profile sorting by genre
- comments
- song listen counts / total listen time


## Global Test Setup

Before running the system tests:
1. Start the backend at `http://localhost:8080`.
2. Start the frontend at `http://localhost:3000`.
3. Ensure seeded catalog data exists in `database/`.
4. Ensure test users are available:
   - `STANDARD_USER_A` — regular account with permission to log in and create playlists
   - `STANDARD_USER_B` — regular account with at least one saved rating for profile verification
   - `ADMIN_USER` — admin-capable account for admin workflows
5. If a test creates temporary data, clean it up at the end of the test or reseed the local data before the next run.

## System Test Matrix

| Test ID  | Scenario | Test Approach   | Preconditions | Steps | Expected Outcome | Assigned Team Member |
|----------|---|-----------------|---|---|---|---|
| ST-01-OB | New user sign-up and automatic session start | Opaque Box      | Frontend/backend running; chosen username does not already exist | Open `/signup`; enter a unique username and password with 6+ characters; submit; allow redirect to the shelf; open `/account` | Account is created, the user is logged in immediately, the shelf loads, and the account page shows the new user identity | Darius Kallistas |
| ST-02-OB | Existing user login and protected page access | Opaque Box      | `STANDARD_USER_A` exists and is logged out | Open `/login`; enter valid credentials; submit; open `/playlists`; open `/account` | Login succeeds, protected pages load without redirect loops, and user-specific data is shown | Brandon Dias |
| ST-03-OB | Browse the shelf and inspect album details | Opaque Box      | Seeded albums and songs exist | Open `/`; browse/select an album in the shelf; verify title, artist, year/genre tags, and track list; open lyrics for one track | Shelf view loads catalog data and the selected album shows full detail with its songs | Thanh Long Le |
| ST-04-OB | Search albums and songs, then jump into the shelf | Opaque Box      | Seeded albums and songs exist | Open `/search`; enter a query matching known catalog data; verify album results; switch to song mode; verify song results; click one result | Search returns matching results and clicking a result opens the shelf focused on that album/song | Daniyal |
| ST-05-CB | Rate an album and verify the rating is persisted | Clear Box       | `STANDARD_USER_A` is logged in; selected album exists | On `/`, open an album; choose a star rating; confirm save feedback; open `/account`; open `/profile/<STANDARD_USER_A>` | Rating is saved, account history updates, and the public profile shows the rated album and updated rating count | Epicfunguyddan |
| ST-06-OB | Create a custom playlist from the playlists page | Opaque Box      | `STANDARD_USER_A` is logged in | Open `/playlists`; create a playlist with name and description; save; open the new playlist | Playlist is created with category `Custom`, appears in the list, and opens with zero songs initially | Darius Kallistas |
| ST-07-TB | Add a single song to a playlist from the shelf | Translucent Box | `STANDARD_USER_A` is logged in and has at least one playlist | Open `/`; select an album; in the track list click `Add` on one song; choose a playlist in the modal; confirm; open that playlist detail page | The chosen song is added to the playlist and the playlist song count increases by exactly one | Brandon Dias |
| ST-08-TB | Add an album's songs to a playlist | Translucent Box | `STANDARD_USER_A` is logged in and has at least one playlist | Open `/`; select an album; click `+ Add Album Songs to Playlist`; choose a playlist; confirm; open playlist detail | All songs from the selected album are added and the playlist shows multiple tracks from that album | Thanh Long Le |
| ST-09-OB | Maintain playlist contents and delete the playlist | Opaque Box      | `STANDARD_USER_A` is logged in and has a populated playlist | Open `/playlists/<id>`; remove one song; verify updated count; return to `/playlists`; delete the playlist; confirm prompt | Removed song no longer appears, song count decreases, and deleted playlist disappears from the list | Daniyal |
| ST-10-OB | Look up another user and open their public profile | Opaque Box      | `STANDARD_USER_A` and `STANDARD_USER_B` exist; `STANDARD_USER_A` is logged in | Open `/account`; search for `STANDARD_USER_B` in Lookup Users; verify result row; navigate to `/profile/<STANDARD_USER_B>` | Lookup returns the matching user and the public profile loads their display name, admin badge if applicable, bio, and rated albums | Epicfunguyddan |
| ST-11-TB | Promote a standard user to admin from the account page | Translucent Box | Logged in as a non-admin account; valid promotion code is available | Open `/account`; enter the promotion code; submit; wait for refresh | Account now shows admin status and the Admin User Management panel becomes available | Darius Kallistas |
| ST-12-CB | Admin creates and manages users end to end | Clear Box       | Logged in as `ADMIN_USER` or a newly promoted admin | Open `/account`; create a user; verify it appears in the admin list; reset that user's password; disable or enable the user; optionally soft delete it | Admin actions succeed, confirmation messages appear, and the user list reflects the updated role/status/deletion state | Brandon Dias |

## Detailed Test Procedures

### ST-01-OB — New user sign-up and automatic session start
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Darius Kallistas

**Preconditions**
- Backend and frontend are running.
- The username chosen for the test is not already present in the user data.

**Steps**
1. Open `http://localhost:3000/signup`.
2. Enter a new username.
3. Enter a password with at least 6 characters.
4. Submit the form.
5. Confirm the app redirects to the shelf at `/`.
6. Open `/account` from the navigation.

**Expected Outcome**
- The account is created successfully.
- The user is automatically authenticated after sign-up.
- The shelf page loads normally.
- The account page displays the new username/display identity.

---

### ST-02-OB — Existing user login and protected page access
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Brandon Dias

**Preconditions**
- `STANDARD_USER_A` exists.
- The browser is in a logged-out state.

**Steps**
1. Open `http://localhost:3000/login`.
2. Sign in using `STANDARD_USER_A` credentials.
3. After redirect, open `/playlists`.
4. Open `/account`.

**Expected Outcome**
- Login succeeds without an error message.
- Both protected pages load successfully.
- The user is not redirected back to `/login` while authenticated.

---

### ST-03-OB — Browse the shelf and inspect album details
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Thanh Long Le

**Preconditions**
- Seeded catalog data exists.

**Steps**
1. Open `http://localhost:3000/`.
2. Browse to a known album in the shelf.
3. Select the album.
4. Verify the detail panel shows title, artist, tags, and description if available.
5. Verify the track list appears.
6. Click `Lyrics` on one track.

**Expected Outcome**
- The shelf loads album cards.
- Selecting an album updates the detail panel.
- The track list corresponds to the selected album.
- The lyrics panel changes to the selected song.

---

### ST-04-OB — Search albums and songs, then jump into the shelf
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Daniyal

**Preconditions**
- Seeded albums and songs are available.

**Steps**
1. Open `http://localhost:3000/search`.
2. Enter a query that should match at least one album and one song.
3. Verify album results in album mode.
4. Switch to song mode.
5. Verify song results.
6. Click one result.

**Expected Outcome**
- Matching results are shown for the active mode.
- Switching modes updates the result list correctly.
- Clicking a result opens `/` and focuses the related album/song in the shelf.

---

### ST-05-CB — Rate an album and verify persistence
**Test Approach:** Clear Box — tester knows that a rating saved on the shelf should be persisted to the backend and must appear in both the account history and the public profile page.  
**Assigned Team Member:** Epicfunguyddan

**Preconditions**
- `STANDARD_USER_A` is logged in.
- The selected album exists and is visible on the shelf.

**Steps**
1. Open the shelf.
2. Select an album.
3. Click a star value in `Your Rating`.
4. Wait for the `✓ Saved!` confirmation.
5. Open `/account` and review recent history.
6. Open `http://localhost:3000/profile/<STANDARD_USER_A>`.

**Expected Outcome**
- The rating is saved successfully.
- The account history shows the newly rated album.
- The public profile reflects the rating in its rated albums list and the rating count has increased.

---

### ST-06-OB — Create a custom playlist
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Darius Kallistas

**Preconditions**
- `STANDARD_USER_A` is logged in.

**Steps**
1. Open `/playlists`.
2. Click `+ Create Playlist`.
3. Enter a playlist name and description.
4. Save the playlist.
5. Open the new playlist from the list.

**Expected Outcome**
- The playlist appears immediately in the playlists list.
- The category defaults to `Custom` unless another category is selected.
- The new playlist opens and initially contains zero songs.

---

### ST-07-TB — Add a single song to a playlist from the shelf
**Test Approach:** Translucent Box — tester knows that adding a song calls a playlist-songs endpoint and that the playlist song count should increase by exactly one.  
**Assigned Team Member:** Brandon Dias

**Preconditions**
- `STANDARD_USER_A` is logged in.
- At least one playlist already exists.

**Steps**
1. Open `/`.
2. Select an album with songs.
3. In the track list, click `Add` for one song.
4. In the modal, choose the target playlist.
5. Confirm the add action.
6. Open `/playlists/<targetPlaylistId>`.

**Expected Outcome**
- The modal lists the user's playlists.
- The add request succeeds.
- The chosen song appears in the playlist detail page.
- Playlist song count increases by exactly one.

---

### ST-08-TB — Add an album's songs to a playlist
**Test Approach:** Translucent Box — tester knows that the "Add Album Songs" action sends all tracks from the album to the playlist endpoint, so the expected increase is the full album track count.  
**Assigned Team Member:** Thanh Long Le

**Preconditions**
- `STANDARD_USER_A` is logged in.
- At least one playlist already exists.

**Steps**
1. Open `/`.
2. Select a known album with multiple tracks.
3. Click `+ Add Album Songs to Playlist`.
4. Choose a target playlist in the modal.
5. Confirm the add action.
6. Open the playlist detail page.

**Expected Outcome**
- The album add action succeeds.
- Multiple songs from the selected album are inserted into the playlist.
- The playlist detail page reflects the increased song count equal to the album's track count.

---

### ST-09-OB — Maintain playlist contents and delete the playlist
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Daniyal

**Preconditions**
- `STANDARD_USER_A` is logged in.
- A playlist exists with at least one song.

**Steps**
1. Open the populated playlist detail page.
2. Remove one song.
3. Confirm the song disappears.
4. Return to `/playlists`.
5. Delete the playlist and accept the confirmation dialog.

**Expected Outcome**
- Removed songs no longer appear in the playlist detail page.
- Playlist count updates correctly.
- The deleted playlist is removed from the playlists list.

---

### ST-10-OB — Look up another user and open their public profile
**Test Approach:** Opaque Box — driven entirely through the UI with no knowledge of backend internals.  
**Assigned Team Member:** Epicfunguyddan

**Preconditions**
- `STANDARD_USER_A` and `STANDARD_USER_B` exist.
- `STANDARD_USER_A` is logged in.
- `STANDARD_USER_B` has profile data and at least one saved rating.

**Steps**
1. Open `/account`.
2. Search for `STANDARD_USER_B` in the lookup field.
3. Verify the result row appears.
4. Navigate to `/profile/<STANDARD_USER_B>`.

**Expected Outcome**
- Lookup returns the expected user.
- The profile page loads without a 404.
- The page shows the user's public identity and rated albums.

---

### ST-11-TB — Promote a standard user to admin
**Test Approach:** Translucent Box — tester knows the promotion code exists and that the backend will update the `isAdmin` flag, making the Admin User Management panel appear.  
**Assigned Team Member:** Darius Kallistas

**Preconditions**
- A non-admin account is logged in.
- The tester has the valid promotion code.

**Steps**
1. Open `/account`.
2. In `Promotion + History`, enter the admin promotion code.
3. Click `Promote`.
4. Wait for the page state to refresh.

**Expected Outcome**
- A success message is shown.
- The current account now indicates admin status.
- The `Admin User Management` panel becomes visible.

---

### ST-12-CB — Admin creates and manages users end to end
**Test Approach:** Clear Box — tester has full knowledge of the admin system: role flags (`isAdmin`), account status (`isActive`), soft delete (`deletedAt`), and the password reset response contract.  
**Assigned Team Member:** Brandon Dias

**Preconditions**
- Logged in as `ADMIN_USER` or a user promoted in ST-11-TB.

**Steps**
1. Open `/account`.
2. In `Create User`, enter username, password, display name, avatar color, and optional bio.
3. Create the user and verify it appears in the admin user list.
4. Use `Reset PW` for that user and record the temporary password from the response.
5. Use `Disable` or `Enable` and verify the `isActive` status text changes in the list.
6. Optionally soft delete the user and confirm the `deletedAt` state is reflected in the list.

**Expected Outcome**
- Admin-only controls are visible and functional.
- User creation succeeds and the new row appears in the admin list.
- Password reset returns a temporary password in the response message.
- Status changes (`isActive`) are immediately reflected in the list.
- If soft deleted, the user row shows the deleted state and can no longer authenticate.

---

## Coverage Summary

These system tests cover every currently documented shipped workflow in the repo:
- `EL-22` account creation / signup-login
- `EL-11` search
- `EL-14` album ratings
- `EL-24` profile page
- `EL-13` playlists
- `EL-17` shelf album detail flow
- `EL-3` admin role/user management
