#!/bin/bash

# ForbiddenBlocks Development Environment Script

set -e

echo "🚀 Setting up ForbiddenBlocks Development Environment..."

# Build the Docker image
echo "📦 Building Docker image..."
docker-compose build

# Start the container
echo "🏃 Starting development container..."
docker-compose run --rm forbiddenblocks-dev

echo "✅ Development environment ready!"
echo "💡 Use 'docker-compose run --rm forbiddenblocks-dev' to start the environment"
echo "💡 Use 'docker-compose exec forbiddenblocks-dev /bin/bash' to attach to running container"