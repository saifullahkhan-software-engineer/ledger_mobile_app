import 'dart:ui';

/// The two languages supported by the app.
enum AppLanguage {
  english('en', 'English', 'en'),
  urdu('ur', 'اردو', 'ur');

  const AppLanguage(this.code, this.nativeName, this.localeCode);

  /// Stable storage key.
  final String code;

  /// Display name in the language itself.
  final String nativeName;

  /// `Locale` language code.
  final String localeCode;

  Locale get locale => Locale(localeCode);

  static AppLanguage fromCode(String? code) {
    for (final language in AppLanguage.values) {
      if (language.code == code) return language;
    }
    return AppLanguage.english;
  }
}
