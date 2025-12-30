# ForbiddenBlocks Development Environment

This Docker-based development environment provides a consistent, isolated workspace for ForbiddenBlocks mod development with the correct Java 21 setup.

## Prerequisites

- Docker
- Docker Compose

## Quick Start

1. **Build and start the environment:**
   ```bash
   ./dev-env.sh
   ```

2. **Or use docker-compose directly:**
   ```bash
   # Build the image
   docker-compose build

   # Start the development container
   docker-compose run --rm forbiddenblocks-dev
   ```

## Environment Details

- **Java Version:** OpenJDK 21 (Temurin)
- **Gradle Version:** 8.12
- **Base OS:** Ubuntu 22.04
- **Container Name:** ForbiddenBlocks_Dev

## Development Workflow

### First Time Setup
```bash
# Build the environment
./dev-env.sh

# Inside the container, verify Java version
java -version
# Should show: OpenJDK 21.x.x

# Verify Gradle
gradle --version
```

### Building the Mod
```bash
# Inside the container
./gradlew build
```

### Running the Client
```bash
# Inside the container
./gradlew runClient
```

### Testing the Configuration
```bash
# Inside the container
./gradlew runClient
# Then in-game, access ModMenu -> ForbiddenBlocks to test the owo-config integration
```

## Volume Mounts

- **Project Directory:** `/workspace` (current directory)
- **Gradle Cache:** `~/.gradle` (persistent between sessions)

## Troubleshooting

### Java Version Issues
If you encounter Java version conflicts:
```bash
# Inside container, verify versions
java -version
gradle --version
```

### Gradle Cache Issues
If Gradle has cached incompatible bytecode:
```bash
# Clear Gradle cache (from host)
docker-compose run --rm forbiddenblocks-dev rm -rf ~/.gradle/caches
```

### Container Management
```bash
# Stop all containers
docker-compose down

# Remove containers and images
docker-compose down --rmi all

# View logs
docker-compose logs
```

## File Structure

```
.
├── Dockerfile              # Development environment definition
├── docker-compose.yml      # Docker Compose configuration
├── dev-env.sh             # Quick setup script
├── README_DEV_ENV.md      # This file
└── [project files]        # Your mod source code
```

## Benefits

- **Consistent Environment:** Same setup across all development machines
- **Java 21 Guarantee:** No version conflicts with system Java
- **Isolated Development:** Changes don't affect host system
- **Persistent Cache:** Gradle dependencies cached between sessions
- **Easy Collaboration:** Share exact environment with team members