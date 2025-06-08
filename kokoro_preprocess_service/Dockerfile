# Use an official Python runtime as a parent image, align with your pyproject.toml's requires-python
FROM python:3.10-slim

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1
# The installer puts uv in /root/.local/bin
ENV PATH="/root/.local/bin:${PATH}"

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends espeak-ng git curl && \
    rm -rf /var/lib/apt/lists/*

# Set the working directory in the container
WORKDIR /app

# --- Dependency Management with uv and uv.lock ---

# Install uv using its standard script.
RUN curl -LsSf https://astral.sh/uv/install.sh | sh

# Verify uv is installed and accessible (optional, but good for debugging)
RUN uv --version

# Copy your dependency definition files
COPY pyproject.toml uv.lock ./

# CORRECTED COMMAND: Use 'uv pip sync' with the '--system' flag.
# This explicitly tells uv to install packages into the container's global
# Python environment, which is standard practice for Docker.
RUN uv pip sync pyproject.toml --system

# --- End Dependency Management ---

# Copy the FastAPI application and your kokoro module
# This will include the app/kokoro/configs directory
COPY ./app /app

# Make port 8000 available
EXPOSE 8000

# Define the command to run your app
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]