import 'package:flutter/material.dart';

import '../widgets/ahsan_traders_logo.dart';

/// Welcome/splash: brand + a Sign In button. Business order is
/// Chicken → LPG (Gas) → Broiler (Poultry Farm).
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1B5E20),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const SizedBox(height: 20),
                const AhsanTradersLogo(size: 150, showText: true),
                const SizedBox(height: 40),
                const Text(
                  '3 Businesses | 1 Vision',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w500,
                    color: Colors.white,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 60),
                // Order: Chicken → LPG (Gas) → Broiler (Poultry).
                const Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _BusinessIcon(
                      backgroundColor: Color(0xFFC62828),
                      icon: Icons.fastfood,
                      label: 'Chicken Shop',
                    ),
                    _BusinessIcon(
                      backgroundColor: Color(0xFF1565C0),
                      icon: Icons.local_fire_department,
                      label: 'LPG / Gas',
                    ),
                    _BusinessIcon(
                      backgroundColor: Color(0xFF2E7D32),
                      icon: Icons.agriculture,
                      label: 'Poultry Farm',
                    ),
                  ],
                ),
                const SizedBox(height: 80),
                const Text(
                  'Grow Together With Trust',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w600,
                    color: Color(0xFFFFD700),
                    fontStyle: FontStyle.italic,
                    letterSpacing: 1,
                  ),
                ),
                const SizedBox(height: 24),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFFFD700),
                    foregroundColor: const Color(0xFF1B5E20),
                    padding: const EdgeInsets.symmetric(
                        horizontal: 48, vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(30),
                    ),
                  ),
                  onPressed: () =>
                      Navigator.of(context).pushNamed('/login'),
                  child: const Text(
                    'Sign In',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _BusinessIcon extends StatelessWidget {
  final Color backgroundColor;
  final IconData icon;
  final String label;

  const _BusinessIcon({
    required this.backgroundColor,
    required this.icon,
    required this.label,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          width: 72,
          height: 72,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: backgroundColor,
          ),
          child: Icon(icon, size: 34, color: Colors.white),
        ),
        const SizedBox(height: 8),
        SizedBox(
          width: 84,
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              color: Colors.white,
              fontWeight: FontWeight.w500,
            ),
            textAlign: TextAlign.center,
          ),
        ),
      ],
    );
  }
}
