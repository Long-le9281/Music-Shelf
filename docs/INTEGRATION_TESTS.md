# Integration Tests

This document defines integration tests for Elgooners Record Shelf.

Integration tests in this phase focus on interactions between:
- Spring Boot controllers
- security/authentication (JWT + role checks)
- persistence layer (SQLite via Database)
- cross-endpoint data consistency

## Scope

### In Scope
- Auth to protected endpoint chains
- Controller to DB persistence/readback consistency
- Role-based authorization interactions
- Multi-step flows spanning multiple endpoints

### Out of Scope
- Pure UI behavior without backend interaction (covered by system tests)
- Isolated method-level validation (covered by unit tests)
- Iteration 3 not-yet-implemented stories

## Global Setup

Before running integration tests:
1. Start backend at http://localhost:8080.
2. Frontend is optional for API-level integration checks.
3. Ensure seeded catalog data exists in database/.
4. Ensure users are available:
    - demo (standard user)
    - nostalgia (standard user)
    - admin (admin user)

## Integration Test Matrix

| Test ID | Components Involved | Preconditions | Steps | Expected Result | Testing Approach | Assigned Team Member |
|---|---|---|---|---|---|---|
| IT-01-TB | AuthController + JwtFilter + ProfileController | demo exists | Login; call /api/auth/me; call /api/profile/me | Both authenticated calls succeed and return consistent username | Automated (JUnit + Spring Boot Test) | Brandon Dias |
| IT-02-CB | AuthController.promote + JwtFilter + AuthController.me | Logged in as non-admin | POST /api/auth/promote with invalid code; reuse same token for /api/auth/me | First call returns 403 and second call still returns 200 with valid user payload | Automated (JUnit + Spring Boot Test) | Daniyal |
| IT-03-TB | RatingController + ratings table + profile/account read paths | Logged in user, known album id | Submit album rating; call /api/ratings/{albumId}; call /api/profile/demo and /api/profile/me | Rating write is visible across read models with updated count/history | Automated (JUnit + Spring Boot Test) | Daniyal |
| IT-04-TB | PlaylistController + playlist_songs table | Logged in user, valid playlist and song | Create playlist; add one song; fetch /api/playlists/{id} | Added song appears and songCount increases by exactly one | Automated (JUnit + Spring Boot Test) | Thanh Long Le |
| IT-05-TB | PlaylistController + album songs query + playlist_songs | Logged in user, valid playlist and album with songs | Add album songs to playlist; fetch /api/playlists/{id} | addedSongs in response matches observed playlist count increase | Automated (JUnit + Spring Boot Test) | Darius Kallistas |
| IT-06-CB | AuthController.promote + AdminController + role persistence | Non-admin user with valid promo code | Promote user; call /api/admin/users; create/update target user role/status | Admin endpoints become accessible and persisted changes are returned by admin API | Automated (JUnit + Spring Boot Test) | Brandon Dias |

## Class Plan (How Each Test Will Be Written)

### Proposed Test Classes
- IntegrationAuthFlowTest
   - Covers IT-01-TB and IT-02-CB
   - Shared helper: loginAndGetToken(username, password)
   - Shared helper: authHeaders(token)
- IntegrationRatingFlowTest
   - Covers IT-03-TB
   - Shared helper: findAlbumIdWithSongs()
- IntegrationPlaylistFlowTest
   - Covers IT-04-TB and IT-05-TB
   - Shared helpers: createPlaylist(token), getPlaylistDetail(token, playlistId)
- IntegrationAdminFlowTest
   - Covers IT-06-CB
   - Shared helper: createTempUsername(prefix)

### Base Setup Pattern (all classes)
- Use SpringBootTest with RANDOM_PORT.
- Use TestRestTemplate for real HTTP calls.
- Use one shared test profile/properties file for integration runs.
- Use dynamic DB path in target/ to avoid polluting runtime DB.
- Keep each test independent (create its own playlist/temp user data).

## Detailed Test Procedures

### IT-01-TB - Login token grants access to account/profile endpoints
Class: IntegrationAuthFlowTest

Preconditions
- demo user exists in seeded data.

Steps
1. POST /api/auth/login with demo/demo123.
2. Save token from response.
3. GET /api/auth/me with Authorization: Bearer token.
4. GET /api/profile/me with Authorization: Bearer token.

Expected Result
- Login returns 200 with non-null token.
- Both /me endpoints return 200.
- username value is consistent across both responses.

---

### IT-02-CB - Invalid promotion code returns forbidden without session loss
Class: IntegrationAuthFlowTest

Preconditions
- Logged in as non-admin user (demo or nostalgia).

Steps
1. POST /api/auth/promote with wrong code.
2. Reuse same token and call GET /api/auth/me.

Expected Result
- Promote call returns 403.
- Follow-up /api/auth/me still returns 200.
- Session/token remains valid.

---

### IT-03-TB - Album rating persists and propagates to read models
Class: IntegrationRatingFlowTest

Preconditions
- Logged in user and valid album id.

Steps
1. POST /api/ratings/{albumId} with stars=4.
2. GET /api/ratings/{albumId}.
3. GET /api/profile/demo.
4. GET /api/profile/me.

Expected Result
- Rating write returns 200.
- /api/ratings/{albumId} returns stars=4.
- Profile responses include rating in read model data.

---

### IT-04-TB - Add single song to playlist updates playlist detail
Class: IntegrationPlaylistFlowTest

Preconditions
- Logged in user and valid song id.

Steps
1. POST /api/playlists to create playlist.
2. POST /api/playlists/{playlistId}/songs/{songId}.
3. GET /api/playlists/{playlistId}.

Expected Result
- Song add returns 200.
- Playlist detail includes song id.
- songCount increases by one.

---

### IT-05-TB - Add album songs to playlist updates count by track total
Class: IntegrationPlaylistFlowTest

Preconditions
- Logged in user, valid playlist id, valid album id with songs.

Steps
1. GET playlist detail and record initial songCount.
2. POST /api/playlists/{playlistId}/albums/{albumId}.
3. GET playlist detail again.

Expected Result
- addAlbum response returns 200 with addedSongs > 0.
- New songCount equals initialCount + addedSongs.

---

### IT-06-CB - Admin role transition unlocks admin endpoints
Class: IntegrationAdminFlowTest

Preconditions
- Non-admin user and valid promotion code.

Steps
1. Login as non-admin.
2. POST /api/auth/promote with valid code.
3. GET /api/admin/users.
4. POST /api/admin/users (create temporary user).
5. PUT /api/admin/users/{username}/status.
6. PUT /api/admin/users/{username}/role.

Expected Result
- Promotion succeeds.
- /api/admin/users returns 200.
- Admin create/update endpoints return success codes and persisted fields.

## General Implementation Guidelines

- Keep one assertion group for status codes and one for payload/state checks.
- Avoid hardcoding catalog ids; discover valid ids from /api/albums or /api/songs first.
- Use unique names for temporary entities (timestamp suffix).
- Clean up temporary users/playlists where possible to keep reruns stable.
- Keep integration tests focused on component interaction, not UI rendering behavior.

## Notes on Test Views

- CB tests include internals-aware assertions about role/session/state transitions.
- TB tests validate cross-component behavior with partial implementation awareness.

## Traceability

- EL-3 User Authentication and Roles -> IT-01, IT-02, IT-06
- EL-14 Star Ratings -> IT-03
- EL-13 Playlists -> IT-04, IT-05
- EL-24 Profile Page -> IT-01, IT-03
