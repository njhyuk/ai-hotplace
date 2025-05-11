# Build stage
FROM gradle:8.6-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle build --no-daemon -x test

# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app

# Install Chromium and dependencies
RUN apt-get update && \
    apt-get install -y \
    chromium \
    chromium-driver \
    xvfb \
    libglib2.0-0 \
    libnss3 \
    libgconf-2-4 \
    libfontconfig1 \
    libx11-xcb1 \
    libxcomposite1 \
    libxcursor1 \
    libxdamage1 \
    libxi6 \
    libxtst6 \
    libxrandr2 \
    libasound2 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libpangocairo-1.0-0 \
    libgtk-3-0 \
    && rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV CHROME_BIN=/usr/bin/chromium
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver
ENV DISPLAY=:99
ENV CHROME_OPTIONS="--headless=new --no-sandbox --disable-dev-shm-usage --disable-gpu"

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m"
EXPOSE 8080

ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"] 