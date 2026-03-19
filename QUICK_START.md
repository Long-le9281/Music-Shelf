# Quick Start Guide - New Frontend

## 🚀 Getting Started

### Prerequisites
- Node.js and npm installed
- Backend running on port 8080 (Java)
- Database set up (run `database/setup.py`)

### Starting the Frontend

```bashcd frontend
# Navigate to frontend folder
cd frontend

# Install dependencies (first time only)
npm install

# Start development server
npm start
```

The app will open at **http://localhost:3000**

---

## 🎯 What's New - Quick Demo

### 1. **Vinyl Record Bin Carousel**
- **Left side** of the shelf page
- **Scroll with mouse wheel** to flip through albums
- **Larger album cards** (260px) with wood grain borders
- **Click any card** to jump to that album
- Feels like browsing a real vinyl record store!

### 2. **Song Detail Modal**
- **Hover over tracks** in the track list (right side)
- **Click any song** to open detailed information modal
- **See track details**: title, duration, album, notes
- **Close** by clicking ✕ button or clicking outside modal
- Beautiful fade-in and slide-up animations!

### 3. **Brighter, Warmer Design**
- **Beige gradient background** - Warm and inviting
- **Wood grain borders** - Vintage record store aesthetic
- **Large, readable text** - Easy on the eyes
- **Coral accents** - Interactive elements pop
- **Dark piano black text** - Professional and clear

---

## 🎵 Main Features Tour

### Shelf Page (Home)
```
┌─────────────────────────────────────────────┐
│  NAVBAR - Navigation & Login                │
├─────────────────┬───────────────────────────┤
│                 │                           │
│  VINYL BIN      │  ALBUM DETAILS            │
│  Carousel       │  ✓ Large cover art       │
│  (Larger cards) │  ✓ Album info            │
│                 │  ✓ Tags                  │
│  Scroll: ↑ ↓    │  ✓ Community rating      │
│                 │  ✓ Your rating (stars)   │
│                 │  ✓ TRACK LIST            │
│                 │    - Click tracks!       │
└─────────────────┴───────────────────────────┘
```

### How to Use
1. **Browse albums** - Scroll up/down on the left side
2. **See details** - Right side updates automatically
3. **Rate albums** - Click stars in "Your Rating" section
4. **Explore songs** - Click any track to see details
5. **Search** - Use "Search" in navbar to find albums

### Track Click → Song Detail Modal
```
┌──────────────────────────────┐
│  Track 3: Song Title         │  ← Click here
└──────────────────────────────┘
           ↓
┌────────────────────────────────────┐
│  ✕  Track 3: Song Title            │
│                                    │
│  Album: Album Name · Artist        │
│                                    │
│  Notes: Song description...        │
│                                    │
│  Details:                          │
│    Length: 3:45                    │
│    Release Year: 2020              │
│    Genre: Rock                     │
└────────────────────────────────────┘
```

---

## 🎨 New Color Scheme

| What | Color | Feel |
|------|-------|------|
| Background | Light Beige | Warm & Inviting |
| Text | Piano Black | Clear & Professional |
| Borders | Wood Grain Brown | Retro & Vintage |
| Buttons | Coral | Warm & Friendly |
| Cards | Dark Brown | Sophisticated |

---

## ⭐ Star Your Favorite Albums

```
1. Navigate to an album on the Shelf
2. Find "Your Rating" section (right side)
3. Click stars (1-5):
   ⭐ = Awful
   ⭐⭐ = Poor
   ⭐⭐⭐ = Good
   ⭐⭐⭐⭐ = Great
   ⭐⭐⭐⭐⭐ = Perfect
4. See "✓ Saved!" confirmation
5. Rating appears in your profile
```

---

## 🔍 Search Albums

```
1. Click "Search" in navbar
2. Type album, artist, or genre name
3. Results appear as you type
4. Click any album to go to Shelf
5. Full album loaded and ready to rate!
```

---

## 👤 View Your Profile

```
1. Sign in (top right corner)
2. Click your username
3. View all your ratings
4. See what you've rated and your scores
```

---

## 🎬 Key Animations You'll See

### Album Cards
- **Smooth scrolling** - Cards glide into position
- **Stacking effect** - Cards appear to layer
- **Scale changes** - Cards grow/shrink smoothly

### Song Modal
- **Fade-in** - Overlay appears smoothly (0.3s)
- **Slide-up** - Modal slides up from bottom (0.4s)
- **Bounce** - Elastic easing makes it feel playful

### Interactive Elements
- **Hover effects** - Items highlight when you hover
- **Button response** - Buttons lift slightly on click
- **Star scaling** - Stars grow when you hover over them

---

## 🔗 Key Pages

| Page | URL | What It Does |
|------|-----|--------------|
| Shelf | `/` | Browse vinyl bin, view album details |
| Search | `/search` | Find albums by title/artist/genre |
| Profile | `/profile/username` | View ratings from any user |
| Sign In | `/login` | Log in to your account |
| Sign Up | `/signup` | Create a new account |

---

## 💡 Pro Tips

### Browsing Efficiently
- **Scroll fast** - Quickly flip through albums
- **Click cards** - Jump to any album instantly
- **Use search** - Find specific albums fast

### Rating Strategy
- **Rate as you browse** - Keep track of favorites
- **Read descriptions** - Learn about albums first
- **Check community** - See what others think

### Discovering Music
- **Scroll casually** - Stumble upon new albums
- **Check search** - Find artists you know
- **View ratings** - Popular albums stand out

---

## ⚙️ Keyboard Shortcuts

| Action | How |
|--------|-----|
| Scroll left/right | Mouse wheel |
| Open search | Click Search link |
| Go home | Click ELGOONERS logo |
| Sign out | Click Sign Out |
| View profile | Click your username |

---

## 🐛 Troubleshooting

### Albums Won't Load
**Solution**: 
- Check backend is running on port 8080
- Refresh the page (Ctrl+R)
- Check browser console (F12)

### Can't Click Tracks
**Solution**:
- Album detail must be loaded first
- Wait for "Track List" to appear
- Try refreshing if stuck

### Rating Won't Save
**Solution**:
- Sign in first (required)
- Check internet connection
- Wait for "✓ Saved!" message
- Try again if it fails

### Modal Won't Close
**Solution**:
- Click ✕ button in top right
- Click dark area outside modal
- Refresh page as last resort

---

## 📚 More Documentation

For detailed information, see:
- **FRONTEND_IMPROVEMENTS.md** - All changes made
- **COLOR_PALETTE.md** - Design system details
- **USER_GUIDE.md** - Complete feature guide
- **COMPONENT_REFERENCE.md** - Technical details
- **VERIFICATION_CHECKLIST.md** - What was tested

---

## 🎉 Enjoy!

Your new frontend is ready to use! 

**Key things to try:**
1. ✅ Scroll through the vinyl record bin
2. ✅ Click a track to see song details
3. ✅ Rate an album with stars
4. ✅ Search for an album
5. ✅ Check the new warm, inviting design

Happy listening! 🎵✨

---

## 📞 Need Help?

- Check the troubleshooting section above
- Review the documentation files
- Check browser console for errors (F12)
- Ensure both frontend and backend are running

Enjoy your improved record collection interface!

