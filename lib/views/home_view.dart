import 'package:flutter/material.dart';

import '../models/app_language.dart';
import '../services/api_service.dart';
import '../services/session_store.dart';
import '../widgets/ahsan_traders_logo.dart';
import '../widgets/custom_icons.dart';
import '../widgets/language_selector.dart';
import 'add_user_view.dart';
import 'icon_manager_view.dart';
import 'users_view.dart';

/// Shared brand palette.
class AhsanColors {
  AhsanColors._();

  static const Color primaryGreen = Color(0xFF1B5E20);
  static const Color golden = Color(0xFFFFD700);
  static const Color lightGreen = Color(0xFF4CAF50);
  static const Color white = Colors.white;
  static const Color background = Color(0xFFF5F5F5);
}

/// Top-level navigation shell. Wires the real backend session into the
/// users / icons screens and lets the superadmin sign out.
///
/// Business order everywhere: Chicken → LPG (Gas) → Broiler (Poultry).
class HomeView extends StatefulWidget {
  const HomeView({
    super.key,
    required this.session,
    required this.language,
    this.onLanguageChanged,
  });

  final Session session;
  final AppLanguage language;
  final ValueChanged<AppLanguage>? onLanguageChanged;

  @override
  State<HomeView> createState() => _HomeViewState();
}

class _HomeViewState extends State<HomeView> {
  late final ApiService _api = ApiService(
    baseUrl: widget.session.baseUrl,
    token: widget.session.token,
  );

  // Dynamic icons configured by the Super Admin for the mobile screens.
  final Map<String, String> _screenIcons = {};

  @override
  void initState() {
    super.initState();
    _loadIcons();
  }

  /// Turns a stored image path into a full URL. Server paths returned by the
  /// upload endpoint look like `/uploads/icons/x.png` and need the base URL.
  String _resolve(String? url) {
    if (url == null || url.isEmpty) return '';
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    if (url.startsWith('/')) return '${_api.normalizedBase}$url';
    return url;
  }

  Future<void> _loadIcons() async {
    try {
      final items = await _api.icons();
      final map = <String, String>{};
      for (final item in items) {
        final key = item['key'] as String?;
        final url = item['image_url'] as String?;
        if (key != null && url != null && url.isNotEmpty) {
          map[key] = url;
        }
      }
      if (!mounted) return;
      setState(() => _screenIcons.addAll(map));
    } on ApiException {
      // Optional: icons fail to load only if the server is unreachable.
    }
  }

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
        builder: (_) => ManagersView(
          api: _api,
          isSuperAdmin: widget.session.isSuperAdmin,
        ),
      ),
    );
  }

  void _navigateToIconManager() {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => IconManagerView(
          api: _api,
          currentIcons: _screenIcons,
          onIconUpdated: _updateIcon,
        ),
      ),
    );
  }

  Future<void> _signOut() async {
    try {
      await _api.logout();
    } catch (_) {
      // Even if the server is unreachable, clear the local session.
    }
    await SessionStore().clear();
    if (!mounted) return;
    Navigator.of(context).pushNamedAndRemoveUntil('/welcome', (r) => false);
  }

  Future<void> _addUser() async {
    try {
      final businesses = await _api.businesses();
      if (!mounted) return;
      final created = await Navigator.of(context).push<Map<String, dynamic>>(
        MaterialPageRoute(
          builder: (_) => AddUserScreen(
            api: _api,
            businesses: businesses,
          ),
        ),
      );
      if (created != null && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('${created['name']} added')),
        );
      }
    } on ApiException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.message)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: Builder(
          builder: (context) => IconButton(
            icon: const Icon(Icons.menu),
            onPressed: () => Scaffold.of(context).openDrawer(),
          ),
        ),
        title: Row(
          children: [
            if (_resolve(_screenIcons['app_logo']).isNotEmpty)
              ClipRRect(
                borderRadius: BorderRadius.circular(15),
                child: Image.network(
                  _resolve(_screenIcons['app_logo']),
                  width: 30,
                  height: 30,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) =>
                      const AhsanTradersLogo(size: 30, showText: false),
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
          if (widget.session.isSuperAdmin)
            IconButton(
              icon: const Icon(Icons.person_add_alt_1),
              tooltip: 'Add User',
              onPressed: _addUser,
            ),
          IconButton(
            icon: const Icon(Icons.notifications_none),
            onPressed: () {},
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
        screenIcons: _screenIcons
            .map((k, v) => MapEntry(k, _resolve(v))),
        isSuperAdmin: widget.session.isSuperAdmin,
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
            decoration: const BoxDecoration(color: AhsanColors.primaryGreen),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    if (_resolve(_screenIcons['app_logo']).isNotEmpty)
                      ClipRRect(
                        borderRadius: BorderRadius.circular(30),
                        child: Image.network(
                          _resolve(_screenIcons['app_logo']),
                          width: 56,
                          height: 56,
                          fit: BoxFit.cover,
                          errorBuilder: (_, __, ___) =>
                              const AhsanTradersLogo(size: 56, showText: false),
                        ),
                      )
                    else
                      const AhsanTradersLogo(size: 56, showText: false),
                    const Spacer(),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: AhsanColors.golden,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        widget.session.role,
                        style: const TextStyle(
                          color: AhsanColors.primaryGreen,
                          fontWeight: FontWeight.bold,
                          fontSize: 10,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  widget.session.name,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  widget.session.phone,
                  style: TextStyle(
                    color: Colors.white.withOpacity(0.8),
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),
          if (widget.session.isSuperAdmin)
            ListTile(
              leading: const Icon(Icons.people, color: AhsanColors.primaryGreen),
              title: const Text('Users & Access'),
              subtitle: const Text('Manage investors and managers'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {
                Navigator.pop(context);
                _navigateToUsers();
              },
            ),
          ListTile(
            leading:
                const Icon(Icons.photo_library, color: AhsanColors.primaryGreen),
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
            onTap: () => Navigator.pop(context),
          ),
          ListTile(
            leading: const Icon(Icons.inventory),
            title: const Text('Stock'),
            onTap: () => Navigator.pop(context),
          ),
          ListTile(
            leading: const Icon(Icons.receipt_long),
            title: const Text('Reports'),
            onTap: () => Navigator.pop(context),
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.settings),
            title: const Text('Settings'),
            onTap: () => Navigator.pop(context),
          ),
          ListTile(
            leading: const Icon(Icons.logout, color: Colors.red),
            title: const Text('Logout', style: TextStyle(color: Colors.red)),
            onTap: () {
              Navigator.pop(context);
              _signOut();
            },
          ),
        ],
      ),
    );
  }
}

class DashboardView extends StatelessWidget {
  final Map<String, String> screenIcons;
  final bool isSuperAdmin;
  final VoidCallback onNavigateToUsers;
  final VoidCallback onNavigateToIcons;
  final Function(String key, String imageUrl) onUpdateIcon;

  const DashboardView({
    super.key,
    required this.screenIcons,
    required this.isSuperAdmin,
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
            _buildWelcomeSection(),
            const SizedBox(height: 20),
            if (isSuperAdmin) ...[
              _buildSuperAdminQuickStrip(context),
              const SizedBox(height: 20),
            ],
            _buildSummaryCards(),
            const SizedBox(height: 24),
            _buildBusinessCards(context),
            const SizedBox(height: 24),
            _buildQuickActions(),
          ],
        ),
      ),
    );
  }

  Widget _buildWelcomeSection() {
    return const Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Welcome!',
          style: TextStyle(
            fontSize: 24,
            fontWeight: FontWeight.bold,
            color: AhsanColors.primaryGreen,
          ),
        ),
        SizedBox(height: 4),
        Text(
          'Ahsan Traders Dashboard',
          style: TextStyle(fontSize: 14, color: Colors.grey),
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
                padding:
                    const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: [
                    BoxShadow(
                        color: Colors.black.withOpacity(0.04), blurRadius: 4),
                  ],
                ),
                child: Row(
                  children: [
                    const Icon(Icons.people,
                        color: AhsanColors.primaryGreen, size: 24),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'See Users',
                          style: TextStyle(
                              fontWeight: FontWeight.bold, fontSize: 13),
                        ),
                        Text(
                          'Investors & Admins',
                          style:
                              TextStyle(fontSize: 11, color: Colors.grey[600]),
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
                padding:
                    const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(10),
                  boxShadow: [
                    BoxShadow(
                        color: Colors.black.withOpacity(0.04), blurRadius: 4),
                  ],
                ),
                child: Row(
                  children: [
                    const Icon(Icons.add_photo_alternate,
                        color: AhsanColors.primaryGreen, size: 24),
                    const SizedBox(width: 10),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Add Image/Icon',
                          style: TextStyle(
                              fontWeight: FontWeight.bold, fontSize: 13),
                        ),
                        Text(
                          'Screen Icons',
                          style:
                              TextStyle(fontSize: 11, color: Colors.grey[600]),
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
      children: const [
        Expanded(
          child: _SummaryCard(
            title: 'Total Sales (Today)',
            amount: 'Rs. 48,750',
            icon: Icons.paid,
            iconColor: Colors.green,
            percentage: '+12%',
            subtitle: 'vs yesterday',
          ),
        ),
        SizedBox(width: 12),
        Expanded(
          child: _SummaryCard(
            title: 'Total Profit (Today)',
            amount: 'Rs. 16,320',
            icon: Icons.trending_up,
            iconColor: Colors.green,
            percentage: '+8%',
            subtitle: 'vs yesterday',
          ),
        ),
      ],
    );
  }

  // Business order: Chicken → LPG (Gas) → Broiler (Poultry Farm).
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
          fallbackIcon: Icons.fastfood,
          backgroundColor: Colors.red.shade700,
          onEditIcon: onNavigateToIcons,
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'LPG / Gas Business',
          amount: 'Rs. 17,450',
          subtitle: 'Sales Today',
          iconUrl: screenIcons['business_lpg'],
          fallbackIcon: Icons.local_fire_department,
          backgroundColor: Colors.blue.shade700,
          onEditIcon: onNavigateToIcons,
        ),
        const SizedBox(height: 12),
        _BusinessCard(
          title: 'Poultry Farm (Broiler)',
          amount: 'Rs. 12,800',
          subtitle: 'Sales Today',
          iconUrl: screenIcons['business_broiler'],
          fallbackIcon: Icons.agriculture,
          backgroundColor: Colors.green.shade700,
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
                style: TextStyle(fontSize: 12, color: Colors.grey[600]),
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
  final IconData fallbackIcon;
  final String? iconUrl;
  final Color backgroundColor;
  final VoidCallback? onEditIcon;

  const _BusinessCard({
    required this.title,
    required this.amount,
    required this.subtitle,
    required this.fallbackIcon,
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
                      errorBuilder: (_, __, ___) => Icon(
                        fallbackIcon,
                        color: Colors.white,
                        size: 24,
                      ),
                    ),
                  )
                : Icon(fallbackIcon, color: Colors.white, size: 24),
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
                  style: TextStyle(fontSize: 12, color: Colors.grey[600]),
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
              child: customIcon ??
                  (icon != null
                      ? Icon(icon, color: AhsanColors.primaryGreen, size: 24)
                      : null),
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
          BottomNavigationBarItem(icon: Icon(Icons.home), label: 'Home'),
          BottomNavigationBarItem(
              icon: Icon(Icons.attach_money), label: 'Sales'),
          BottomNavigationBarItem(
              icon: Icon(Icons.account_balance_wallet), label: 'Expenses'),
          BottomNavigationBarItem(
              icon: Icon(Icons.assessment), label: 'Reports'),
          BottomNavigationBarItem(
              icon: Icon(Icons.more_horiz), label: 'More'),
        ],
      ),
    );
  }
}
