import 'package:flutter/material.dart';

import '../core/l10n/l10n_ext.dart';
import '../models/app_language.dart';
import '../widgets/language_selector.dart';
import 'customers_view.dart';

/// Top-level navigation shell. The MVP has a single main section (customers),
/// with a language selector available from the app bar at all times.
class HomeView extends StatelessWidget {
  const HomeView({
    super.key,
    required this.language,
    required this.onLanguageChanged,
  });

  final AppLanguage language;
  final ValueChanged<AppLanguage> onLanguageChanged;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(context.l10n.appTitle),
        actions: [
          Padding(
            padding: const EdgeInsetsDirectional.only(end: 8),
            child: Center(
              child: LanguageSelector(
                value: language,
                onChanged: onLanguageChanged,
              ),
            ),
          ),
        ],
      ),
      body: const CustomersView(),
    );
  }
}
