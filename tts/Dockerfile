# {PATH_TO_PROJECT}/tts/Dockerfile

# 1. Base Image: Use an official Python image.
FROM python:3.10.16-slim-bullseye

# --- Environment Variables ---
# Set the working directory in the container
WORKDIR /app

# Prevent Python from writing pyc files to disc (optional, good practice)
ENV PYTHONDONTWRITEBYTECODE 1
# Ensure Python output is sent straight to terminal (helps with Docker logs)
ENV PYTHONUNBUFFERED 1

# --- System Dependencies ---
# Update package lists and install espeak-ng and other build essentials
# 'build-essential' might be needed if some pip packages compile C extensions.
# 'libasound2-dev' might be needed for some audio libraries if you use ALSA directly (soundfile handles this)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    build-essential \
    espeak-ng \
    && apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# --- Application Dependencies ---
# Copy the requirements file first to leverage Docker cache
COPY requirements.txt .

# Install Python dependencies
# --no-cache-dir reduces image size
# The --extra-index-url for PyTorch is crucial for CPU builds on some systems
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt \
    --extra-index-url https://download.pytorch.org/whl/cpu

# --- Application Code ---
# Copy the rest of your application code into the working directory
COPY app.py .
# If you have other files/folders (e.g., model weights downloaded manually, static assets), copy them too.
# Example: COPY ./models /app/models

# --- Expose Port ---
# Expose the port the Flask app runs on (must match PORT in app.py)
EXPOSE 5005

# --- Healthcheck (Optional but Recommended) ---
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
    CMD curl -f http://localhost:5005/health || exit 1

# --- Run Command ---
# Use Gunicorn as the WSGI server for production.
# Adjust workers based on your server's CPU cores (2-4 workers per core is a common starting point).
# The default PORT in app.py is 5005.
# Use CMD in "exec" form (JSON array)
CMD ["gunicorn", "--workers", "4", "--bind", "0.0.0.0:5005", "app:app"]

# For development, you might use:
# CMD ["python", "app.py"]
# But Gunicorn is preferred for anything beyond basic testing.