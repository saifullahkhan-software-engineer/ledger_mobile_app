import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/l10n/app_l10n.dart';
import 'core/theme/app_theme.dart';
import 'models/app_language.dart';
import 'services/language_service.dart';
import 'views/splash_screen.dart';
import 'views/home_view.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Use English as default to avoid async initialization issues
  runApp(LedgerApp(initialLanguage: AppLanguage.english));
}

class LedgerApp extends StatefulWidget {
  const LedgerApp({super.key, required this.initialLanguage});

  final AppLanguage initialLanguage;

  @override
  State<LedgerApp> createState() => _LedgerAppState();
}

class _LedgerAppState extends State<LedgerApp> {
  final LanguageService _languageService = LanguageService();
  late AppLanguage _language;
  bool _showSplash = true;

  @override
  void initState() {
    super.initState();
    _language = widget.initialLanguage;
  }

  void _setLanguage(AppLanguage language) {
    setState(() {
      _language = language;
      _languageService.save(language);
    });
  }

  void _navigateToHome() {
    setState(() {
      _showSplash = false;
    });
  }

  void _handleLoginSuccess() {
    _navigateToHome();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ahsan Traders',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      // The app is always available in English and Urdu.
      locale: _language.locale,
      supportedLocales: AppLanguage.values.map((l) => l.locale).toList(),
      localizationsDelegates: const [
        AppL10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: _showSplash
          ? SplashScreen(onLoginSuccess: _handleLoginSuccess)
          : HomeView(
              language: _language,
              onLanguageChanged: _setLanguage,
            ),
    );
  }
}

