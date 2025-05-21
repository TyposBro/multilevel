# {PATH_TO_PROJECT}/api/Dockerfile

# Using alpine variants can result in smaller images, but might lack some tools.
# 'bookworm' is based on Debian 12.
FROM node:22-bookworm-slim

# --- Environment Variables ---
# Set the working directory in the container
WORKDIR /usr/src/app

# Set the Node environment to production (many libraries optimize for this)
ENV NODE_ENV=production

# --- Application Dependencies ---
COPY package*.json ./

# Install dependencies using npm ci (clean install, generally preferred for CI/production)
# If you use yarn, replace with 'RUN yarn install --frozen-lockfile --production'
RUN npm ci --only=production

# --- Application Code ---
# Bundle app source
COPY . .

# --- Expose Port ---
EXPOSE 3000

# --- Healthcheck ---
HEALTHCHECK --interval=30s --timeout=10s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:3000/ || exit 1

# --- Run Command ---
CMD ["npm", "start"]