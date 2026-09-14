import 'package:flutter/material.dart';

import '../l10n/app_l10n.dart';

/// Convenience accessor so widgets can write `context.l10n` instead of the
/// verbose `AppL10n.of(context)!`.
extension AppL10nX on BuildContext {
  AppL10n get l10n => AppL10n.of(this);
}
