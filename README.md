# DB Eagle

[![Build Status](https://github.com/aguilaj10/db-eagle/actions/workflows/build.yml/badge.svg)](https://github.com/aguilaj10/db-eagle/actions)

DB Eagle is a lightweight database manager for PostgreSQL and SQLite, built with Kotlin and Jetpack Compose Desktop.

## [🚀 Visit the Landing Page](https://aguilaj10.github.io/db-eagle/)

Explore features, screenshots, and download the latest releases for macOS, Windows, and Linux.

---

## Features

- **Multi-Database Support**: Connect and manage PostgreSQL and SQLite.
- **ER Diagrams**: Automatically generate and visualize schema relationships.
- **Data Export**: Export results to CSV or JSON formats.
- **Modern UI**: Clean and intuitive desktop experience.

## Download

Get the latest version from our [GitHub Releases](https://github.com/aguilaj10/db-eagle/releases/latest).

## Development

This project uses Gradle and Compose Desktop.

### Prerequisites

- JDK 17 or higher

### Running locally

```bash
./gradlew run
```

### Packaging

```bash
./gradlew package
```

## GitHub Pages Setup

The landing page is served from the `docs/` folder. Ensure your repository settings are configured:
1. Go to **Settings** > **Pages**
2. Set **Source** to "Deploy from a branch"
3. Set **Branch** to `main` and folder to `/docs`
4. Click **Save**

## License

MIT
