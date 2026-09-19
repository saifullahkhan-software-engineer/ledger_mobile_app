import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// The signed-in account plus the server it belongs to.
class Session {
  const Session({
    required this.baseUrl,
    required this.token,
    required this.name,
    required this.phone,
    required this.role,
    this.language = 'en',
  });

  final String baseUrl;
  final String token;
  final String name;
  final String phone;
  final String role;
  final String language;

  bool get isSuperAdmin => role == 'SUPERADMIN';
  bool get isAdmin => role == 'ADMIN' || role == 'SUPERADMIN';

  Map<String, dynamic> toJson() => {
        'base_url': baseUrl,
        'token': token,
        'name': name,
        'phone': phone,
        'role': role,
        'language': language,
      };

  factory Session.fromJson(Map<String, dynamic> json) => Session(
        baseUrl: json['base_url'] as String? ?? '',
        token: json['token'] as String? ?? '',
        name: json['name'] as String? ?? '',
        phone: json['phone'] as String? ?? '',
        role: json['role'] as String? ?? 'ADMIN',
        language: json['language'] as String? ?? 'en',
      );
}

/// Persists the session and lets the user sign out.
class SessionStore {
  static const String _key = 'ahsan_traders_session';

  Future<Session?> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final raw = prefs.getString(_key);
      if (raw == null || raw.isEmpty) return null;
      return Session.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  Future<void> save(Session session) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_key, jsonEncode(session.toJson()));
    } catch (_) {
      // Best effort; the user simply logs in again next time.
    }
  }

  Future<void> clear() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_key);
    } catch (_) {
      // Ignore.
    }
  }
}
