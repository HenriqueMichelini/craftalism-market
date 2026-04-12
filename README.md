# craftalism-market

Minecraft Paper plugin client for the Craftalism market experience.

## Project Layout

This repository is a single Gradle project rooted at the repository top level.

## Current State

The repository currently provides the initial snapshot, quote, and trade-execution client flow:

- root-level Gradle build and Paper plugin packaging
- `/market` command entry
- API-backed snapshot browsing with local cached fallback
- category and item browsing GUIs
- trade GUI quantity controls with debounced quote refresh
- buy execution from the latest quote with rejection-code handling
- plugin-local item delivery after successful buys, including overflow drops
- sell execution with plugin-local inventory validation and post-success item removal
- in-memory session tracking and cleanup on inventory close/player quit

The remaining hardening work is around compensation handling, broader GUI/session coverage, and continued alignment with API contract evolution.
