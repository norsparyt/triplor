# Triplor 🚀
A travel matching and planning app built with Flutter, focused on connecting solo travelers and groups through shared adventures.

## Overview
Triplor helps users discover, create, and explore travel adventures, and eventually match with other travelers based on trip preferences such as location, dates, and travel type.

This project is being developed as a **scalable MVP**, with clean architecture and state management best practices, suitable for real-world extension.

## Tech Stack
- **Flutter**
- **Dart**
- **Riverpod** (state management)
- **GoRouter** (navigation)
- **Material UI**

## Current Features (MVP)
- Bottom navigation with shell routing
- Adventure listing on Home screen
- Create Adventure flow (local/in-memory)
- Central Adventure Repository
- Adventure detail screen (ID-based navigation)
- Clean separation of UI, state, and data layers

## Architecture
- Feature-first folder structure
- Repository pattern for data access
- Riverpod providers for state management
- UI split into Screens and Pure View widgets

## Project Status
🚧 **In active development**

Planned next steps:
- Unit tests for repositories and notifiers
- Caching & async refactors
- Firebase integration (later phase)
- Matching logic between travelers
- Chat and requests flow

## Why this project?
This app is built as a **portfolio-quality project** to demonstrate:
- Real app architecture decisions
- Scalable Flutter + Riverpod patterns
- Thoughtful MVP scoping
- Clean navigation and state handling

## Getting Started
```bash
flutter pub get
flutter run