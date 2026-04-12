# craftalism-market

Minecraft Paper plugin client for the Craftalism market experience.

## Project Layout

This repository is a single Gradle project rooted at the repository top level.

## Current State

The repository currently provides the first read-only browsing slice:

- root-level Gradle build and Paper plugin packaging
- `/market` command entry
- config-backed fixture data for categories and items
- category, item, and informational trade GUIs
- in-memory session tracking and cleanup on inventory close/player quit

Quote flow, API integration, and trade execution are intentionally not implemented yet.
