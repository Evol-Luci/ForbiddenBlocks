# ForbiddenBlocks Development Environment
FROM ubuntu:22.04

# Install essential packages including gnupg for key management
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    git \
    unzip \
    vim \
    gnupg \
    software-properties-common \
    && rm -rf /var/lib/apt/lists/*

# Install OpenJDK 21 using Ubuntu's default repositories (simpler and more reliable)
RUN apt-get update && apt-get install -y \
    openjdk-21-jdk \
    && rm -rf /var/lib/apt/lists/*

# Set Java 21 as default
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
ENV PATH="$JAVA_HOME/bin:$PATH"

# Install Gradle
ENV GRADLE_VERSION=8.14
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip && \
    unzip gradle-${GRADLE_VERSION}-bin.zip && \
    mv gradle-${GRADLE_VERSION} /opt/gradle && \
    rm gradle-${GRADLE_VERSION}-bin.zip

ENV PATH="/opt/gradle/bin:$PATH"

# Create development user
RUN useradd -m -s /bin/bash devuser && \
    chown -R devuser:devuser /home/devuser

# Set working directory
WORKDIR /workspace

# Switch to development user
USER devuser

# Default command
CMD ["/bin/bash"]