import 'package:flutter/material.dart';

import '../core/l10n/l10n_ext.dart';
import '../core/theme/app_theme.dart';
import '../models/app_language.dart';
import '../widgets/ahsan_traders_logo.dart';
import '../widgets/custom_icons.dart';
import '../widgets/language_selector.dart';
import 'customers_view.dart';
import 'icon_manager_view.dart';
import 'users_view.dart';

// Re-export AhsanColors for convenience
class AhsanColors {
  AhsanColors._();
  
  static const Color primaryGreen = Color(0xFF1B5E20);
  static const Color golden = Color(0xFFFFD700);
  static const Color lightGreen = Color(0xFF4CAF50);
  static const Color white = Colors.white;
  static const Color background = Color(0xFFF5F5F5);
}

/// Top-level navigation shell. Super Admin can manage businesses, view all users,
/// and add custom images/icons for the mobile screen cards and actions.
class HomeView extends StatefulWidget {
  const HomeView({
    super.key,
    required this.language,
    this.onLanguageChanged,
  });

  final AppLanguage language;
  final ValueChanged<AppLanguage>? onLanguageChanged;

  @override
  State<HomeView> createState() => _HomeViewState();
}

class _HomeViewState extends State<HomeView> {
  // Dynamic icons configured by Super Admin for the mobile screen
  final Map<String, String> _screenIcons = {};

  void _updateIcon(String key, String imageUrl) {
    setState(() {
      if (imageUrl.isEmpty) {
        _screenIcons.remove(key);
      } else {
        _screenIcons[key] = imageUrl;
      }
    });
  }

  void _navigateToUsers() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => const UsersView(isSuperAdmin: true),
      ),
    );
  }

  void _navigateToIconManager() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => IconManagerView(
          currentIcons: _screenIcons,
          onIconUpdated: _updateIcon,
        ),
      ),
    );
  }

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
            // Custom or default logo in app bar
            if (_screenIcons['app_logo'] != null && _screenIcons['app_logo']!.isNotEmpty)
              ClipRRect(
                borderRadius: BorderRadius.circular(15),
                child: Image.network(
                  _screenIcons['app_logo']!,
                  width: 30,
                  height: 30,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => const AhsanTradersLogo(size: 30, showText: false),
                ),
              )
            else
              const AhsanTradersLogo(size: 30, showText: false),
            const SizedBox(width: 12),
            const Text('Ahsan Traders'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.app_registration),
            tooltip: 'Customize Screen Icons',
            onPressed: _navigateToIconManager,
          ),
          IconButton(
            icon: const Icon(Icons.notifications_none),
            onPressed: () {
              // Handle notifications
            },
          ),
          if (widget.onLanguageChanged != null)
            Padding(
              padding: const EdgeInsetsDirectional.only(end: 8),
              child: Center(
                child: LanguageSelector(
                  value: widget.language,
                  onChanged: widget.onLanguageChanged!,
                ),
              ),
            ),
        ],
      ),
      drawer: _buildDrawer(context),
      body: DashboardView(
        screenIcons: _screenIcons,
        onNavigateToUsers: _navigateToUsers,
        onNavigateToIcons: _navigateToIconManager,
        onUpdateIcon: _updateIcon,
      ),
      bottomNavigationBar: const CustomBottomNavigationBar(),
    );
  }

  Widget _buildDrawer(BuildContext context) {
    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          DrawerHeader(
            decoration: const BoxDecoration(
              color: AhsanColors.primaryGreen,
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    if (_screenIcons['app_logo'] != null && _screenIcons['app_logo']!.isNotEmpty)
                      ClipRRect(
                        borderRadius: BorderRadius.circular(30),
                        child: Image.network(
                          _screenIcons['app_logo']!,
                          width: 56,
                          height: 56,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) => const AhsanTradersLogo(size: 56, showText: false),
                        ),
                      )
                    else
                      const AhsanTradersLogo(size: 56, showText: false),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AhsanColors.golden,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Text(
                        'SUPER ADMIN',
                        style: TextStyle(
                          color: AhsanColors.primaryGreen,
                          fontWeight: FontWeight.bold,
                          fontSize: 10,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
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
            leading: const Icon(Icons.people, color: AhsanColors.primaryGreen),
            title: const Text('Users & Access'),
            subtitle: const Text('Manage investors and managers'),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: Colors.green.shade100,
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Text('All Users', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
            ),
            onTap: () {
              Navigator.pop(context);
              _navigateToUsers();
            },
          ),
          ListTile(
            leading: const Icon(Icons.photo_library, color: AhsanColors.primaryGreen),
            title: const Text('Mobile Screen Icons'),
            subtitle: const Text('Add & edit icons shown on screen'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {
              Navigator.pop(context);
              _navigateToIconManager();
            },
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.store),
            title: const Text('Businesses'),
            onTap: () {
              Navigator.pop(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.inventory),
            title: const Text('Stock'),
            onTap: () {
              Navigator.pop(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.receipt_long),
            title: const Text('Reports'),
            onTap: () {
              Navigator.pop(context);
            },
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.settings),
            title: const Text('Settings'),
            onTap: () {
              Navigator.pop(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.logout),
            title: const Text('Logout'),
            onTap: () {
              Navigator.pop(context);
            },
          ),
        ],
      ),
    );
  }
}

class DashboardView extends StatelessWidget {
  final Map<String, String> screenIcons;
  final VoidCallback onNavigateToUsers;
  final VoidCallback onNavigateToIcons;
  final Function(String key, String imageUrl) onUpdateIcon;

  const DashboardView({
    super.key,
    required this.screenIcons,
    required this.onNavigateToUsers,
    required this.onNavigateToIcons,
    required this.onUpdateIcon,
  });

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
            const SizedBox(height: 20),

            // Super Admin Quick Control Strip
            _buildSuperAdminQuickStrip(context),
            const SizedBox(height: 20),
            
            // Summary cards
            _buildSummaryCards(),
            const SizedBox(height: 24),
            
            // Business cards
            _buildBusinessCards(context),
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
          'Super Admin Dashboard · Full System Authority',
          style: TextStyle(
            fontSize: 14,
            color: Colors.grey[600],
          ),
        ),
      ],
    );
  }

  Widget _buildSuperAdminQuickStrip(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AhsanColors.primaryGreen.withOpacity(0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AhsanColors.primaryGreen.withOpacity(0.2)),
      ),
      child: Row(
        children: [
          Expanded(
            child: InkWell(
              onTap: onNavigateToUsers,
              borderRadius: BorderRadius.circular(10),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: [
                    BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 4),
                  ],
                ),
                child: Row(
                  children: [
                    const Icon(Icons.people, color: AhsanColors.primaryGreen, size: 24),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'See Users',
                          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                        ),
                        Text(
                          'Investors & Admins',
                          style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: InkWell(
              onTap: onNavigateToIcons,
              borderRadius: BorderRadius.circular(10),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: [
                    BoxShadow(color: Colors.black.withOpacity(0.04), blurRadius: 4),
                  ],
                ),
                child: Row(
                  children: [
                    const Icon(Icons.add_photo_alternate, color: AhsanColors.primaryGreen, size: 24),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Add Image/Icon',
                          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                        ),
                        Text(
                          'Screen Icons',
                          style: TextStyle(fontSize: 11, color: Colors.grey[600]),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
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

  Widget _buildBusinessCards(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text(
              'Business Overview',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AhsanColors.primaryGreen,
              ),
            ),
            TextButton.icon(
              icon: const Icon(Icons.edit, size: 14),
              label: const Text('Edit Card Icons', style: TextStyle(fontSize: 12)),
              onPressed: onNavigateToIcons,
            ),
          ],
        ),
        const SizedBox(height: 8),
        _BusinessCard(
          title: 'Chicken Shop',
          amount: 'Rs. 18,500',
          subtitle: 'Sales Today',
          iconUrl: screenIcons['business_chicken'],
          customIcon: CustomIcons.chicken(),
          backgroundColor: Colors.red.shade700,
          onEditIcon: onNavigateToIcons,
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'Broiler Farming',
          amount: 'Rs. 12,800',
          subtitle: 'Sales Today',
          iconUrl: screenIcons['business_broiler'],
          customIcon: CustomIcons.agriculture(),
          backgroundColor: Colors.green.shade700,
          onEditIcon: onNavigateToIcons,
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'LPG Business',
          amount: 'Rs. 17,450',
          subtitle: 'Sales Today',
          iconUrl: screenIcons['business_lpg'],
          customIcon: CustomIcons.lpg(),
          backgroundColor: Colors.blue.shade700,
          onEditIcon: onNavigateToIcons,
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
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                    fontWeight: FontWeight.w500,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            amount,
            style: const TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.bold,
              color: Colors.black87,
            ),
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Text(
                percentage,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: Colors.green,
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
  final String? iconUrl;
  final Color backgroundColor;
  final VoidCallback? onEditIcon;

  const _BusinessCard({
    required this.title,
    required this.amount,
    required this.subtitle,
    this.icon,
    this.customIcon,
    this.iconUrl,
    required this.backgroundColor,
    this.onEditIcon,
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
          // Icon shown on the mobile screen: dynamically rendered image if custom, or default vector icon
          Container(
            width: 50,
            height: 50,
            decoration: BoxDecoration(
              color: backgroundColor,
              borderRadius: BorderRadius.circular(12),
            ),
            child: (iconUrl != null && iconUrl!.isNotEmpty)
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(12),
                    child: Image.network(
                      iconUrl!,
                      width: 50,
                      height: 50,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) =>
                          customIcon ?? (icon != null ? Icon(icon, color: Colors.white, size: 24) : const SizedBox.shrink()),
                    ),
                  )
                : (customIcon ?? (icon != null ? Icon(icon, color: Colors.white, size: 24) : null)),
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
          if (onEditIcon != null)
            IconButton(
              icon: const Icon(Icons.edit_note, size: 20, color: Colors.grey),
              tooltip: 'Change icon image',
              onPressed: onEditIcon,
            ),
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
      child: Column(
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: AhsanColors.primaryGreen.withOpacity(0.1),
              borderRadius: BorderRadius.circular(16),
            ),
            child: Center(
              child: customIcon ?? (icon != null ? Icon(icon, color: AhsanColors.primaryGreen, size: 24) : null),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Colors.black87,
            ),
          ),
        ],
      ),
    );
  }
}

class CustomBottomNavigationBar extends StatelessWidget {
  const CustomBottomNavigationBar({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, -5),
          ),
        ],
      ),
      child: BottomNavigationBar(
        type: BottomNavigationBarType.fixed,
        selectedItemColor: AhsanColors.primaryGreen,
        unselectedItemColor: Colors.grey,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.home),
            label: 'Home',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.attach_money),
            label: 'Sales',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.account_balance_wallet),
            label: 'Expenses',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.assessment),
            label: 'Reports',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.more_horiz),
            label: 'More',
          ),
        ],
      ),
    );
  }
}
