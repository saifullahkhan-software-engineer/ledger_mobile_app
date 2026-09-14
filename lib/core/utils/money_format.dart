/// Very small formatter helpers. Amounts are stored as `int` paise/units of the
/// smallest currency, matching SQLite's lack of a safe decimal type.
class MoneyFormat {
  MoneyFormat._();

  /// Formats a whole amount in minor units (e.g. 1500 -> 15.00).
  static String format(int minorUnits) {
    final sign = minorUnits < 0 ? '-' : '';
    final abs = minorUnits.abs();
    final major = abs ~/ 100;
    final minor = abs % 100;
    return '$sign$major.${minor.toString().padLeft(2, '0')}';
  }

  /// Parses a user-entered amount string into minor units, or returns `null`
  /// when the input is not a valid, positive amount.
  static int? parse(String input) {
    final text = input.trim();
    if (text.isEmpty) return null;
    final value = double.tryParse(text);
    if (value == null || value <= 0) return null;
    return (value * 100).round();
  }
}
