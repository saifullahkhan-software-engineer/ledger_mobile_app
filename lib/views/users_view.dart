import 'package:flutter/material.dart';
import '../models/app_user.dart';

class UsersView extends StatefulWidget {
  final bool isSuperAdmin;
  final VoidCallback? onUserUpdated;

  const UsersView({
    super.key,
    this.isSuperAdmin = true,
    this.onUserUpdated,
  });

  @override
  State<UsersView> createState() => _UsersViewState();
}

class _UsersViewState extends State<UsersView> {
  String _selectedRoleFilter = 'ALL';
  String _searchQuery = '';

  // Initial list representing users loaded from the backend
  late List<AppUser> _users;

  @override
  void initState() {
    super.initState();
    _users = [
      const AppUser(
        id: 'user-001',
        phone: '+923007117755',
        name: 'Ahsan Khan',
        role: 'SUPERADMIN',
        language: 'en',
        kycStatus: 'VERIFIED',
        assignedBusinesses: ['Chicken Shop', 'LPG Business', 'Broiler Farming'],
      ),
      const AppUser(
        id: 'user-002',
        phone: '+923001234568',
        name: 'Tariq Mehmood',
        role: 'ADMIN',
        language: 'ur',
        kycStatus: 'VERIFIED',
        assignedBusinesses: ['Ahsan Chicken Shop'],
      ),
      const AppUser(
        id: 'user-003',
        phone: '+923009876543',
        name: 'Bilal Ahmad',
        role: 'INVESTOR',
        language: 'en',
        kycStatus: 'VERIFIED',
        assignedBusinesses: [],
      ),
      const AppUser(
        id: 'user-004',
        phone: '+923335551212',
        name: 'Zahid Hussain',
        role: 'INVESTOR',
        language: 'ur',
        kycStatus: 'UNVERIFIED',
        assignedBusinesses: [],
      ),
      const AppUser(
        id: 'user-005',
        phone: '+923214449988',
        name: 'Usman Ali',
        role: 'ADMIN',
        language: 'en',
        kycStatus: 'VERIFIED',
        assignedBusinesses: ['Ahsan LPG Business'],
      ),
    ];
  }

  List<AppUser> get _filteredUsers {
    return _users.where((user) {
      final matchesRole = _selectedRoleFilter == 'ALL' || user.role == _selectedRoleFilter;
      final matchesSearch = _searchQuery.isEmpty ||
          user.name.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          user.phone.contains(_searchQuery);
      return matchesRole && matchesSearch;
    }).toList();
  }

  void _showUserDetail(AppUser user) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedCornerShape(
        top: Radius.circular(20),
      ),
      builder: (ctx) {
        return StatefulBuilder(
          builder: (context, setModalState) {
            return Padding(
              padding: EdgeInsets.only(
                left: 20,
                right: 20,
                top: 20,
                bottom: MediaQuery.of(context).viewInsets.bottom + 24,
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: Colors.grey[300],
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 28,
                        backgroundColor: const Color(0xFF1B5E20),
                        child: Text(
                          user.name.isNotEmpty ? user.name[0].toUpperCase() : 'U',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              user.name,
                              style: const TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              user.phone,
                              style: TextStyle(
                                color: Colors.grey[600],
                                fontSize: 14,
                              ),
                            ),
                          ],
                        ),
                      ),
                      _buildRoleBadge(user.role),
                    ],
                  ),
                  const SizedBox(height: 20),
                  const Divider(),
                  const SizedBox(height: 12),
                  _buildDetailRow('User ID', user.id),
                  _buildDetailRow('Language', user.language.toUpperCase()),
                  _buildDetailRow('KYC Status', user.kycStatus, isKyc: true),
                  if (user.assignedBusinesses.isNotEmpty)
                    _buildDetailRow(
                      'Assigned Businesses',
                      user.assignedBusinesses.join(', '),
                    ),
                  const SizedBox(height: 20),
                  if (widget.isSuperAdmin && user.role != 'SUPERADMIN') ...[
                    const Text(
                      'Super Admin Actions',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Color(0xFF1B5E20),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        if (user.kycStatus != 'VERIFIED')
                          Expanded(
                            child: ElevatedButton.icon(
                              style: ElevatedButton.styleFrom(
                                backgroundColor: const Color(0xFF1B5E20),
                                foregroundColor: Colors.white,
                              ),
                              icon: const Icon(Icons.verified_user),
                              label: const Text('Verify KYC'),
                              onPressed: () {
                                setState(() {
                                  final idx = _users.indexWhere((u) => u.id == user.id);
                                  if (idx != -1) {
                                    _users[idx] = AppUser(
                                      id: user.id,
                                      phone: user.phone,
                                      name: user.name,
                                      role: user.role,
                                      language: user.language,
                                      kycStatus: 'VERIFIED',
                                      assignedBusinesses: user.assignedBusinesses,
                                    );
                                  }
                                });
                                Navigator.pop(ctx);
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(content: Text('KYC verified for ${user.name}')),
                                );
                              },
                            ),
                          ),
                        if (user.kycStatus != 'VERIFIED') const SizedBox(width: 12),
                        Expanded(
                          child: OutlinedButton.icon(
                            icon: const Icon(Icons.manage_accounts),
                            label: Text(
                              user.role == 'INVESTOR' ? 'Make Manager' : 'Make Investor',
                            ),
                            onPressed: () {
                              final newRole = user.role == 'INVESTOR' ? 'ADMIN' : 'INVESTOR';
                              setState(() {
                                final idx = _users.indexWhere((u) => u.id == user.id);
                                if (idx != -1) {
                                  _users[idx] = AppUser(
                                    id: user.id,
                                    phone: user.phone,
                                    name: user.name,
                                    role: newRole,
                                    language: user.language,
                                    kycStatus: user.kycStatus,
                                    assignedBusinesses: user.assignedBusinesses,
                                  );
                                }
                              });
                              Navigator.pop(ctx);
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text('Role changed to $newRole for ${user.name}')),
                              );
                            },
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildDetailRow(String label, String value, {bool isKyc = false}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey[700], fontSize: 14)),
          if (isKyc)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(
                color: value == 'VERIFIED'
                    ? Colors.green.withOpacity(0.15)
                    : Colors.amber.withOpacity(0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                value,
                style: TextStyle(
                  color: value == 'VERIFIED' ? Colors.green[800] : Colors.amber[800],
                  fontWeight: FontWeight.bold,
                  fontSize: 12,
                ),
              ),
            )
          else
            Flexible(
              child: Text(
                value,
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                textAlign: TextAlign.right,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildRoleBadge(String role) {
    Color bg;
    Color fg;
    switch (role) {
      case 'SUPERADMIN':
        bg = const Color(0xFFFFD700).withOpacity(0.2);
        fg = const Color(0xFFB8860B);
        break;
      case 'ADMIN':
        bg = Colors.blue.withOpacity(0.15);
        fg = Colors.blue.shade800;
        break;
      default:
        bg = Colors.green.withOpacity(0.15);
        fg = Colors.green.shade800;
        break;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        role,
        style: TextStyle(color: fg, fontWeight: FontWeight.bold, fontSize: 11),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filteredUsers;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Users & Access'),
        backgroundColor: const Color(0xFF1B5E20),
        foregroundColor: Colors.white,
      ),
      body: Column(
        children: [
          // Header / Search area
          Container(
            color: const Color(0xFF1B5E20),
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
            child: TextField(
              onChanged: (val) => setState(() => _searchQuery = val),
              style: const TextStyle(color: Colors.white),
              decoration: InputDecoration(
                hintText: 'Search by name or phone...',
                hintStyle: TextStyle(color: Colors.white.withOpacity(0.7)),
                prefixIcon: const Icon(Icons.search, color: Colors.white),
                filled: true,
                fillColor: Colors.white.withOpacity(0.2),
                contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(30),
                  borderSide: BorderSide.none,
                ),
              ),
            ),
          ),

          // Role Filter Tabs
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Row(
              children: [
                _buildFilterChip('ALL', 'All Users (${_users.length})'),
                const SizedBox(width: 8),
                _buildFilterChip(
                  'INVESTOR',
                  'Investors (${_users.where((u) => u.role == 'INVESTOR').length})',
                ),
                const SizedBox(width: 8),
                _buildFilterChip(
                  'ADMIN',
                  'Managers (${_users.where((u) => u.role == 'ADMIN').length})',
                ),
                const SizedBox(width: 8),
                _buildFilterChip(
                  'SUPERADMIN',
                  'Super Admin (${_users.where((u) => u.role == 'SUPERADMIN').length})',
                ),
              ],
            ),
          ),

          // User list
          Expanded(
            child: filtered.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.person_off, size: 48, color: Colors.grey[400]),
                        const SizedBox(height: 12),
                        Text('No users found', style: TextStyle(color: Colors.grey[600])),
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: filtered.length,
                    padding: const EdgeInsets.all(12),
                    itemBuilder: (context, index) {
                      final user = filtered[index];
                      return Card(
                        elevation: 1,
                        margin: const EdgeInsets.only(bottom: 10),
                        shape: RoundedCornerShape(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: ListTile(
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 16,
                            vertical: 8,
                          ),
                          leading: CircleAvatar(
                            backgroundColor: user.role == 'SUPERADMIN'
                                ? const Color(0xFFFFD700)
                                : const Color(0xFF1B5E20),
                            child: Text(
                              user.name.isNotEmpty ? user.name[0].toUpperCase() : 'U',
                              style: TextStyle(
                                color: user.role == 'SUPERADMIN'
                                    ? const Color(0xFF1B5E20)
                                    : Colors.white,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          title: Row(
                            children: [
                              Expanded(
                                child: Text(
                                  user.name,
                                  style: const TextStyle(fontWeight: FontWeight.bold),
                                ),
                              ),
                              _buildRoleBadge(user.role),
                            ],
                          ),
                          subtitle: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const SizedBox(height: 4),
                              Text(user.phone, style: TextStyle(color: Colors.grey[700])),
                              const SizedBox(height: 2),
                              Row(
                                children: [
                                  Icon(
                                    user.kycStatus == 'VERIFIED'
                                        ? Icons.check_circle
                                        : Icons.access_time,
                                    size: 14,
                                    color: user.kycStatus == 'VERIFIED'
                                        ? Colors.green
                                        : Colors.amber,
                                  ),
                                  const SizedBox(width: 4),
                                  Text(
                                    'KYC ${user.kycStatus}',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: user.kycStatus == 'VERIFIED'
                                          ? Colors.green[700]
                                          : Colors.amber[800],
                                    ),
                                  ),
                                  if (user.assignedBusinesses.isNotEmpty) ...[
                                    const SizedBox(width: 8),
                                    Text(
                                      '• ${user.assignedBusinesses.length} biz',
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: Colors.grey[600],
                                      ),
                                    ),
                                  ],
                                ],
                              ),
                            ],
                          ),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () => _showUserDetail(user),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String role, String label) {
    final selected = _selectedRoleFilter == role;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      selectedColor: const Color(0xFF1B5E20),
      labelStyle: TextStyle(
        color: selected ? Colors.white : Colors.black89,
        fontWeight: selected ? FontWeight.bold : FontWeight.normal,
        fontSize: 12,
      ),
      onSelected: (_) {
        setState(() => _selectedRoleFilter = role);
      },
    );
  }
}

class RoundedCornerShape extends RoundedRectangleBorder {
  const RoundedCornerShape({
    super.side = BorderSide.none,
    BorderRadiusGeometry borderRadius = BorderRadius.zero,
    Radius? top,
  }) : super(
          borderRadius: top != null
              ? BorderRadius.vertical(top: top)
              : borderRadius,
        );
}
