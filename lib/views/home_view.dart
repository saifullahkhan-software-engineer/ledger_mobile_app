import 'package:flutter/material.dart';

import '../core/l10n/l10n_ext.dart';
import '../core/theme/app_theme.dart';
import '../models/app_language.dart';
import '../widgets/ahsan_traders_logo.dart';
import '../widgets/custom_icons.dart';
import '../widgets/language_selector.dart';
import 'customers_view.dart';

// Re-export AhsanColors for convenience
class AhsanColors {
  AhsanColors._();
  
  static const Color primaryGreen = Color(0xFF1B5E20);
  static const Color golden = Color(0xFFFFD700);
  static const Color lightGreen = Color(0xFF4CAF50);
  static const Color white = Colors.white;
  static const Color background = Color(0xFFF5F5F5);
}

/// Top-level navigation shell. The MVP has a single main section (customers),
/// with a language selector available from the app bar at all times.
class HomeView extends StatelessWidget {
  const HomeView({
    super.key,
    required this.language,
    this.onLanguageChanged,
  });

  final AppLanguage language;
  final ValueChanged<AppLanguage>? onLanguageChanged;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: Builder(
          builder: (context) => IconButton(
            icon: const Icon(Icons.menu),
            onPressed: () {
              Scaffold.of(context).openDrawer();
            },
          ),
        ),
        title: Row(
          children: [
            // Small logo in app bar
            const AhsanTradersLogo(size: 30, showText: false),
            const SizedBox(width: 12),
            const Text('Ahsan Traders'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.notifications_none),
            onPressed: () {
              // Handle notifications
            },
          ),
          if (onLanguageChanged != null)
            Padding(
              padding: const EdgeInsetsDirectional.only(end: 8),
              child: Center(
                child: LanguageSelector(
                  value: language,
                  onChanged: onLanguageChanged!,
                ),
              ),
            ),
        ],
      ),
      drawer: _buildDrawer(context),
      body: const DashboardView(),
      bottomNavigationBar: const CustomBottomNavigationBar(),
    );
  }

  Widget _buildDrawer(BuildContext context) {
    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          DrawerHeader(
            decoration: BoxDecoration(
              color: AhsanColors.primaryGreen,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const AhsanTradersLogo(size: 60, showText: false),
                const SizedBox(height: 16),
                const Text(
                  'Ahsan Khan',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  '+923007117755',
                  style: TextStyle(
                    color: Colors.white.withOpacity(0.8),
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
          ListTile(
            leading: const Icon(Icons.store),
            title: const Text('Businesses'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.people),
            title: const Text('Investors'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.inventory),
            title: const Text('Stock'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.receipt_long),
            title: const Text('Reports'),
            onTap: () {},
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.settings),
            title: const Text('Settings'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('Logout'),
            onTap: () {
              Navigator.pop(context);
              // Handle logout
            },
          ),
        ],
      ),
    );
  }
}

class DashboardView extends StatelessWidget {
  const DashboardView({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Welcome section
            _buildWelcomeSection(),
            const SizedBox(height: 24),
            
            // Summary cards
            _buildSummaryCards(),
            const SizedBox(height: 24),
            
            // Business cards
            _buildBusinessCards(),
            const SizedBox(height: 24),
            
            // Quick actions
            _buildQuickActions(),
          ],
        ),
      ),
    );
  }

  Widget _buildWelcomeSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Welcome, Ahsan Khan',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: AhsanColors.primaryGreen,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          'Manage your 3 businesses from one place',
          style: TextStyle(
            fontSize: 14,
            color: Colors.grey[600],
          ),
        ),
      ],
    );
  }

  Widget _buildSummaryCards() {
    return Row(
      children: [
        Expanded(
          child: _SummaryCard(
            title: 'Total Sales (Today)',
            amount: 'Rs. 48,750',
            customIcon: CustomIcons.coinStack(),
            iconColor: Colors.green,
            percentage: '+12%',
            subtitle: 'vs yesterday',
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _SummaryCard(
            title: 'Total Profit (Today)',
            amount: 'Rs. 16,320',
            customIcon: CustomIcons.barChart(),
            iconColor: Colors.green,
            percentage: '+8%',
            subtitle: 'vs yesterday',
          ),
        ),
      ],
    );
  }

  Widget _buildBusinessCards() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Business Overview',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AhsanColors.primaryGreen,
          ),
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'Chicken Shop',
          amount: 'Rs. 18,500',
          subtitle: 'Sales Today',
          customIcon: CustomIcons.chicken(),
          backgroundColor: Colors.red.shade700,
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'Broiler Farming',
          amount: 'Rs. 12,800',
          subtitle: 'Sales Today',
          customIcon: CustomIcons.agriculture(),
          backgroundColor: Colors.green.shade700,
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'LPG Business',
          amount: 'Rs. 17,450',
          subtitle: 'Sales Today',
          customIcon: CustomIcons.lpg(),
          backgroundColor: Colors.blue.shade700,
        ),
      ],
    );
  }

  Widget _buildQuickActions() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Quick Actions',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
            color: AhsanColors.primaryGreen,
          ),
        ),
        const SizedBox(height: 12),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _QuickAction(
              customIcon: CustomIcons.add(),
              label: 'Add Sale',
              onTap: () {},
            ),
            _QuickAction(
              customIcon: CustomIcons.wallet(),
              label: 'Add Expense',
              onTap: () {},
            ),
            _QuickAction(
              customIcon: CustomIcons.reports(),
              label: 'Reports',
              onTap: () {},
            ),
            _QuickAction(
              customIcon: CustomIcons.stock(),
              label: 'Stock',
              onTap: () {},
            ),
          ],
        ),
      ],
    );
  }
}

class _SummaryCard extends StatelessWidget {
  final String title;
  final String amount;
  final IconData? icon;
  final Widget? customIcon;
  final Color iconColor;
  final String percentage;
  final String subtitle;

  const _SummaryCard({
    required this.title,
    required this.amount,
    this.icon,
    this.customIcon,
    required this.iconColor,
    required this.percentage,
    required this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (customIcon != null)
                customIcon!
              else if (icon != null)
                Icon(icon, color: iconColor, size: 20),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  title,
                  style: const TextStyle(
                    fontSize: 12,
                    color: Colors.grey,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            amount,
            style: const TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.bold,
              color: AhsanColors.primaryGreen,
            ),
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Text(
                percentage,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.green.shade700,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(width: 4),
              Text(
                subtitle,
                style: TextStyle(
                  fontSize: 12,
                  color: Colors.grey[600],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _BusinessCard extends StatelessWidget {
  final String title;
  final String amount;
  final String subtitle;
  final IconData? icon;
  final Widget? customIcon;
  final Color backgroundColor;

  const _BusinessCard({
    required this.title,
    required this.amount,
    required this.subtitle,
    this.icon,
    this.customIcon,
    required this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 4,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              color: backgroundColor,
              borderRadius: BorderRadius.circular(12),
            ),
            child: customIcon ?? (icon != null ? Icon(icon, color: Colors.white, size: 24) : null),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AhsanColors.primaryGreen,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  amount,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: Colors.black87,
                  ),
                ),
                Text(
                  subtitle,
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ),
          const Icon(Icons.chevron_right, color: Colors.grey),
        ],
      ),
    );
  }
}

class _QuickAction extends StatelessWidget {
  final IconData? icon;
  final Widget? customIcon;
  final String label;
  final VoidCallback onTap;

  const _QuickAction({
    this.icon,
    this.customIcon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            Container(
              width: 50,
              height: 50,
              decoration: BoxDecoration(
                color: AhsanColors.primaryGreen.withOpacity(0.1),
                borderRadius: BorderRadius.circular(12),
              ),
              child: customIcon ?? (icon != null ? Icon(icon, color: AhsanColors.primaryGreen, size: 24) : null),
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(
                fontSize: 12,
                color: AhsanColors.primaryGreen,
                fontWeight: FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class CustomBottomNavigationBar extends StatelessWidget {
  const CustomBottomNavigationBar({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 70,
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 4,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _BottomNavItem(
            icon: Icons.home,
            label: 'Home',
            isActive: true,
          ),
          _BottomNavItem(
            icon: Icons.attach_money,
            label: 'Sales',
            isActive: false,
          ),
          _BottomNavItem(
            icon: Icons.account_balance_wallet,
            label: 'Expenses',
            isActive: false,
          ),
          _BottomNavItem(
            icon: Icons.assessment,
            label: 'Reports',
            isActive: false,
          ),
          _BottomNavItem(
            icon: Icons.more_horiz,
            label: 'More',
            isActive: false,
          ),
        ],
      ),
    );
  }
}

class _BottomNavItem extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool isActive;

  const _BottomNavItem({
    required this.icon,
    required this.label,
    required this.isActive,
  });

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () {
        // Handle navigation
      },
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              icon,
              color: isActive ? AhsanColors.primaryGreen : Colors.grey,
              size: 24,
            ),
            const SizedBox(height: 4),
            Text(
              label,
              style: TextStyle(
                fontSize: 12,
                color: isActive ? AhsanColors.primaryGreen : Colors.grey,
                fontWeight: isActive ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
