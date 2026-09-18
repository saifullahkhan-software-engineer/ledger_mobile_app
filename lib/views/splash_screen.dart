import 'package:flutter/material.dart';
import '../widgets/ahsan_traders_logo.dart';
import 'login_screen.dart';

class SplashScreen extends StatelessWidget {
  final VoidCallback onLoginSuccess;

  const SplashScreen({super.key, required this.onLoginSuccess});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1B5E20), // Dark green background
      body: GestureDetector(
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => LoginScreen(onLoginSuccess: onLoginSuccess),
            ),
          );
        },
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              child: Padding(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const SizedBox(height: 40),
                    // Logo
                    const AhsanTradersLogo(
                      size: 150,
                      showText: true,
                    ),
                    const SizedBox(height: 40),
                    // Slogan
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
                    // Business Icons
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                      children: [
                        _BusinessIcon(
                          backgroundColor: Colors.red.shade700,
                          icon: Icons.fastfood,
                          label: 'Chicken Shop',
                        ),
                        _BusinessIcon(
                          backgroundColor: Colors.green.shade700,
                          icon: Icons.agriculture,
                          label: 'Broiler Farming',
                        ),
                        _BusinessIcon(
                          backgroundColor: Colors.blue.shade700,
                          icon: Icons.local_fire_department,
                          label: 'LPG Business',
                        ),
                      ],
                    ),
                    const SizedBox(height: 80),
                    // Bottom slogan
                    const Text(
                      'Grow Together With Trust',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: Color(0xFFFFD700), // Golden
                        fontStyle: FontStyle.italic,
                        letterSpacing: 1,
                      ),
                    ),
                    const SizedBox(height: 20),
                    // Tap to continue hint
                    const Text(
                      'Tap to continue',
                      style: TextStyle(
                        fontSize: 14,
                        color: Colors.white70,
                      ),
                    ),
                  ],
                ),
              ),
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
          width: 70,
          height: 70,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: backgroundColor,
          ),
          child: Icon(
            icon,
            size: 35,
            color: Colors.white,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          label,
          style: const TextStyle(
            fontSize: 12,
            color: Colors.white,
            fontWeight: FontWeight.w500,
          ),
          textAlign: TextAlign.center,
        ),
      ],
    );
  }
}