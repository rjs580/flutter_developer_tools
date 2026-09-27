# Changelog

All notable changes to this project are documented here. The format is based on
Keep a Changelog, and this project follows Semantic Versioning.

## [1.0.1]

### Fixed

- Type hints no longer throw errors with Dart plugin 509 and newer, and work again there.
  Dart 509 removed the hover call we used, so we now use Dart's newer LSP hover when the
  old one isn't there (#1)
- Parameter name hints no longer throw errors on constructor calls with Dart plugin 508.1 and newer
- If a Dart or Flutter plugin update breaks one of our features, that feature now turns off
  quietly instead of breaking highlighting in the whole editor
- Package docs on hover no longer hang for up to 30 seconds when pub.dev can't be reached
- Package upgrade quick fixes no longer start pub get while the IDE is holding its write lock
- Saving breadcrumb settings can no longer clash with breadcrumbs being drawn

### Added

- A "Report on GitHub" button for plugin errors in the IDE error dialog. It opens a
  pre-filled issue that you can review before sending. Each error is reported once per
  session, so nothing gets spammed
- A new plugin icon, with a dark theme version

## [1.0.0]

First release.

### Package management

- Pub.dev auto-complete with popularity info
- README and CHANGELOG on hover, with repository links
- Inline update hints, with safe and full upgrade fixes
- Warnings for discontinued and incompatible packages
- Click through to pub.dev package and version pages

### Dart code

- Doc code samples highlighted to match your IDE theme
- Breadcrumbs with custom icons for classes, methods, and more
- Parameter name hints for positional arguments
- Inferred type hints for variables and parameters
- Code lens with usage and implementation counts

### Quick actions

- Run flutter gen-l10n from .arb files
- Run build_runner build, watch, and clean from generated files
