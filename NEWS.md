Version 1.6 (2021-02-05)
========================

 * Added:
      - Auto-uncompress archive option (Android 8.0+)
      - Notifications about the file moving and checksum validation error
 * Improved filename parsing from URL
 * Temporary HTTP redirects are now saved as permanent
 * Bugfixing:
      - Android 10 storage access
      - Getting the gzip archives length
      - Progress notification visibility after re-running
 * New translations:
      - Malay
      - Swedish
      - Tamil
 * Updated current translations

Version 1.5 (2021-10-09)
========================

 * Android 12 support
 * Hiding non-writable directories in the built-in file manager for Android 11+
 * [Browser] Mixed content compatibility mode enabled
 * Bugfixing:
      - Google Drive downloading with progress and pausing
      - Content-Disposition parsing and symbols escaping
      - [Browser] Data URI handling
      - [Browser] Redirecting to a non-valid URL
 * Added Norwegian (Bokmål) language
 * Updated current translations

Version 1.4 (2021-07-19)
========================

 * Android 11 support
 * Added:
      - Speed limit
      - [Browser] Hide browser icon option
 * More improved Content-Disposition parsing
 * Bugfixing:
      - Downloading files that requires Referer header
      - Handling "Do not ask again" of the permissions
      - Adding the .bin extension
      - [Browser] Cookies option
      - Crash on Android 8.0 if tap on the text field
      - Sites that require WWW in URL
      - Handling HTTP 307

Version 1.3.1 (2021-05-05)
========================

 * Improved support for some URLs and filename parsing
 * Support for non-Unicode filenames
 * Added close and start page buttons in the browser
 * Bugfixing:
      - Applying custom User Agent
      - Including download description in the search
      - Storage free space checking
      - "Replace file" option
      - Small fixes

Version 1.3 (2020-09-07)
========================

 * Android 10 as a target platform
 * SSL cache optimization
 * Added:
      - "HTTP Referer" header field in the add download dialog
      - Saving options after closing the add download dialog
      - Opening app from download progress notification
 * Bugfixing:
      - Downloading from GDrive (currently works only with built-in browser)
      - [Browser] pinch zooming
      - [Browser] desktop page
      - Clipboard button in Android 10
      - Ability to close the pause notification
      - URL normalizing
 * New translations:
      - Persian
      - Ukrainian
      - Amharic

Version 1.2 (2020-05-25)
========================

 * Added simple built-in browser (WebView is required)
 * Improved MIME-type detection and filename extraction
 * Bugfixing:
      - Crash if the "Replace file" option is selected during download adding
      - Some ANR's
      - Stuck notifications
      - Displaying Indonesian language
      - Navbar color in a dark theme for some devices
      - Small fixes
 * New translations:
      - Bengali
      - French
      - Italian

Version 1.1.1 (2019-11-30)
========================

 * Fixed path selection through the system file manager
 * Added checksum verification option

Version 1.1 (2019-11-27)
========================

 * Bugfixing
 * Changed Dark and Black theme
 * Added:
     - Clipboard button in the add dialog
     - Resume button for failed downloads
     - Reboot button after changing the theme
     - Timeout settings
     - Adaptive delay between retries for failed downloads
     - Handling Retry-After header
     - Automatically adding file extensions after adding downloads
 * Updated current translations
 * New translations:
     - Azerbaijani
     - Indonesian
     - Vietnamese
     - Chinese Traditional

Version 1.0.3 (2019-04-27)
==========================

 * Bugfixing
 * Added:
     - Auto connect option for the add dialog
 * Updated current translations
 * New translations:
     - Hindi
     - Serbian

Version 1.0.2 (2019-04-25)
==========================

 * Bugfixing:
     - Partial download
     - "EBADF (Bad file descriptor)" error
     - "Share" menu for the download without file
     - Checking unsupported URL scheme
     - Notifications error for Android 4.4
     - Small fixes
 * Hide notify dot for the foreground notify
 * Changed splash background color
 * New translations:
     - Spanish
     - Brazilian Portuguese
     - Czech
     - Slovak
     - German
     - Japanese
     - Turkish
     - Arabic
     - Chinese Simplified

Version 1.0.1 (2019-04-15)
==========================

 * Bugfixing:
     - "Keep CPU awake" option
     - Support of sites without partial download, but with the existing content length
     - Small fixes

Version 1.0 (2019-04-14)
========================

First release.
