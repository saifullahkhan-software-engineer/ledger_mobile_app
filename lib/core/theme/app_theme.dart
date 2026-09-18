import 'package:flutter/material.dart';

/// Ahsan Traders brand colors
class AhsanColors {
  AhsanColors._();
  
  static const Color primaryGreen = Color(0xFF1B5E20); // Dark green
  static const Color golden = Color(0xFFFFD700); // Golden
  static const Color lightGreen = Color(0xFF4CAF50); // Light green
  static const Color white = Colors.white;
  static const Color background = Color(0xFFF5F5F5);
}

/// Light theme used across the app. Updated for Ahsan Traders branding.
class AppTheme {
  AppTheme._();

  static ThemeData get light {
    final scheme = ColorScheme.fromSeed(
      seedColor: AhsanColors.primaryGreen,
      primary: AhsanColors.primaryGreen,
      secondary: AhsanColors.golden,
    );
    
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AhsanColors.background,
      appBarTheme: AppBarTheme(
        centerTitle: false,
        backgroundColor: AhsanColors.primaryGreen,
        foregroundColor: AhsanColors.white,
        elevation: 2,
        titleTextStyle: const TextStyle(
          color: AhsanColors.white,
          fontSize: 20,
          fontWeight: FontWeight.bold,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AhsanColors.primaryGreen,
          foregroundColor: AhsanColors.white,
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: AhsanColors.primaryGreen),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(8),
          borderSide: const BorderSide(color: AhsanColors.primaryGreen, width: 2),
        ),
      ),
    );
  }
}
