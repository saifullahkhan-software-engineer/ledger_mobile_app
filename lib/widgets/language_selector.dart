import 'package:flutter/material.dart';

import '../models/app_language.dart';

/// Compact dropdown that switches the app language between English (LTR) and
/// Urdu (RTL). Labels are shown in the language itself so the selector is
/// usable no matter which language is currently active.
class LanguageSelector extends StatelessWidget {
  const LanguageSelector({
    super.key,
    required this.value,
    required this.onChanged,
  });

  final AppLanguage value;
  final ValueChanged<AppLanguage> onChanged;

  @override
  Widget build(BuildContext context) {
    return DropdownButton<AppLanguage>(
      value: value,
      underline: const SizedBox.shrink(),
      borderRadius: BorderRadius.circular(12),
      items: AppLanguage.values
          .map(
            (language) => DropdownMenuItem(
              value: language,
              child: Text(language.nativeName),
            ),
          )
          .toList(),
      onChanged: (language) {
        if (language != null) onChanged(language);
      },
    );
  }
}
