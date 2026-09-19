import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';

import 'core/l10n/app_l10n.dart';
import 'core/theme/app_theme.dart';
import 'models/app_language.dart';
import 'services/language_service.dart';
import 'services/session_store.dart';
import 'views/home_view.dart';
import 'views/login_screen.dart';
import 'views/splash_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final session = await SessionStore().load();
  final language = await LanguageService().load();
  runApp(LedgerApp(initialLanguage: language, session: session));
}

class LedgerApp extends StatefulWidget {
  const LedgerApp({
    super.key,
    required this.initialLanguage,
    this.session,
  });

  final AppLanguage initialLanguage;
  final Session? session;

  @override
  State<LedgerApp> createState() => _LedgerAppState();
}

class _LedgerAppState extends State<LedgerApp> {
  final LanguageService _languageService = LanguageService();
  late AppLanguage _language;
  Session? _session;

  @override
  void initState() {
    super.initState();
    _language = widget.initialLanguage;
    _session = widget.session;
  }

  void _setLanguage(AppLanguage language) {
    setState(() {
      _language = language;
      _languageService.save(language);
    });
  }

  void _onLoggedIn(Session session) {
    setState(() => _session = session);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ahsan Traders',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      locale: _language.locale,
      supportedLocales: AppLanguage.values.map((l) => l.locale).toList(),
      localizationsDelegates: const [
        AppL10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      initialRoute: _session == null ? '/welcome' : '/home',
      routes: {
        '/welcome': (_) => const SplashScreen(),
        '/login': (_) => LoginScreen(onLoggedIn: _onLoggedIn),
        '/home': (_) => HomeView(
              session: _session!,
              language: _language,
              onLanguageChanged: _setLanguage,
            ),
      },
    );
  }
}
