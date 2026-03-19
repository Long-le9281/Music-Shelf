# User Guide - New Frontend Features

## 🎵 Browsing the Vinyl Record Bin

### How to Navigate Albums
1. **Start on the Shelf page** - You'll see the vinyl record bin on the left side
2. **Scroll with your mouse wheel** - 
   - Scroll **UP** → flip to previous album
   - Scroll **DOWN** → flip to next album
3. **Click any album card** - Jump directly to that album (shows 5 cards at a time)

### Visual Cues
- **Center card** - Currently selected album (in focus)
- **Cards above/below** - Next albums in the bin
- **Scroll hint** (bottom) - Reminds you to scroll to browse

---

## 📖 Song Details - Expanded Modal

### Accessing Song Information
1. **View the track list** - Appears on the right side under the album art
2. **Click on any track** - Opens an expanded detail modal
3. **Modal shows**:
   - 🎵 Track number and title
   - ⏱️ Duration
   - 📀 Album information
   - 📝 Song notes (if available)
   - 📊 Additional metadata

### Closing the Modal
- **Click the ✕ button** (top right)
- **Click outside the modal** (on the dark overlay)
- **Press Escape key** (optional, if implemented in future)

---

## ⭐ Rating Albums

### How to Rate
1. **Sign in or create account** - Required for ratings
2. **Navigate to an album**
3. **Find "Your Rating" section** (right side, below album info)
4. **Click stars** (1-5) to rate:
   - ⭐ = Awful
   - ⭐⭐ = Poor
   - ⭐⭐⭐ = Good
   - ⭐⭐⭐⭐ = Great
   - ⭐⭐⭐⭐⭐ = Perfect

5. **Confirmation** - You'll see "✓ Saved!" message

### Community Ratings
- **View average** - Shows rating summary and number of votes
- **Help others** - Your ratings contribute to the community average

---

## 🔍 Search & Discovery

### Quick Search
1. **Click "Search"** in the navigation bar
2. **Type your query** - Search by:
   - Album title
   - Artist name
   - Genre
   - Year

3. **View results** - Browse matching albums in a grid
4. **Click album** - Navigate to the Shelf page with that album

### Search Tips
- Search is **case-insensitive**
- Results update as you type
- Shows 350ms after you stop typing (auto-search)

---

## 👤 User Profile

### Viewing Your Profile
1. **Click your username** in the top right (after signing in)
2. **View your stats**:
   - Total album ratings
   - All your rated albums
   - Star ratings you gave

### Viewing Other Profiles
- **From anywhere**: Navigate to `/profile/username`
- **Example**: `/profile/vinyl_lover`
- View what others have rated and their opinions

---

## 🌟 Design Features

### Modern, Warm Aesthetic
- **Bright, inviting colors** - Warm creams and beiges
- **Wood grain styling** - Reminds you of real record bins
- **Piano black accents** - Sophisticated, readable text
- **Smooth animations** - All transitions feel polished

### Vinyl Record Bin Experience
- **Stacked cards** - Like flipping through records
- **Larger album art** - Better visibility (260×260px)
- **Textured styling** - Visual depth and interest
- **Smooth scrolling** - Natural, fluid interaction

### Interactive Elements
- **Hovering over items** - Highlights with color and shadow
- **Clicking tracks** - Opens detailed information
- **Responsive feedback** - Buttons show state changes
- **Smooth modals** - Beautiful fade-in animations

---

## 🎯 Navigation

### Main Pages
- **Shelf** - Browse vinyl record bin and view album details (home)
- **Search** - Find albums by title, artist, or genre
- **Profile** - View your ratings and account info
- **Sign In/Sign Up** - Authentication pages

### Quick Links (Navbar)
- **ELGOONERS** logo - Returns to Shelf page
- **SHELF** - Main page
- **SEARCH** - Search albums
- **[YOUR USERNAME]** - Your profile (when signed in)
- **SIGN OUT** - Logout (when signed in)

---

## 💡 Tips & Tricks

### Getting the Most Out of the Shelf
1. **Use scroll wheel** - Fastest way to browse albums
2. **Click album cards** - Jump to specific albums
3. **Read descriptions** - Learn about albums before rating
4. **Check community ratings** - See what others think
5. **Click tracks** - Discover more about individual songs

### Making the Most of Ratings
1. **Be honest** - Your ratings help the community
2. **Rate multiple albums** - Build your profile
3. **Check average ratings** - Popular albums are highlighted
4. **Revisit albums** - Update your rating if opinion changes

### Finding Albums Quickly
1. **Use search bar** - Fastest for specific albums
2. **Browse shelf** - Great for discovery
3. **Check profiles** - See what similar users enjoy
4. **Note favorites** - Click and rate to remember

---

## 🎨 Visual Guide

### Color Meanings
- **Warm Beige** (#f5f1ed) - Friendly, inviting background
- **Wood Brown** (#8b7355) - Frames and accents (vinyl bin inspired)
- **Dark Brown** (#3a3430) - Card backgrounds and accents
- **Piano Black** (#2c2420) - Clear, readable text
- **Coral** (#d4744f) - Interactive elements and CTAs

### Text Sizes
- **Large** (3rem) - Album titles
- **Medium** (0.9-1rem) - Section titles, artist names
- **Small** (0.68-0.85rem) - Metadata, descriptions

---

## ⚙️ Settings & Preferences

### Account Management
- **Change password** - Coming soon
- **Update avatar** - Coming soon
- **Privacy settings** - Coming soon

### Preferences
- **Theme switching** - Future feature
- **Sort/filter albums** - Future feature

---

## 🐛 Troubleshooting

### Albums Won't Load
- **Check backend** - Ensure Java backend is running on port 8080
- **Refresh page** - Press Ctrl+R or Cmd+R
- **Check console** - Open DevTools (F12) for error messages

### Ratings Not Saving
- **Sign in first** - You must be logged in to rate
- **Check connection** - Verify internet connection
- **Try again** - If the server is busy, wait and retry

### Search Not Working
- **Wait for results** - Search waits 350ms after you stop typing
- **Check spelling** - Typos won't find matches
- **Try variations** - Artist name vs album title

### Modal Won't Close
- **Click the ✕ button** - Top right of modal
- **Click outside modal** - On the dark overlay
- **Refresh page** - As a last resort

---

## 🎬 What's New in This Update

✨ **New Features**:
- 🎨 Brighter, hippie-modern color scheme
- 🎵 Enhanced vinyl record bin carousel
- 📖 Clickable songs with expanded detail modal
- 🌟 Centered, balanced layouts
- 🎯 Better visual hierarchy and spacing

📱 **Improvements**:
- Larger album covers (260px)
- Better hover states and feedback
- Smooth animations throughout
- Improved mobile responsiveness
- Cleaner typography and spacing

---

## 📞 Support

If you encounter any issues:
1. Check this guide first
2. Review console for error messages (F12)
3. Ensure backend is running
4. Clear browser cache
5. Contact support with error details

Happy listening! 🎵

