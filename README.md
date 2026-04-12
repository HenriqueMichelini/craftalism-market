# craftalism-market

Minecraft Paper plugin client for the Craftalism market experience.

## Project Layout

This repository is a single Gradle project rooted at the repository top level.

## Current State

The repository currently provides the first read-only browsing slice:

- root-level Gradle build and Paper plugin packaging
- `/market` command entry
- API-backed snapshot browsing with local cached fallback
- category and item browsing GUIs
- trade GUI quantity controls with debounced quote refresh
- in-memory session tracking and cleanup on inventory close/player quit

Trade execution is intentionally not implemented yet.
