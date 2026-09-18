import 'package:flutter/material.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  // Test with minimal setup to isolate the issue
  runApp(const TestApp());
}

class TestApp extends StatelessWidget {
  const TestApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ahsan Traders',
      debugShowCheckedModeBanner: false,
      home: Scaffold(
        backgroundColor: const Color(0xFF1B5E20),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: const [
              Text(
                'Test Screen',
                style: TextStyle(color: Colors.white, fontSize: 24),
              ),
              SizedBox(height: 20),
              Text(
                'If you see this, the app is working',
                style: TextStyle(color: Colors.white, fontSize: 16),
              ),
            ],
          ),
        ),
      ),
    );
  }
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
