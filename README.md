# PromptMe

**PromptMe** is a lightweight, always‑on‑top, voice‑activated teleprompter app built with Java 24, JavaFX and Vosk. It lets you scroll your script hands‑free using offline speech recognition, or manually via mouse and keyboard, and ships as a portable App‑Image ZIP for Windows.

---

## Table of Contents

1. [Key Features](#key-features)  
2. [Prerequisites](#prerequisites)  
3. [Download & Installation](#download--installation)  
4. [Scripts](#scripts)  
5. [Running the App](#running-the-app)
6. [Demo](#demo)
7. [License](#license)  

---

## Key Features

- **Voice‑Activated Scrolling**  
  - Offline speech recognition via Vosk  
  - Homophone & fuzzy‑match support (“um”‑proof control)  

- **Live Word Highlighting**  
  - Current word in bold colour  
  - Neighboring words subtly tinted  
  - Click any word to jump instantly  

- **Script Import & Reflow**  
  - Supports `.txt`, `.docx`, `.doc`, `.pdf`  
  - Responsive `TextFlow` pane with auto‑reset  
  - Import via upload button or drag-and-drop

- **Session Timer**  
  - ▶︎ / ■ toggle starts & stops  
  - Elapsed time in `m:ss` format  

- **Customizable Appearance**  
  - Light / Dark theme toggle  
  - Adjustable font size (12 – 48 px)  
  - Smooth hover‑grow animations  

- **Manual Controls & Shortcuts**  
  - Spacebar advances word by word  
  - Vertical slider for fine scroll  
  - Click‑and‑drag (outside controls) to move/resize  

- **Always‑On‑Top & Transparent**  
  - Frameless, floating window  
  - Keep focus on your words, not distractions  

---

## Prerequisites

- **Windows x64** (for portable ZIP)  
- **No Java install required**—bundled runtime included  
- Optional (for building):  
  - Java 24 SDK  
  - Maven 3.8+  
  - WiX Toolset 3.11+ (for MSI builds)  

---

## Download & Installation

1. Go to the [Releases](https://github.com/Aboody03/PromptMe/releases) page.  
2. Download **PromptMe.zip** under **PromptMe v1.0.0**.  
3. Unzip anywhere you like (Downloads, Desktop, USB drive, etc.).
4. Double-click **PromptMe.exe** to launch.

---

## Scripts

We include two helper scripts to grab the Vosk model when you first set up—or in CI:

- **Windows PowerShell**  
  ```powershell
  .\scripts\download-model.ps1
  ```

This will:
  1. Download `vosk-model-small-en-us-0.15.zip` from the official Vosk site.
  2. Create `model/vosk-model-small-en-us-0.15/` directory.
  3. Unzip the model files into that folder.

- **macOS/Linux Bash (Untested)**
  ```bash
  ./scripts/download-model.sh
  ```
  Performs the same steps in a POSIX shell environment.

After running either script, you’ll have the complete `model/...` folder ready for offline recognition.

## Running the App
1. **Double‑click** `PromptMe.exe`.
2. **Upload** your script via the 📂 button.
3. **Speak** or use the spacebar/slider to advance.
4. **Toggle** themes and font size from the control bar.

## Demo

https://github.com/user-attachments/assets/6626be42-ffdc-4a62-8160-d35f55ee3d4a

## License
This project is licensed under the **MIT License**.
See [LICENSE](https://github.com/Aboody03/PromptMe/blob/main/LICENSE) for details.
