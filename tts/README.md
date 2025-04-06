# Installation

This guide details how to set up the necessary environment and install dependencies to run this project, which uses the `kokoro` TTS library.

**TL;DR:** Use Python 3.9-3.12, install `espeak-ng`, create a virtual environment, and `pip install kokoro==0.7.16 soundfile`. (Note: `kokoro>=0.9.4` currently has installation issues).

## Prerequisites

1.  **Python (Version 3.9 - 3.12 Recommended):** This project requires a specific Python version for compatibility with its dependencies. Versions 3.9, 3.10, 3.11, and 3.12 are known to work well. **Python 3.13+ is NOT recommended** due to C extension compilation issues with some dependencies (like `numpy`/`blis`).
    * We strongly recommend using [**`pyenv`**](https://github.com/pyenv/pyenv) to manage Python versions easily. Follow their instructions to install it (e.g., `brew install pyenv` on macOS).

2.  **`espeak-ng`:** This is a system-level dependency required by `kokoro`. Install it using your system's package manager:
    * **macOS (using Homebrew):**
        ```bash
        brew install espeak-ng
        ```
    * **Debian / Ubuntu Linux:**
        ```bash
        sudo apt-get update && sudo apt-get install -y espeak-ng
        ```
    * **Windows:**
        1. Go to [espeak-ng releases](https://github.com/espeak-ng/espeak-ng/releases)
        2. Click on **Latest release**
        3. Download the appropriate `*.msi` file (e.g., `espeak-ng-xxxxxxxx-xxxxxxx-x64.msi`)
        4. Run the downloaded installer.

## Setup Steps

1.  **Clone the Repository (If applicable):**
    ```bash
    # git clone <your-repo-url>
    # cd <your-repo-directory>
    ```

2.  **Set Python Version using `pyenv`:**
    Install a recommended Python version if you haven't already (e.g., 3.10.16):
    ```bash
    pyenv install 3.10.16
    ```
    Set the version for this project directory:
    ```bash
    pyenv local 3.10.16
    ```
    *(This creates a `.python-version` file)*

3.  **Create and Activate a Virtual Environment:**
    (Ensure you are using the pyenv-provided Python, e.g., 3.10.16 - verify with `python --version` *before* creating the venv)
    ```bash
    # Use the Python selected by pyenv to create the venv
    python -m venv venv

    # Activate the venv
    source venv/bin/activate
    # On Windows Git Bash/PowerShell: .\venv\Scripts\activate
    ```
    Verify the correct Python is active within the venv:
    ```bash
    python --version
    # Should show the version set by pyenv (e.g., 3.10.16)
    ```

4.  **Install Python Packages:**
    ```bash
    # Upgrade pip (recommended)
    pip install --upgrade pip

    # Install kokoro and soundfile (for examples)
    # NOTE: We install kokoro<0.9.0 due to unresolved dependency issues in >=0.9.4
    pip install "kokoro<0.9.0" soundfile
    # Or specify the last known good version explicitly:
    # pip install kokoro==0.7.16 soundfile

    # Install language extras for misaki if needed (based on kokoro docs):
    # Example for Japanese: pip install misaki[ja]
    # Example for Mandarin: pip install misaki[zh]
    # (kokoro should install the base misaki automatically)
    ```

## Important Note on `kokoro >= 0.9.4` (As of April 2025)

The official `kokoro` documentation recommends version `0.9.4` or newer. However, installing `kokoro>=0.9.4` currently fails because it requires the package `misaki>=0.9.4`, which is **not available on the public Python Package Index (PyPI)**.

Until the `misaki` package is updated on PyPI by the developers, installing `kokoro>=0.9.4` using standard `pip` methods will result in a "No matching distribution found for misaki>=0.9.4" error.

We recommend using `kokoro<0.9.0` (e.g., `0.7.16`) for a stable installation for now. If you absolutely need features from `0.9.4`, you would need to contact the `kokoro` maintainers or investigate workarounds like installing `kokoro` without dependencies (`pip install --no-deps ...`) which is generally not recommended.

## Running the Code

*(Add instructions here on how to run your specific project)*