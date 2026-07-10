# Flutter Developer Tools

Small tools that make everyday Flutter and Dart work faster in IntelliJ IDEA and
Android Studio: smarter pubspec.yaml editing, docs on hover, better navigation,
and inline hints.

## Requirements

- IntelliJ IDEA 2025.2 or newer (or a compatible Android Studio)
- The Dart and Flutter plugins installed and enabled

## Features

### Package management

- Auto-complete pub.dev packages as you type, with a Flutter Favorites badge
- Inline update hints, with a safe upgrade option that skips major versions
- One-click upgrade, with an option to run flutter pub get for you
- Warnings for discontinued and Dart 3 incompatible packages
- Click a package name to open its pub.dev page

### Documentation

- Hover a package to read its README, or a version to read its CHANGELOG
- Dart docs with code samples highlighted to match your IDE theme
- Works with GitHub, GitLab, Bitbucket, Codeberg, and SourceHut
- Understands packages inside monorepos

### Navigation and hints

- Breadcrumbs for classes, methods, functions, constructors, mixins, enums, and
  extensions, with custom icons
- Code lens with usage and implementation counts
- Parameter name hints for positional arguments
- Inferred type hints for var, final, and const

### Quick actions

- Run flutter gen-l10n from .arb files
- Run build_runner build, watch, and clean from generated files

## Install

In the IDE, open Settings > Plugins > Marketplace, search for
"Flutter Developer Tools", and install.

## Settings

Settings > Tools > Flutter Developer Tools lets you set breadcrumb icons and
control how hints are shown.

## Build from source

```
./gradlew buildPlugin
```

The plugin zip is written to build/distributions.

To try it in a sandbox IDE:

```
./gradlew runIde
```

## License

MIT. See [LICENSE](LICENSE).
