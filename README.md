# Compass Cat for Wear OS

**Navigate with Precision: A Feature-Rich Compass for Your Pixel Watch**

![PXL_20250406_182111445](https://github.com/user-attachments/assets/a3d6b1aa-3940-434d-b3b9-5f9453deb1e4)
![PXL_20250406_124152110](https://github.com/user-attachments/assets/a20b2a29-474e-4036-bc27-ce80ed535ecd)

Compass Cat transforms your Google Pixel Watch (and other compatible Wear OS devices) into an advanced navigation tool. Designed for outdoor enthusiasts, hikers, and anyone needing reliable orientation, this app combines classic compass functionality with modern GPS data and intuitive controls.

## Key Features

*   **Accurate Compass:** Displays a clear compass rose with a primary needle indicating North.
*   **Magnetic & True North Toggle:** Easily switch between Magnetic North and True North references by tapping the mode indicator icon (🐾/🐈).
*   **Battery Optimized:** Significant performance improvements in v1.2 with lower sensor sampling rates and adaptive GPS frequency.
*   **Ambient Mode Support:** High-contrast grayscale UI for Always-On Display, protecting your screen from burn-in while saving battery.
*   **Rotating Bezel for Target Bearing:** Use your watch's crown to rotate the bezel and set a desired bearing.
*   **"Go-To" / Waypoint Needle:** 
    *   The **Yellow Needle** always points towards your set **Waypoint**.
    *   If no waypoint is set, it points towards the fixed 12 o'clock bearing marker.
*   **Theme Cycle (Day/Night/NVG):** Three distinct themes to preserve vision in all conditions:
    *   **Normal:** Standard white/black theme.
    *   **Tactical Red:** All-red theme for preserving night vision.
    *   **NVG Green:** High-contrast green "Night Vision Goggle" style.
*   **Comprehensive GPS Data Readouts:**
    *   **BRG (Bearing):** Current bearing set on the bezel relative to the top marker.
    *   **Spd / Alt:** Current speed (km/h) and altitude (m).
    *   **Lat / Lon:** Real-time GPS coordinates for emergency reporting.
    *   **Decl:** Current magnetic declination at your location.
*   **Level Bubble / Clinometer:** A visual bubble level on the right side of the display.
*   **Calibration Warning:** A prominent figure-8 icon and "CALIBRATE" prompt appear when sensors detect interference.

## How to Use

1.  **Main Compass:**
    *   **Red Needle** = Magnetic North.
    *   **Blue Needle** = True North.
    *   **Yellow Needle** = Your Waypoint (or straight up if none set).

2.  **Interaction Model (2-Second Long Presses):**
    *   **Long-Press Center**: Cycle Themes (Normal → Red → Green).
    *   **Long-Press Edge/Bezel**: Set Waypoint to your current location.
    *   **Long-Press Cat Icon**: Show Callsign **"VA3FOD"** and the current **App Version** (stays for 2s after release).

3.  **Cat Icon Taps:**
    *   **Single Tap**: Toggle **True North** (🐾) vs **Magnetic North** (🐈).
    *   **Double Tap**: Start the **Squirrel Chase** Easter Egg.

4.  **Calibration:**
    *   If you see the figure-8 animation or "CALIBRATE (8)", move your watch in a large figure-8 motion in the air until it disappears.

## Technology Stack

*   **Language:** Kotlin
*   **Platform:** Wear OS (Optimized for Google Pixel Watch 3)
*   **Sensors:** `TYPE_ROTATION_VECTOR`, GPS
*   **UI:** Custom `View` drawing on `Canvas`

## Changelog

### v1.2
*   **App Renamed to "Compass Cat"**.
*   **Battery Optimizations**: Reduced sensor sampling rate (15Hz vs 50Hz+) to save power.
*   **Adaptive GPS**: Smarter location update frequency to preserve battery during long trips.
*   **Ambient Mode**: Added a low-power grayscale UI for always-on display support.
*   **Version Info**: Added app version display to the "Secret" callsign screen.

### v1.1
*   Added **Night Mode** (Tactical Red) and **NVG Green** themes.
*   Added **Waypoint / Return to Start** functionality with yellow needle.
*   Added **Live GPS Coordinates** (Lat/Lon) to readouts.
*   Improved **Calibration Warning** with figure-8 visual.
*   Standardized all long-presses to 2 seconds.
*   Callsign persistence (stays 2s after release).
*   Fixed fireworks animation speed and sparkle effect.
