# RecallLive - Memory Preservation App for Alzheimer's Patients

**Congressional App Challenge 2024 Submission**

## 📱 About RecallLive

RecallLive is an Android application designed to help Alzheimer's patients preserve memories and maintain connections with loved ones through automated daily memory videos, AI-powered photo organization, and real-time emotion tracking.

---

## 🚀 How to Run the App

### Prerequisites
- Android device running Android 8.0 (API 26) or higher
- OR Android Studio with an emulator (Pixel 4 or newer recommended)

### Installation Steps

1. **Download the APK**
    - Locate `RecallLive.apk` in the submission folder
    - Transfer to your Android device

2. **Enable Installation from Unknown Sources**
    - Go to Settings → Security → Install unknown apps
    - Select your file manager/browser
    - Enable "Allow from this source"

3. **Install the APK**
    - Tap the APK file on your device
    - Tap "Install"
    - Wait for installation to complete (~30 seconds)

4. **Launch the App**
    - Open "RecallLive" from your app drawer
    - You'll see the welcome screen

5. **Create a Test Account**
    - Choose **"Patient Signup"**
    - Enter test credentials:
        - **Email:** `testpatient@example.com`
        - **Password:** `Test123456`
        - **Guardian Email:** (optional) leave blank for now
    - Tap "Sign Up"

6. **Grant Required Permissions**
    - When prompted, allow:
        - ✅ **Storage/Photos** - Required to access your photos
        - ✅ **Camera** - Required for emotion tracking during videos
        - ✅ **Location** - Optional, helps with photo organization

7. **Wait for Initial Setup (2-3 minutes)**
    - The app will automatically:
        - Scan your device for photos
        - Organize them by location and time
        - Generate your first memory video
    - You'll see a progress notification
    - **First video generation takes ~2-3 minutes**

8. **Explore the App**
    - **Home Tab:** View your daily memory videos
    - **Quiz Tab:** Take location-based memory quizzes
    - **Settings Tab:** Adjust preferences

---

## 🗂️ Core Functionality & Implementation

### 1️⃣ **Automatic Photo Clustering**
**What it does:** Organizes photos by location (GPS) and time into meaningful clusters

**Key Implementation Files:**
- **`PhotoClusteringManager.java`** (lines 15-300)
    - Core DBSCAN clustering algorithm
    - Groups photos within 100-meter radius
    - Creates time-based clusters (3-hour windows)

- **`PhotoMetadataExtractor.java`** (lines 15-150)
    - Extracts EXIF metadata (GPS, timestamps)
    - Reads from device MediaStore
    - Determines time of day (morning/afternoon/evening/night)

- **`PhotoProcessingService.java`** (lines 15-250)
    - Orchestrates the clustering pipeline
    - Manages background threading with AppExecutors
    - Saves results to both local database and Firebase

- **`FirebaseClusterManager.java`** (lines 15-350)
    - Stores clusters in Firebase Realtime Database
    - Retrieves clusters for video generation
    - Path: `Patient/{uid}/clusters/{clusterId}`

**Algorithm Overview:**
```
1. Scan device photos → Extract GPS + timestamp
2. Spatial clustering (DBSCAN, 100m radius)
3. Temporal clustering (3-hour windows)
4. Generate cluster metadata (location name, time description)
5. Store in Firebase
```

---

### 2️⃣ **Daily Automatic Video Generation**
**What it does:** Creates personalized memory videos from photo clusters with AI narration

**Key Implementation Files:**
- **`AutomaticVideoService.java`** (lines 1-650) - ⭐ **MAIN VIDEO SERVICE**
    - Schedules daily video generation at midnight
    - Generates 10 videos on signup/login
    - Manages video lifecycle (creation, cleanup)
    - Uses WorkManager for reliable background execution

- **`Media3VideoGenerator.java`** (lines 1-700) - ⭐ **VIDEO ENCODING ENGINE**
    - **Line 140-180:** Photo selection and loading
    - **Line 200-250:** Bitmap to video dimension scaling
    - **Line 300-450:** Video file creation with MediaCodec
    - **Line 540-640:** YUV420 color space conversion (critical for correct colors)
    - Creates MP4 videos (720x1280 portrait, H.264 codec)
    - Handles landscape → portrait rotation

- **`TTSVideoGenerator.java`** (lines 1-400)
    - Generates narration scripts based on location/time
    - Uses Android TextToSpeech API
    - Creates WAV audio files
    - Speech rate: 0.85x (slower for memory patients)

- **`VideoAudioMerger.java`** (lines 1-250)
    - Merges silent video with TTS audio
    - Uses MediaMuxer and MediaExtractor
    - Uploads final video to Firebase Storage

**Video Generation Process:**
```
1. Select cluster → Pick 4 random photos
2. Generate narration script (location + time context)
3. Encode photos to video (MediaCodec)
4. Synthesize TTS audio (TextToSpeech)
5. Merge audio + video (MediaMuxer)
6. Upload to Firebase Storage
7. Save metadata to Firestore
```

**Configuration:** See `VideoConfiguration.java` for customizable settings:
- Videos per day: 10
- Generation time: Midnight
- TTS enabled: Yes
- Auto-cleanup: Enabled

---

### 3️⃣ **Real-Time Emotion Tracking**
**What it does:** Detects facial emotions during video playback using machine learning

**Key Implementation Files:**
- **`BackgroundEmotionDetector.java`** (lines 1-550) - ⭐ **EMOTION DETECTION ENGINE**
    - **Line 80-120:** TensorFlow Lite model initialization
    - **Line 180-250:** Face detection with ML Kit
    - **Line 300-400:** Emotion classification (7 emotions)
    - **Line 450-500:** Emotion timeline recording
    - Detects: Happy, Sad, Angry, Neutral, Fear, Disgust, Surprise
    - Runs every 2 seconds during playback
    - Uses front camera (invisible to user)

- **`PatientVideoOpenedFragment.java`** (lines 1-400)
    - Video player with integrated emotion tracking
    - Starts emotion detection on video play
    - Saves emotion data to Firebase on completion
    - Path: `Patient/{uid}/videoEmotions/{videoId}`

- **`model_filter.tflite`** (app/src/main/assets/)
    - Pre-trained CNN for emotion recognition
    - Input: 48x48 grayscale face image
    - Output: 7 emotion probabilities

**Technical Details:**
- Face detection: ML Kit Face Detection API
- Emotion model: Custom TensorFlow Lite CNN
- Processing: 48x48 grayscale, normalized [0,1]
- Inference time: ~50ms per frame
- Sampling rate: Every 2 seconds

---

### 4️⃣ **Daily Memory Quiz**
**What it does:** Location-based quiz to stimulate cognitive recall

**Key Implementation Files:**
- **`PatientQuizFragment.java`** (lines 1-450)
    - **Line 100-150:** Daily reset logic
    - **Line 200-300:** Random question generation
    - **Line 350-400:** Answer validation
    - Generates 10 random questions daily
    - Shows photo, asks "Where is this?"
    - 4 multiple choice (1 correct + 3 random locations)

**Quiz Features:**
- Resets at midnight daily
- Questions randomized from photo clusters
- Tracks score and completion
- Saves results to Firebase: `Patient/{uid}/quizResults/`

---

### 5️⃣ **Patient-Guardian Connection**
**What it does:** Allows caregivers to monitor patient's emotional responses

**Key Implementation Files:**
- **`GuardianHomeFragment.java`** (lines 1-400)
    - Dashboard showing all patient videos
    - Displays emotion summaries per video
    - Shows engagement statistics

- **`GuardianVideoViewerFragment.java`** (lines 1-350)
    - Plays patient videos with emotion timeline
    - Shows detailed emotion breakdown
    - Visualizes emotional responses during playback

- **`LoginActivity.java`** (lines 250-400)
    - Account linking logic
    - Searches for guardian by email
    - Creates bidirectional link: Patient ↔ Guardian

**Guardian Features:**
- View all patient videos
- See emotion data per video
- Monitor engagement trends
- Secure account linking

---

### 6️⃣ **User Authentication**
**Key Implementation Files:**
- **`LoginActivity.java`** (lines 1-550)
    - Firebase Authentication
    - User type detection (Patient/Guardian)
    - Automatic service initialization

- **`PatientSignupActivity.java`** (lines 1-450)
    - Patient account creation
    - Optional guardian linking
    - Triggers initial setup (clustering + video generation)

- **`GuardianSignupActivity.java`** (lines 1-350)
    - Guardian account creation
    - Patient email verification
    - Account linkage

---

### 7️⃣ **Background Services & Automation**
**Key Implementation Files:**
- **`AutoClusteringService.java`** (lines 1-300)
    - Automatic photo clustering on login
    - Periodic re-clustering (weekly)
    - Uses WorkManager for reliability

- **`DailyReminderReceiver.java`** (lines 1-200)
    - Push notifications for new videos
    - Scheduled using AlarmManager

- **`BootReceiver.java`** (lines 1-150)
    - Restarts services after device reboot
    - Ensures continuous operation

**Background Job Management:**
- Uses WorkManager for reliable scheduling
- Constraints: WiFi + charging + battery not low
- Daily execution at midnight

---

## 📊 Technical Architecture

### Technology Stack
- **Language:** Java (Android SDK)
- **Backend:** Firebase
    - Authentication (user accounts)
    - Realtime Database (clusters, emotions)
    - Firestore (video metadata)
    - Storage (video files)
- **Machine Learning:**
    - Google ML Kit (face detection)
    - TensorFlow Lite (emotion classification)
    - Android TextToSpeech (narration)
- **Video Processing:**
    - MediaCodec (H.264 encoding)
    - MediaMuxer (audio/video merging)
    - MediaExtractor (file reading)
- **Background Jobs:** WorkManager
- **Database:** Room (local photo cache)

### Data Flow
```
Device Photos → EXIF Extraction → Clustering → Firebase Storage
                                                      ↓
Guardian ← Firebase ← Emotion Data ← Video Playback ← Video Generation
```

---

## 🎯 Testing Guide for Judges

### Test Scenario 1: First-Time Patient Setup
1. Sign up as new patient
2. Grant photo permissions
3. Observe automatic clustering (check notifications)
4. Wait 2-3 minutes for first video
5. **Expected:** Video appears in Home tab

### Test Scenario 2: Watch Video with Emotion Tracking
1. Tap any video in Home tab
2. Allow camera permission
3. Watch video while facing front camera
4. **Expected:** Emotions tracked in background (check Firebase console)

### Test Scenario 3: Take Memory Quiz
1. Go to Quiz tab
2. Answer 10 questions about photo locations
3. Get immediate feedback on answers
4. **Expected:** Score displayed at end, saves to Firebase

### Test Scenario 4: Guardian Monitoring
1. Create guardian account
2. Enter patient email during signup
3. Login as guardian
4. View patient videos and emotion data
5. **Expected:** See all videos + emotion breakdowns

### Test Scenario 5: Daily Automation
1. Keep app installed overnight
2. Check next day at 12:01 AM
3. **Expected:** 10 new videos generated automatically

---

## 🐛 Troubleshooting

### No Videos Appearing
- **Cause:** No photos on device or permissions denied
- **Fix:** Add sample photos with GPS metadata, restart app

### Video Generation Failed
- **Cause:** Insufficient storage or network issues
- **Fix:** Free up 500MB storage, connect to WiFi

### Emotion Tracking Not Working
- **Cause:** Camera permission denied or model file missing
- **Fix:** Grant camera permission, check `model_filter.tflite` exists

### Login Issues
- **Cause:** Firebase connection problems
- **Fix:** Check internet connection, verify `google-services.json`

---

## 📁 Project Structure

```
RecallLive/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/recalllive/
│   │   │   ├── AutomaticVideoService.java    ⭐ Daily video generation
│   │   │   ├── Media3VideoGenerator.java     ⭐ Video encoding
│   │   │   ├── BackgroundEmotionDetector.java ⭐ Emotion tracking
│   │   │   ├── PhotoClusteringManager.java   ⭐ Clustering algorithm
│   │   │   ├── PatientQuizFragment.java      ⭐ Memory quiz
│   │   │   ├── LoginActivity.java
│   │   │   ├── PatientSignupActivity.java
│   │   │   └── ... (40+ other files)
│   │   ├── assets/
│   │   │   └── model_filter.tflite          (Emotion recognition model)
│   │   └── res/
│   └── build.gradle
├── google-services.json                      (Firebase config)
└── README.md                                 (This file)
```

---

## 👥 Credits

- **Developer:** [Your Name]
- **School:** [Your School]
- **Congressional District:** [Your District]
- **Submission:** Congressional App Challenge 2024

### Third-Party Libraries
- Firebase SDK
- Google ML Kit
- TensorFlow Lite
- AndroidX Libraries

---

## 📄 License

This project is submitted for the Congressional App Challenge 2024.

---

## 📧 Contact

For questions about this submission, please contact:
- Email: hojinshin2027@gmail.com
- GitHub: HojiShin