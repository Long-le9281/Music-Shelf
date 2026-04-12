#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"

log() {
  printf "[setup] %s\n" "$1"
}

warn() {
  printf "[setup] WARNING: %s\n" "$1"
}

has_cmd() {
  command -v "$1" >/dev/null 2>&1
}

OS_NAME="$(uname -s 2>/dev/null || echo unknown)"

install_with_supported_manager() {
  if [[ "$OS_NAME" == MINGW* || "$OS_NAME" == MSYS* || "$OS_NAME" == CYGWIN* ]]; then
    if has_cmd winget; then
      log "Installing missing packages with winget..."
      winget install --id OpenJS.NodeJS.LTS -e --accept-package-agreements --accept-source-agreements
      winget install --id EclipseAdoptium.Temurin.17.JDK -e --accept-package-agreements --accept-source-agreements
      winget install --id Apache.Maven -e --accept-package-agreements --accept-source-agreements
      return 0
    fi

    if has_cmd choco; then
      log "Installing missing packages with Chocolatey..."
      choco install -y nodejs-lts temurin17 maven
      return 0
    fi

    if has_cmd scoop; then
      log "Installing missing packages with Scoop..."
      scoop install nodejs-lts temurin17-jdk maven
      return 0
    fi
  fi

  if has_cmd brew; then
    log "Installing missing packages with Homebrew..."
    brew install node openjdk@17 maven
    return 0
  fi

  if has_cmd apt-get; then
    log "Installing missing packages with apt-get..."
    sudo apt-get update
    sudo apt-get install -y nodejs npm openjdk-17-jdk maven
    return 0
  fi

  return 1
}

ensure_toolchain() {
  local missing=0

  if ! has_cmd node; then
    warn "Node.js is not installed."
    missing=1
  fi

  if ! has_cmd npm; then
    warn "npm is not installed."
    missing=1
  fi

  if ! has_cmd java; then
    warn "Java is not installed."
    missing=1
  fi

  if ! has_cmd mvn; then
    warn "Maven is not installed."
    missing=1
  fi

  if [[ "$missing" -eq 1 ]]; then
    log "Trying automatic install for supported package managers..."
    if ! install_with_supported_manager; then
      cat <<'EOF'
[setup] Could not auto-install dependencies on this machine.
[setup] Please install these manually and re-run this script:
  - Node.js 18+ (includes npm)
  - Java 17+
  - Maven 3.9+

[setup] Quick links:
  - Node.js: https://nodejs.org/
  - Java 17: https://adoptium.net/
  - Maven: https://maven.apache.org/download.cgi
EOF
      exit 1
    fi
  fi
}

install_project_dependencies() {
  if [[ ! -f "$FRONTEND_DIR/package.json" ]]; then
    warn "frontend/package.json was not found. Skipping npm install."
  else
    log "Installing frontend dependencies..."
    (cd "$FRONTEND_DIR" && npm install)
  fi

  if [[ ! -f "$BACKEND_DIR/pom.xml" ]]; then
    warn "backend/pom.xml was not found. Skipping Maven dependency warm-up."
  else
    log "Downloading backend dependencies..."
    (cd "$BACKEND_DIR" && mvn -q -DskipTests dependency:go-offline)
  fi
}

main() {
  log "Detected OS: $OS_NAME"
  ensure_toolchain

  log "Tool versions:"
  node --version || true
  npm --version || true
  java -version || true
  mvn -v || true

  install_project_dependencies

  cat <<EOF
[setup] Done.
[setup] Next steps:
  - Windows: run run.bat
  - macOS/Linux: run backend and frontend manually (mvn spring-boot:run + npm start)
EOF
}

main "$@"

