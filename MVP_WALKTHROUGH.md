# MVP Demonstration - Written Walkthrough

## Overview
This document provides a step-by-step walkthrough of the Music-Shelf MVP, demonstrating all core features and functionality.

**Demo Credentials:** Username: `demo` | Password: `demo123`

---

## 1. Login Screen

**What You'll See:**
A simple login window with fields for username and password, plus "Login" and "Sign Up" buttons.

**How to Use:**
- Enter username: `demo`
- Enter password: `demo123`
- Click "Login" button

**What Happens:**
The login window closes and the main application window opens with the full music catalog.

*[Screenshot of login screen goes here]*

---

## 2. Main Application Window - Browse Catalog

**What You'll See:**
The main Music-Shelf window displaying:
- A search bar with a dropdown filter (Albums / Songs / Both)
- A list of all available albums and songs
- "Rate" and "View Profile" buttons at the bottom

**Default View:**
By default, both albums and songs are displayed:
- Abbey Road - The Beatles
- Thriller - Michael Jackson
- Back in Black - AC/DC
- Random Access Memories - Daft Punk
- Plus 5 individual songs from these albums

**How to Use:**
Simply scroll through the list to browse all available music in the catalog. The dropdown at the top lets you filter by:
- "Albums" - Shows only albums
- "Songs" - Shows only songs
- "Both" - Shows albums and songs together (default)

*[Screenshot of main window with full catalog goes here]*

---

## 3. Search Functionality

**What You'll See:**
A search field at the top with a search button and filter dropdown.

**How to Use:**
1. Type a search term (e.g., "Beatles", "Black", "Daft Punk")
2. Click the "Search" button
3. Results appear in the list below

**Search Capabilities:**
- Search by **album title** (e.g., "Abbey Road")
- Search by **artist name** (e.g., "The Beatles", "Michael Jackson")
- Search is **case-insensitive**
- Works across both albums and songs simultaneously

**Example Searches:**
- "Beatles" → finds Abbey Road album and "Come Together" song
- "Black" → finds "Back in Black" album and song
- "Daft" → finds "Random Access Memories" album and "Get Lucky" song

**Clearing Search:**
Leave the search field empty and the full catalog reappears.

*[Screenshot of search results goes here]*

---

## 4. Rating Albums and Songs

**What You'll See:**
After selecting an item and clicking "Rate", a dialog box appears asking for a rating.

**How to Use:**
1. Click on any album or song in the list to select it
2. Click the "Rate" button
3. A popup window appears: "Rating (1-5)"
4. Enter a number between 1 and 5
5. Click OK

**Rating Scale:**
- 1 = Didn't like it
- 2 = Below average
- 3 = Average
- 4 = Good
- 5 = Excellent

**Feedback:**
After rating, you'll see a confirmation message: "Rating saved!"

**Error Handling:**
- If you don't select an item first: "Select an item first"
- If you enter an invalid number: "Rating must be between 1 and 5"
- If you enter non-numeric input: "Invalid rating"

*[Screenshot of rating dialog goes here]*

---

## 5. User Profile - View Your Ratings

**What You'll See:**
Your personal profile page showing:
- Window title: "[Username] Profile"
- A list of all items you've rated with their ratings
- A "Back" button to return to the main window

**How to Use:**
1. From the main window, click "View Profile"
2. Your profile opens in a new window
3. You'll see every album or song you've rated, displayed as:
   ```
   [Item Name] ([Type]) - [Artist] - [Your Rating]/5
   ```
   
   Example: `Abbey Road (Album) - The Beatles - 4/5`

**What's Shown:**
- All your ratings in chronological order (newest last)
- Item type clearly labeled (Album or Song)
- Artist name for context
- Your rating out of 5

**Navigation:**
Click the "Back" button to return to the main application and continue browsing or searching.

*[Screenshot of profile window with sample ratings goes here]*

---

## 6. Complete User Flow (End-to-End)

**Typical Usage Scenario:**

1. **Start the app** → Login screen appears
2. **Log in** with credentials (demo / demo)
3. **Browse** → Main window shows full catalog
4. **Search** for an artist or album you like
5. **Select** an item from the results
6. **Rate** it on the 1-5 scale
7. **Repeat** for multiple items
8. **View Profile** to see all your ratings
9. **Go Back** to main window and search again
10. **Close app** (note: ratings are saved only during the session)

---

## Notes & Tips

- **Demo Account:** Use "demo" / "demo" to explore without creating a new account
- **Create Account:** Click "Sign Up" on the login screen to create a new user (username and password)
- **Session-Based:** Ratings are saved while the app is running but reset when you close the application
- **Multiple Items:** You can rate the same item multiple times; each rating appears as a separate entry
- **Search Filters:** Use the dropdown to quickly switch between viewing albums, songs, or both

---

## Summary

The Music-Shelf MVP provides a clean, intuitive interface for:
✅ User authentication (login/signup)
✅ Browsing a music catalog
✅ Searching by title or artist
✅ Rating your favorite albums and songs
✅ Viewing your personal rating history

This walkthrough demonstrates all core MVP functionality. Enjoy exploring the Music-Shelf!
