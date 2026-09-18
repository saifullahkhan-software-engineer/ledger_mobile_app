import 'package:flutter/material.dart';

class AhsanTradersLogo extends StatelessWidget {
  final double size;
  final bool showText;

  const AhsanTradersLogo({
    super.key,
    this.size = 120,
    this.showText = true,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        // Circular Logo
        Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: const Color(0xFF1B5E20), // Dark green background
            border: Border.all(
              color: const Color(0xFFFFD700), // Golden border
              width: 4,
            ),
          ),
          child: Center(
            child: Stack(
              alignment: Alignment.center,
              children: [
                // A letter
                Text(
                  'A',
                  style: TextStyle(
                    fontSize: size * 0.5,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                    fontFamily: 'serif',
                  ),
                ),
                // T letter (offset to create AT effect)
                Positioned(
                  right: size * 0.15,
                  child: Text(
                    'T',
                    style: TextStyle(
                      fontSize: size * 0.35,
                      fontWeight: FontWeight.bold,
                      color: const Color(0xFFFFD700), // Golden
                      fontFamily: 'serif',
                    ),
                  ),
                ),
                // Leaf decoration
                Positioned(
                  top: size * 0.1,
                  left: size * 0.1,
                  child: Icon(
                    Icons.eco,
                    size: size * 0.2,
                    color: Colors.green.shade300,
                  ),
                ),
              ],
            ),
          ),
        ),
        if (showText) ...[
          const SizedBox(height: 16),
          // AHSAN text
          Text(
            'AHSAN',
            style: TextStyle(
              fontSize: size * 0.25,
              fontWeight: FontWeight.bold,
              color: Colors.white,
              letterSpacing: 4,
            ),
          ),
          // TRADERS text with golden lines
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: size * 0.2,
                height: 2,
                color: const Color(0xFFFFD700),
              ),
              const SizedBox(width: 8),
              Text(
                'TRADERS',
                style: TextStyle(
                  fontSize: size * 0.15,
                  fontWeight: FontWeight.w500,
                  color: Colors.white,
                  letterSpacing: 2,
                ),
              ),
              const SizedBox(width: 8),
              Container(
                width: size * 0.2,
                height: 2,
                color: const Color(0xFFFFD700),
              ),
            ],
          ),
        ],
      ],
    );
  }
}