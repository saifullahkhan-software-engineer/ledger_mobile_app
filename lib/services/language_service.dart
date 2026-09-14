import 'package:shared_preferences/shared_preferences.dart';

import '../models/app_language.dart';

/// Persists the selected language across app restarts.
class LanguageService {
  static const String _key = 'app_language';

  Future<AppLanguage> load() async {
    final prefs = await SharedPreferences.getInstance();
    return AppLanguage.fromCode(prefs.getString(_key));
  }

  Future<void> save(AppLanguage language) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, language.code);
  }
}
