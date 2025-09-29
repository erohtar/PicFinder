# PicFinder

A privacy-focused Android app that scans user-selected folders for images, extracts text using OCR, and provides powerful local search functionality - all while keeping your data completely private and offline.

## Features

### 🔍 **Smart Text Search**
- OCR-powered text extraction from images using Google ML Kit
- Space-separated keyword search (finds images containing ALL keywords)
- Search through extracted text, file names, and folder paths
- Real-time search results with image thumbnails

### 📁 **Folder Management**
- Select and manage multiple folders for scanning
- Individual rescan and remove buttons for each folder
- Display folder paths and image counts
- Persistent folder selection across app restarts

### ⚙️ **Flexible Scanning Options**
- Daily, weekly, or manual-only scan frequency
- Background scanning with WorkManager
- Progress tracking during scans
- Automatic cleanup of deleted images

### 🎨 **Material Design UI**
- Dark theme with cyan accent colors
- Bottom navigation with three main sections:
  - **Search**: Find images by text content
  - **Folders**: Manage scanned folders
  - **Settings**: Configure scan frequency and view statistics

### 🔒 **Privacy First**
- **100% local processing** - no data leaves your device
- No internet connection required for core functionality
- All OCR processing happens on-device using ML Kit
- Local SQLite database for storing extracted text

## Screenshots Reference

The app interface matches the provided screenshots:
- **Search Tab**: Clean search interface with image thumbnails and extracted text previews
- **Folders Tab**: List of selected folders with rescan/remove buttons and image counts
- **Settings Tab**: Scan frequency options, database statistics, and manual controls

## Technical Architecture

### Database
- **SQLite** with Room persistence library
- **ImageEntity**: Stores file paths, extracted text, metadata
- **FolderEntity**: Manages selected folders and scan information

### OCR Integration
- **Google ML Kit Text Recognition** for local text extraction
- Supports common image formats: JPG, JPEG, PNG, BMP, WebP
- Processes images asynchronously with error handling

### Background Processing
- **WorkManager** for scheduled background scans
- Configurable scan frequency (daily/weekly/manual)
- Battery-optimized with appropriate constraints

### Search Algorithm
- Space-separated keyword matching
- Searches across extracted text, file names, and folder paths
- Real-time results with debounced input (300ms delay)

## Installation

### Prerequisites
- Android device running API level 24+ (Android 7.0+)
- Storage permissions for accessing image folders

### APK Installation
1. Download the APK file: `PicFinder-debug.apk` (47.6 MB)
2. Enable "Install from unknown sources" in your device settings
3. Install the APK on your OnePlus Open or compatible device

### First Run Setup
1. Grant storage permissions when prompted
2. Navigate to the "Folders" tab
3. Tap "Add Folder" to select image directories
4. The app will automatically start scanning selected folders
5. Use the "Search" tab to find images by text content

## Usage Guide

### Adding Folders
1. Go to **Folders** tab
2. Tap **Add Folder**
3. Select directories containing images
4. The app will scan and extract text from all images

### Searching Images
1. Go to **Search** tab
2. Type keywords in the search box
3. Results show images containing ALL keywords
4. Tap any result to open the image

### Managing Scans
1. Go to **Settings** tab
2. Choose scan frequency (Daily/Weekly/Manual Only)
3. View database statistics
4. Use "Scan All Folders Now" for immediate scanning
5. Clear database if needed

## Development

### Built With
- **Kotlin** - Primary programming language
- **Android Jetpack** - Architecture components
- **Room Database** - Local data persistence
- **ML Kit** - On-device text recognition
- **WorkManager** - Background task scheduling
- **Material Design 3** - UI components
- **Glide** - Image loading and caching

### Project Structure
```
app/src/main/java/com/picfinder/app/
├── data/
│   ├── database/          # Room entities and DAOs
│   └── repository/        # Data repository layer
├── ui/
│   ├── search/           # Search functionality
│   ├── folders/          # Folder management
│   └── settings/         # App settings
├── utils/                # Utility classes
│   ├── OCRService        # Text extraction
│   ├── ImageScanService  # Image scanning logic
│   └── WorkManagerUtils  # Background scheduling
└── workers/              # Background workers
```

### Key Components

#### OCRService
- Handles text extraction from images using ML Kit
- Supports file paths and URIs
- Error handling and logging

#### ImageScanService
- Manages folder scanning operations
- Progress tracking and reporting
- Database updates and cleanup

#### Search Functionality
- Real-time search with debouncing
- Multi-keyword support with AND logic
- Efficient database queries

## Privacy & Security

- **No network permissions** - App works completely offline
- **Local processing only** - OCR happens on your device
- **No data collection** - No analytics or tracking
- **Secure storage** - All data stored locally in encrypted SQLite database

## Compatibility

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Architecture**: ARM64, ARM32
- **Storage**: Requires access to external storage
- **Tested on**: OnePlus Open and compatible devices

## File Sizes
- **Debug APK**: 47.6 MB
- **Release APK**: 46.1 MB (unsigned)

## Future Enhancements

Potential improvements for future versions:
- Support for additional image formats
- Advanced search filters (date, size, folder)
- Export/import functionality for database
- Batch operations on search results
- OCR accuracy improvements
- Performance optimizations for large image collections

## License

This project is developed as a privacy-focused, local-first application. All processing happens on-device to ensure user privacy and data security.

---

**Ready to install**: Use `PicFinder-debug.apk` for immediate installation on your OnePlus Open device.