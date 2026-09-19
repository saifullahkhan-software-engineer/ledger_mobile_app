/// Helpers for accepting and validating Pakistani phone numbers the way
/// people actually type them, then normalizing to the backend's E.164 format.
///
/// Accepted:    03001234567, +923001234567, 923001234567, 3001234567
/// Normalized:  +923001234567

final RegExp _phoneRegExp = RegExp(r'^\+[1-9]\d{7,14}$');

/// Strips spaces/dashes/brackets and converts common local formats to +92….
String normalizePhone(String input) {
  var digits = input.trim().replaceAll(RegExp(r'[\s\-().]'), '');
  if (digits.startsWith('+')) {
    digits = digits.substring(1);
  }
  // Already international Pakistan format, e.g. 923001234567.
  if (digits.length == 12 && digits.startsWith('92')) {
    return '+$digits';
  }
  // Local format with leading zero, e.g. 03001234567.
  if (digits.length == 11 && digits.startsWith('0')) {
    return '+92${digits.substring(1)}';
  }
  // Bare number without leading zero, e.g. 3001234567.
  if (digits.length == 10) {
    return '+92$digits';
  }
  return '+$digits';
}

/// Returns an error message when [input] cannot become a valid E.164 number.
String? phoneError(String input) {
  final normalized = normalizePhone(input);
  if (!_phoneRegExp.hasMatch(normalized)) {
    return 'Enter a valid phone number (e.g. 03001234567 or +923001234567)';
  }
  return null;
}
