import 'package:flutter/material.dart';

class CustomIcons {
  // Coin stack icon for sales - using wallet icon per user preference
  static Widget coinStack({Color color = Colors.green, double size = 24}) {
    return Icon(Icons.account_balance_wallet, color: color, size: size);
  }

  // Bar chart icon for profit
  static Widget barChart({Color color = Colors.green, double size = 24}) {
    return Icon(Icons.bar_chart, color: color, size: size);
  }

  // Chicken icon
  static Widget chicken({Color color = Colors.white, double size = 24}) {
    return Icon(Icons.fastfood, color: color, size: size);
  }

  // Agriculture/broiler icon
  static Widget agriculture({Color color = Colors.white, double size = 24}) {
    return Icon(Icons.agriculture, color: color, size: size);
  }

  // LPG/fire icon
  static Widget lpg({Color color = Colors.white, double size = 24}) {
    return Icon(Icons.local_fire_department, color: color, size: size);
  }

  // Plus/add icon
  static Widget add({Color color = Colors.green, double size = 24}) {
    return Icon(Icons.add_circle, color: color, size: size);
  }

  // Wallet icon
  static Widget wallet({Color color = Colors.green, double size = 24}) {
    return Icon(Icons.account_balance_wallet, color: color, size: size);
  }

  // Reports icon
  static Widget reports({Color color = Colors.green, double size = 24}) {
    return Icon(Icons.insert_chart, color: color, size: size);
  }

  // Stock/inventory icon
  static Widget stock({Color color = Colors.green, double size = 24}) {
    return Icon(Icons.inventory_2, color: color, size: size);
  }
}