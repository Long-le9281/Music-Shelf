# System Test Final Report

This document records the final executed end-to-end system-test results for the current shipped workflows in **Elgooners Record Shelf**.

System testing covered the full user-visible flow across the React frontend, Spring Boot backend, and seeded catalog/user data.

---

## Scope

### Included in Final System Report
- Sign up and log in
- Browse shelf and inspect album details
- Search albums and songs
- Rate albums
- View account and public profile pages
- Create, update, populate, and delete playlists
- Admin promotion and user management

### Excluded from Final System Report
The following backlog items were intentionally excluded because they were not part of the shipped release scope:
- friend/profile discovery beyond the current lookup flow
- profile sorting by genre
- comments
- song listen counts / total listen time

---

## Execution Environment

Final system verification used the running application with local seeded data.

Baseline setup for the executed system scenarios:
1. Backend running at `http://localhost:8080`
2. Frontend running at `http://localhost:3000`
3. Seeded catalog data available in `database/`
4. Available test users:
   - `demo`
   - `nostalgia`
   - `admin`
5. Temporary users/playlists cleaned up or re-seeded between runs as needed

---

## Execution Summary

| Planned System Tests | Executed | Passed | Failed | Open Blocking Defects |
|---:|---:|---:|---:|---:|
| 12 | 12 | 12 | 0 | 0 |

**Final result:** all planned shipped-scope system scenarios were run and passed after final verification.

---

## Final System Test Matrix

| Test ID | Scenario | Actual Execution Result | Bugs Documented / Resolution | Final Status |
|---|---|---|---|---|
| ST-01-OB | New user sign-up and automatic session start | Executed successfully. New user creation redirected into an authenticated session and `/account` displayed the created identity. | No remaining defect after final verification. | Pass |
| ST-02-OB | Existing user login and protected page access | Executed successfully. Valid login granted access to protected pages without redirect loops. | No remaining defect after final verification. | Pass |
| ST-03-OB | Browse the shelf and inspect album details | Executed successfully. Shelf loaded seeded albums, album selection updated the detail panel, and the track/lyrics flow displayed correctly. | No remaining defect after final verification. | Pass |
| ST-04-OB | Search albums and songs, then jump into the shelf | Executed successfully. Matching album/song results appeared and selecting a result navigated back into the correct shelf context. | No remaining defect after final verification. | Pass |
| ST-05-CB | Rate an album and verify persistence | Executed successfully. Rating save feedback appeared and the new rating showed up in account/profile views. | Persistence/readback consistency was rechecked and closed during final verification. | Pass |
| ST-06-OB | Create a custom playlist from the playlists page | Executed successfully. Playlist creation returned a new `Custom` playlist that opened with zero songs. | No remaining defect after final verification. | Pass |
| ST-07-TB | Add a single song to a playlist from the shelf | Executed successfully. Song add flow completed and the target playlist count increased by one. | Playlist count/readback behavior was rechecked and closed during final verification. | Pass |
| ST-08-TB | Add an album's songs to a playlist | Executed successfully. Album add flow inserted multiple tracks and the playlist reflected the expected increase. | Playlist count/readback behavior was rechecked and closed during final verification. | Pass |
| ST-09-OB | Maintain playlist contents and delete the playlist | Executed successfully. Song removal updated the playlist state and deleting the playlist removed it from the list view. | No remaining defect after final verification. | Pass |
| ST-10-OB | Look up another user and open their public profile | Executed successfully. User lookup returned the expected profile and the public profile view loaded correctly. | No remaining defect after final verification. | Pass |
| ST-11-TB | Promote a standard user to admin from the account page | Executed successfully. Promotion updated the account state and exposed the admin management UI. | Admin-state refresh behavior was rechecked and closed during final verification. | Pass |
| ST-12-CB | Admin creates and manages users end to end | Executed successfully. Admin create/reset/status-management actions completed and the UI reflected the resulting user state. | No remaining defect after final verification. | Pass |

---

## Bugs Documented and Resolved During System Testing

| Defect ID | Issue Observed | Resolution / Final Disposition | Verification |
|---|---|---|---|
| DEF-ST-01 | The browser can briefly show `connection refused` immediately after startup while the backend or frontend is still booting | The startup behavior was documented in `README.md`; users are instructed to wait `10-20` seconds and refresh | Startup guidance updated and retested during launch flow |
| DEF-ST-02 | Rating and playlist workflows required explicit end-to-end rechecks to ensure UI state matched persisted backend state | Final verification reopened account/profile/playlist detail pages after writes to confirm the displayed data matched persisted state | ST-05-CB, ST-07-TB, ST-08-TB, and ST-09-OB passed |
| DEF-ST-03 | Admin promotion had to refresh the visible account state before admin controls could be relied on for follow-up actions | Final verification included the post-promotion refresh/state-update step before exercising admin controls | ST-11-TB and ST-12-CB passed |

---

## Coverage Summary

These executed system tests cover every shipped workflow included in the final release scope:
- `EL-22` account creation / signup-login
- `EL-11` search
- `EL-14` album ratings
- `EL-24` profile page
- `EL-13` playlists
- `EL-17` shelf album detail flow
- `EL-3` admin role/user management

---

## Final Assessment

- All planned system tests were run.
- All **12 / 12** planned system tests passed.
- Bugs and workflow issues found during system testing were documented and resolved or operationally mitigated before final sign-off.
- No open shipped-scope system defects remained at final report sign-off.
