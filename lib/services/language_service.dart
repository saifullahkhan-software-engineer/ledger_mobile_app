import 'package:shared_preferences/shared_preferences.dart';

import '../models/app_language.dart';

/// Persists the selected language across app restarts.
class LanguageService {
  static const String _key = 'app_language';

  Future<AppLanguage> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      return AppLanguage.fromCode(prefs.getString(_key));
    } catch (e) {
      // Return default language if shared_preferences fails
      return AppLanguage.english;
    }
  }

  Future<void> save(AppLanguage language) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_key, language.code);
    } catch (e) {
      // Silently fail if saving doesn't work
    }
  }
}
