# ProNavi Compass for Wear OS

**Navigate with Precision: A Feature-Rich Compass for Your Pixel Watch**

![PXL_20250406_182111445](https://github.com/user-attachments/assets/a3d6b1aa-3940-434d-b3b9-5f9453deb1e4)
![PXL_20250406_124152110](https://github.com/user-attachments/assets/a20b2a29-474e-4036-bc27-ce80ed535ecd)

ProNavi Compass transforms your Google Pixel Watch (and other compatible Wear OS devices) into an advanced navigation tool. Designed for outdoor enthusiasts, hikers, and anyone needing reliable orientation, this app combines classic compass functionality with modern GPS data and intuitive controls.

## Key Features

*   **Accurate Compass:** Displays a clear compass rose with a primary needle indicating North.
*   **Magnetic & True North Toggle:** Easily switch between Magnetic North and True North references by tapping the mode indicator icon (🐾/🐈). The needle and BRG readout color change to reflect the current mode (Red for Magnetic, Blue for True).
*   **Rotating Bezel for Target Bearing:** Use your watch's crown to rotate the bezel and set a desired bearing.
*   **"Go-To" Needle:** A distinct yellow needle always points towards the fixed 12 o'clock bearing marker, providing a clear visual target to align with when following a set bezel bearing.
*   **Comprehensive GPS Data Readouts:**
    *   **BRG (Bearing):** Current bearing set on the bezel relative to the top marker (shown as °M or °T).
    *   **Spd (Speed):** Current speed over ground in km/h.
    *   **Alt (Altitude):** Current altitude in meters.
    *   **Decl (Declination):** Current magnetic declination at your location.
    *   **Loc (Local Time):** Current local time.
    *   **UTC (Coordinated Universal Time):** Current UTC time.
*   **Level Bubble / Clinometer:** A visual bubble level on the right side of the display indicates the watch's current pitch and roll.
*   **Sensor Accuracy Indicator:** A small colored dot near the bubble level provides real-time feedback on the compass sensor's accuracy:
    *   **Green:** High Accuracy
    *   **Yellow:** Medium Accuracy
    *   **Red:** Low Accuracy (Calibration Recommended)
    *   **Gray:** Unreliable (Calibration Required)
*   **Edge-to-Edge Bezel Marks:** Maximizes central display space by placing bezel tick marks right at the screen edge.
*   **Secret Overlay Text:** Long-press (3+ seconds) the mode indicator icon to reveal a hidden message.
*   **Fun Easter Egg:** Double-tap the mode indicator icon for a playful animation!

## How to Use

1.  **Main Compass:**
    *   The **Red Needle** (default) points to **Magnetic North**.
    *   The **Blue Needle** points to **True North**.
    *   The **Yellow Needle** always points **straight up** towards the light blue triangular **Bearing Marker** at the 12 o'clock position. This marker is your reference for the "BRG" readout.

2.  **Setting a Bearing:**
    *   Rotate your watch's **crown**. This will turn the outer compass bezel.
    *   Align the desired bearing number (e.g., 090 for East) on the bezel with the **top light blue Bearing Marker**.
    *   The central "BRG" readout will display this set bearing.

3.  **Navigating to a Bearing:**
    *   Once your desired bearing is set on the bezel (e.g., "N" on the bezel is under the blue marker if you want to go North):
    *   Physically turn your body/watch until the **main North Needle (Red or Blue)** aligns with the "N" (or other cardinal/degree marking) on the **rotating bezel** that corresponds to your desired heading.
    *   When you are facing the direction indicated by the blue top marker, the yellow "Go-To" needle will also be pointing straight up, aligned with the blue marker and your physical direction of travel.

4.  **Switching North Reference (Magnetic/True):**
    *   **Tap** the **Mode Icon** (🐾/🐈) on the left side of the screen.
    *   The icon will change (Cat 🐈 for Magnetic, Paws 🐾 for True).
    *   The main needle color will change (Red for Magnetic, Blue for True).
    *   The "BRG" readout color and suffix (°M/°T) will update.

5.  **Interpreting Sensor Accuracy:**
    *   Observe the small colored dot near the bubble level. Refer to the "Key Features" section for color meanings. If Red or Gray, attempt to calibrate your watch's compass by moving it in a figure-8 motion.

## Easter Eggs

*   **Secret Message:** Long-press the mode icon (🐾/🐈) for 3+ seconds.
*   **Squirrel Chase:** Double-tap the mode icon (🐾/🐈) quickly. If the squirrel catches the cat, enjoy the show!

## Technology Stack

*   **Language:** Kotlin
*   **Platform:** Wear OS (Optimized for Google Pixel Watch 3, compatible with other Wear OS devices)
*   **Sensors:** `TYPE_ROTATION_VECTOR` (fused orientation), GPS
*   **UI:** Custom `View` drawing on `Canvas`

## Future Ideas (Roadmap)

*   GPS Signal Strength Indicator
*   "Back to Start" Waypoint Navigation
*   Night Mode / Dimmed Theme
*   Sun Azimuth & Altitude Display






