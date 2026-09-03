# Firebase Setup for ClashFit

This directory contains the Firestore rules and indexes for ClashFit's cloud backend.

## Data Model

### Collection: `users/{uid}`
The public profile: what the leaderboard shows. Every signed-in player can read every profile,
so this document deliberately holds no email and no other personal data. Firebase Auth keeps the
email; Firestore never sees it.

Fields:
- `displayName` (string, ≤ 24 chars): Player's display name
- `level` (number): Current level (1+)
- `xp` (number): Total XP earned
- `bestStreak` (number): Longest streak in days
- `friendCode` (string): 6-character friend code (derived from SHA-256 of uid)
- `createdAt` (timestamp): Account creation time
- `updatedAt` (timestamp): Last profile update time

### Collection: `scores/{weekKey}/entries/{uid}`
Stores weekly challenge scores. Multiple players' entries are grouped under a week key.

Week key format: ISO "YYYY-WNN" (e.g., "2026-W36")

Fields:
- `uid` (string): Player's UID
- `displayName` (string, ≤ 24 chars): Player's display name at time of update
- `level` (number): Player's level at time of update
- `weeklyDamage` (number): Damage dealt this week
- `weeklyCleanReps` (number): Clean reps this week
- `updatedAt` (timestamp): When this entry was last updated

### Collection: `users/{uid}/friends/{friendUid}`
Stores mutual friendships. A friendship is represented as edges in both directions.

Fields:
- `addedAt` (timestamp): When the friendship was created

## Setup Instructions (for Omkar)

1. **Create the Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com)
   - Create a new project named "ClashFit"
   - Accept the default settings

2. **Add an Android App**
   - In the Firebase Console, go to Project Settings
   - Add an Android app with package name: `com.clashfit`
   - Download the `google-services.json` file (do NOT commit this file to git)

3. **Enable Authentication**
   - In the Firebase Console, go to Authentication
   - Click "Get Started"
   - Enable Email/Password sign-in method
   - Keep the default settings

4. **Create Firestore Database**
   - In the Firebase Console, go to Firestore Database
   - Click "Create database"
   - Start in **production mode** (security rules protect access)
   - Choose the region closest to your users

5. **Deploy Security Rules**
   - Install the CLI once: `npm install -g firebase-tools`, then `firebase login`
   - From this directory: `cd firebase && firebase use --add` and pick the project you just created
   - Then: `firebase deploy --only firestore`
   - This deploys both the rules and the indexes. `firebase.json` in this directory points the CLI
     at `firestore.rules` and `firestore.indexes.json`, so no other configuration is needed.
   - Until the rules are deployed, a production-mode database denies every read and write, and the
     leaderboard in the app will correctly show itself as unavailable.

6. **Configure Local Build**
   - Extract values from `google-services.json`:
     - `current_key` → `FIREBASE_API_KEY`
     - `mobilesdk_app_id` → `FIREBASE_APP_ID`
     - `project_id` → `FIREBASE_PROJECT_ID`
   - Add these to `android/local.properties`:
     ```
     FIREBASE_API_KEY=<paste_api_key_here>
     FIREBASE_APP_ID=<paste_app_id_here>
     FIREBASE_PROJECT_ID=<paste_project_id_here>
     ```
   - The `local.properties` file is git-ignored; never commit it

7. **Important: Do NOT Commit google-services.json**
   - The `google-services.json` file contains sensitive configuration
   - It is automatically added to `.gitignore`
   - If it was accidentally committed, rotate the API key in the Firebase Console

## Firestore Security Rules

The `firestore.rules` file implements:

- **Read Access**: All players can read all user profiles and leaderboards
- **Write Access**:
  - Users can only modify their own `users/{uid}` document
  - Users can only add/remove their own friends in `users/{uid}/friends/*`
  - Users can only write their own weekly score in `scores/*/entries/{uid}`
- **Validation**: Display names are capped at 24 characters; numeric fields must be non-negative

## Weekly Challenge Scores

Scores are submitted after each session with a `ScoreSnapshot`. The week key is generated using ISO week format:

```
String.format("%04d-W%02d", year, weekOfYear)
// Example: 2026-W36
```

The MetaRepository's weekly progress is synced to Firestore if the metric matches (DAMAGE → weeklyDamage, CLEAN_REPS → weeklyCleanReps).
