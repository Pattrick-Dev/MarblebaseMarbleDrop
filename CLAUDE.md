# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MarbleDrop is a Paper 1.21.11 Minecraft plugin for the Marblebase SMP server. It provides a marble collection, progression, and racing system.

## Build & Development

There are no test suites — testing is done by deploying to a local Paper server.

## Architecture

### Data Model — Marbles

Marbles are `ItemStack`s with metadata stored entirely in Bukkit's `PersistentDataContainer` (PDC). No external database is used. The key schema lives in `MarbleKeys.java`. `MarbleItem` is the read/write utility; `MarbleData` is the immutable value object. All marble properties (rarity, 5 stats, level, XP, creator UUID, etc.) live in PDC.

### Command Routing

All commands go through `/md <subcommand>` and are dispatched by `CommandKit`. Each subsystem has its own command class (e.g., `InfusionTableCommand`, `RaceCommand`, `TrackCommand`).

### Config

`MdConfig` wraps `config.yml` and provides typed getters for all configurable values: hologram names/offsets, visible radius, infusion animation timing, catalyst values, daily infusion limits, stat caps by rarity, and debug mode.

### UI Pattern

All interactive GUIs are chest inventories. Each menu has a paired `Listener` class that handles click events (e.g., `InfusionTableMenu` + `InfusionTableListener`, `UpgradeMenu` + `UpgradeMenuListener`).

### Persistence

Player data lives in PDC (no YAML/DB needed). World state (table/station/recycler locations, tracks, race signs) is persisted to YAML files in the plugin data folder.

### Async / Scheduling

Hologram updates (`InfusionTableAmbient`, `RecyclerAmbient`) and action bar displays (`ActionBarTaskTracker`) run on Bukkit's async/repeating schedulers. Race physics (`MarbleRaceEngine`) runs on the sync scheduler.
