# Use an official Python runtime as a parent image
FROM python:3.9-slim

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1
# ENV PYTORCH_ENABLE_MPS_FALLBACK=1 # Can be kept if torch ops are still heavy

# Install system dependencies (espeak-ng still needed for misaki)
RUN apt-get update && \
    apt-get install -y --no-install-recommends espeak-ng git && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory in the container
WORKDIR /app

# Copy the requirements file
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt torch --index-url https://download.pytorch.org/whl/cpu

# Copy the FastAPI application and your kokoro module
# This will include the app/kokoro/configs directory
COPY ./app /app

# Make port 8000 available
EXPOSE 8000

# Define the command to run your app
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]