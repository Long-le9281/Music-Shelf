# Integration Tests

This document defines integration tests for Elgooners Record Shelf.

Integration tests in this phase focus on interactions between:
- Spring Boot controllers
- security/authentication (JWT + role checks)
- persistence layer (SQLite via `Database`)
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

1. Start backend at `http://localhost:8080`.
2. Ensure frontend is optional for API-driven integration checks.
3. Seed baseline data in `database/`.
4. Ensure users are available:
   - `demo` (standard user)
   - `nostalgia` (standard user)
   - `admin` (admin user)

## Integration Test Matrix

| Test ID | Scenario | Components Exercised | Preconditions | Steps | Expected Outcome | Assigned Team Member |
|---|---|---|---|---|---|---|
| IT-01-TB | Login token grants access to account/profile endpoints | `AuthController` + JWT filter + `ProfileController` | `demo` exists | Login with valid credentials, call `/api/auth/me`, then `/api/profile/me` | Authenticated calls succeed with consistent user identity data | Brandon Dias |
| IT-02-CB | Invalid promotion code returns forbidden without session loss | `AuthController.promote` + JWT filter + `AuthController.me` | Logged in as non-admin (`demo` or `nostalgia`) | POST `/api/auth/promote` with invalid code, then call `/api/auth/me` using the same token | First call returns `403`; second call still returns authenticated user details | Daniyal |
| IT-03-TB | Album rating persists and propagates to read models | `RatingController` + ratings table + profile/account queries | Logged in user and known album ID | Submit rating, fetch account history and profile ratings | Rating appears consistently in both views with updated timestamp/count | Epicfunguyddan |
| IT-04-TB | Add single song to playlist updates playlist detail | `PlaylistController` + `playlist_songs` table | Logged in user with existing playlist and song | Add song via playlist endpoint, fetch playlist detail | Playlist contains song and song count increases by one | Thanh Long Le |
| IT-05-TB | Add full album songs to playlist updates count by track total | `PlaylistController` + songs query + `playlist_songs` table | Logged in user, existing playlist, known album with songs | Add album songs to playlist endpoint, fetch playlist detail | Playlist count increases by album track count (dedupe rules respected) | Darius Kallistas |
| IT-06-CB | Admin role transition unlocks admin management endpoints | Promotion path + role persistence + admin endpoints | Non-admin account and valid promo code | Promote account, call `/api/admin/users`, update another user role/status | Admin endpoints become accessible and role/status changes persist | Brandon Dias |

## Notes on Test Views

- `CB` tests use deeper API/data contract awareness and explicit assertions on returned fields and state transitions.
- `TB` tests assume partial internal knowledge (which endpoints/tables should change) but validate through externally visible contracts.

## Traceability

- EL-3 User Authentication and Roles -> IT-01, IT-02, IT-06
- EL-14 Star Ratings -> IT-03
- EL-13 Playlists -> IT-04, IT-05
- EL-24 Profile Page -> IT-01, IT-03
